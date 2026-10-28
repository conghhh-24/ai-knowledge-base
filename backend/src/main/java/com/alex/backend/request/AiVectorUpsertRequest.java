package com.alex.backend.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AiVectorUpsertRequest {
    @JsonProperty("document_id")
    private Long documentId;

    @JsonProperty("chunk_start")
    private int chunkStart;

    private List<String> chunks;
}
