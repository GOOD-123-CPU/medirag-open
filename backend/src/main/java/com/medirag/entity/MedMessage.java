package com.medirag.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息实体
 */
@Data
@TableName("med_message")
public class MedMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /** 角色: user/assistant */
    private String role;

    private String content;

    /** 引用来源 JSON */
    private String sources;

    /** 检索过程日志 JSON */
    private String retrievalLog;

    /** 反馈: 1有用 -1无用 0未评 */
    private Integer feedback;

    /** 是否触发兜底 */
    private Integer isFallback;

    private Integer tokensUsed;

    private Integer responseTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
