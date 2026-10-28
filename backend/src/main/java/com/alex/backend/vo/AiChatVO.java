package com.alex.backend.vo;

import lombok.Data;

import java.util.List;

@Data
public class AiChatVO {
    private String answer;

    private List<AiSourceVO> sources;
}
