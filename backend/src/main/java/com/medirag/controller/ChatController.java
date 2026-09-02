package com.medirag.controller;

import com.medirag.common.exception.BusinessException;
import com.medirag.common.ratelimit.RedisRateLimiter;
import com.medirag.common.result.Result;
import com.medirag.common.result.ResultCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.medirag.entity.MedConversation;
import com.medirag.entity.MedMessage;
import com.medirag.entity.dto.ChatRequestDTO;
import com.medirag.entity.vo.PageVO;
import com.medirag.mapper.MedConversationMapper;
import com.medirag.mapper.MedMessageMapper;
import com.medirag.service.UserService;
import com.medirag.service.rag.RagPipeline;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagPipeline ragPipeline;
    private final UserService userService;
    private final MedConversationMapper conversationMapper;
    private final MedMessageMapper messageMapper;
    private final RedisRateLimiter rateLimiter;

    /** SSE 流式问答专用线程池：有界，避免无界 newCachedThreadPool 在高并发下线程膨胀。 */
    private final ExecutorService sseExecutor = Executors.newFixedThreadPool(16);

    /** 限流参数：每用户每分钟最多 10 次问答（保护下游 LLM 配额）。 */
    private static final int CHAT_RATE_LIMIT = 10;
    private static final Duration CHAT_RATE_WINDOW = Duration.ofMinutes(1);

    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) Long conversationId, @RequestParam String message) {
        Long userId = userService.getCurrentUserId();

        if (!rateLimiter.tryAcquire("rate:chat:" + userId, CHAT_RATE_LIMIT, CHAT_RATE_WINDOW)) {
            SseEmitter rejected = new SseEmitter(5_000L);
            try {
                rejected.send(SseEmitter.event().name("error")
                        .data("{\"message\":\"提问太频繁啦，请稍后再试\"}"));
                rejected.complete();
            } catch (Exception ignored) {
            }
            return rejected;
        }

        SseEmitter emitter = new SseEmitter(120_000L);
        String healthProfile = userService.getCurrentUser().getHealthProfile();

        sseExecutor.execute(() -> ragPipeline.execute(userId, conversationId, message, healthProfile, emitter));
        return emitter;
    }

    @GetMapping("/conversations")
    public Result<PageVO<MedConversation>> listConversations(
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "20") Integer size) {
        Long userId = userService.getCurrentUserId();
        Page<MedConversation> page = new Page<>(current, size);
        conversationMapper.selectPage(page, new LambdaQueryWrapper<MedConversation>()
                .eq(MedConversation::getUserId, userId)
                .eq(MedConversation::getDeleted, 0)
                .orderByDesc(MedConversation::getLastActive));
        return Result.success(PageVO.of(page));
    }

    @GetMapping("/history/{conversationId}")
    public Result<PageVO<MedMessage>> getHistory(
            @PathVariable Long conversationId,
            @RequestParam(value = "current", defaultValue = "1") Integer current,
            @RequestParam(value = "size", defaultValue = "50") Integer size) {
        Long userId = userService.getCurrentUserId();
        MedConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return Result.error("会话不存在");
        }

        Page<MedMessage> page = new Page<>(current, size);
        messageMapper.selectPage(page, new LambdaQueryWrapper<MedMessage>()
                .eq(MedMessage::getConversationId, conversationId)
                .orderByAsc(MedMessage::getCreateTime));
        return Result.success(PageVO.of(page));
    }

    @GetMapping("/retrieval-log/{messageId}")
    public Result<Object> getRetrievalLog(@PathVariable Long messageId) {
        MedMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            return Result.error("消息不存在");
        }
        String log = message.getRetrievalLog();
        if (log == null) {
            return Result.success(null);
        }
        return Result.success(JSON.parse(log));
    }

    @DeleteMapping("/conversations/{conversationId}")
    public Result<Void> deleteConversation(@PathVariable Long conversationId) {
        Long userId = userService.getCurrentUserId();
        MedConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return Result.error("会话不存在");
        }
        conversationMapper.deleteById(conversationId);
        return Result.success();
    }

    @PostMapping("/feedback")
    public Result<Void> submitFeedback(@RequestBody ChatRequestDTO.FeedbackDTO dto) {
        MedMessage message = messageMapper.selectById(dto.getMessageId());
        if (message == null) {
            return Result.error("消息不存在");
        }
        message.setFeedback(dto.getRating());
        messageMapper.updateById(message);
        return Result.success();
    }

    @GetMapping("/export/{conversationId}")
    public ResponseEntity<byte[]> exportMarkdown(@PathVariable Long conversationId) {
        Long userId = userService.getCurrentUserId();
        MedConversation conv = conversationMapper.selectById(conversationId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            return ResponseEntity.notFound().build();
        }

        List<MedMessage> messages = messageMapper.selectList(
                new LambdaQueryWrapper<MedMessage>()
                        .eq(MedMessage::getConversationId, conversationId)
                        .orderByAsc(MedMessage::getCreateTime));

        byte[] bytes = buildMarkdown(conv, messages).getBytes(StandardCharsets.UTF_8);
        String filename = "medirag-" + conversationId + ".md";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("text/markdown; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(bytes);
    }

    private String buildMarkdown(MedConversation conv, List<MedMessage> messages) {
        StringBuilder md = new StringBuilder();
        String exportTime = LocalDateTime.now().format(DT_FMT);
        String createTime = conv.getCreateTime() != null ? conv.getCreateTime().format(DT_FMT) : "-";

        md.append("# ").append(conv.getTitle()).append("\n\n");
        md.append("> **平台**: MediRAG 智能医疗问答  \n");
        md.append("> **创建时间**: ").append(createTime).append("  \n");
        md.append("> **导出时间**: ").append(exportTime).append("  \n");
        md.append("> **消息数量**: ").append(messages.size()).append("  \n\n");
        md.append("---\n\n");

        for (MedMessage msg : messages) {
            String time = msg.getCreateTime() != null ? msg.getCreateTime().format(DT_FMT) : "";
            if ("user".equals(msg.getRole())) {
                md.append("### 用户");
                if (!time.isEmpty()) {
                    md.append(" `").append(time).append("`");
                }
                md.append("\n\n").append(msg.getContent()).append("\n\n");
            } else {
                md.append("### MediRAG");
                if (!time.isEmpty()) {
                    md.append(" `").append(time).append("`");
                }
                if (msg.getResponseTime() != null) {
                    md.append(" ").append(msg.getResponseTime()).append("ms");
                }
                md.append("\n\n").append(msg.getContent()).append("\n\n");
                appendSources(md, msg.getSources());

                if (msg.getFeedback() != null && msg.getFeedback() == 1) {
                    md.append("> 用户反馈：有帮助\n\n");
                } else if (msg.getFeedback() != null && msg.getFeedback() == -1) {
                    md.append("> 用户反馈：无帮助\n\n");
                }
            }
            md.append("---\n\n");
        }

        md.append("*本文档由 MediRAG 自动生成，仅供参考，不构成医疗诊断建议。*\n");
        return md.toString();
    }

    private void appendSources(StringBuilder md, String sourcesJson) {
        if (sourcesJson == null || sourcesJson.isBlank()) {
            return;
        }
        try {
            JSONArray sources = JSON.parseArray(sourcesJson);
            if (sources == null || sources.isEmpty()) {
                return;
            }
            md.append("**参考来源**\n\n");
            for (int i = 0; i < sources.size(); i++) {
                JSONObject s = sources.getJSONObject(i);
                String name = s.getString("name");
                String chapter = s.getString("chapter");
                Integer page = s.getInteger("pageNumber");
                md.append(i + 1).append(". 《").append(name != null ? name : "医学文献").append("》");
                if (chapter != null && !chapter.isBlank()) {
                    md.append(" - ").append(chapter);
                }
                if (page != null && page > 0) {
                    md.append(" - 第").append(page).append("页");
                }
                md.append("\n");
            }
            md.append("\n");
        } catch (Exception ignored) {
        }
    }
}
