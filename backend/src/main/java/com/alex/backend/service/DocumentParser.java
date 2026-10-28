package com.alex.backend.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads local text documents and splits their content into overlapping chunks. */
@Service
public class DocumentParser {

    public String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public List<String> parse(Path path, int chunkSize, int overlap) throws IOException {
        return split(read(path), chunkSize, overlap);
    }

    public List<String> split(String content, int chunkSize, int overlap) {
        if (content == null || content.isEmpty()) {
            return List.of();
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be greater than zero");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be between 0 and chunkSize - 1");
        }

        List<String> chunks = new ArrayList<>();
        int step = chunkSize - overlap;
        for (int start = 0; start < content.length(); start += step) {
            int end = Math.min(start + chunkSize, content.length());
            chunks.add(content.substring(start, end));
            if (end == content.length()) {
                break;
            }
        }
        return chunks;
    }
}
