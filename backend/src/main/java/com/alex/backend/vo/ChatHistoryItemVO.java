package com.alex.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatHistoryItemVO {
    private Long id;
    private Long documentId;
    private String question;
    private String answer;
    private LocalDateTime createTime;
}
