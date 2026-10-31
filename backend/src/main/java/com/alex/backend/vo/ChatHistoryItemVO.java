package com.alex.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ChatHistoryItemVO {
    private Long id;
    private Long documentId;
    private String question;
    private String answer;
    private java.util.List<AiSourceVO> sources = java.util.List.of();
    private LocalDateTime createTime;
}
