package com.oj.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * AI 聊天消息
 */
@Data
@TableName("ai_message")
public class AiMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 角色: user / assistant */
    private String role;

    /** 消息内容 */
    private String content;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
}
