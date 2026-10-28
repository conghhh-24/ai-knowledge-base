package com.alex.backend.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AiSourceVO {
    private String text;

    @JsonProperty("chunk_index")
    private int chunkIndex;
    private double distance;
}
