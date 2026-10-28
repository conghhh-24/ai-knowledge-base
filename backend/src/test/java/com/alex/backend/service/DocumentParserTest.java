package com.alex.backend.service;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentParserTest {
    private final DocumentParser parser = new DocumentParser();

    @Test
    void splitsContentWithOverlap() {
        assertEquals(List.of("0123", "3456", "6789"),
                parser.split("0123456789", 4, 1));
    }

    @Test
    void shortAndEmptyContentAreHandled() {
        assertEquals(List.of("你好"), parser.split("你好", 10, 2));
        assertEquals(List.of(), parser.split("", 10, 2));
    }

    @Test
    void readsUtf8TextFile() throws Exception {
        Path path = Files.createTempFile("document-parser-", ".md");
        try {
            Files.writeString(path, "标题\n内容", java.nio.charset.StandardCharsets.UTF_8);
            assertEquals(List.of("标题\n内容"), parser.parse(path, 100, 0));
        } finally {
            Files.deleteIfExists(path);
        }
    }

    @Test
    void rejectsInvalidChunkSettings() {
        assertThrows(IllegalArgumentException.class, () -> parser.split("abc", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> parser.split("abc", 3, 3));
        assertThrows(IllegalArgumentException.class, () -> parser.split("abc", 3, -1));
    }
}
