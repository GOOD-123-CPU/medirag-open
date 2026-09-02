package com.medirag.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 医疗知识库文档实体
 */
@Data
@TableName("med_knowledge_base")
public class MedKnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    private String category;

    private String fileUrl;

    private String fileType;

    private Long fileSize;

    private Integer chunkCount;

    /** 状态: uploading/processing/ready/failed */
    private String status;

    private String errorMsg;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
