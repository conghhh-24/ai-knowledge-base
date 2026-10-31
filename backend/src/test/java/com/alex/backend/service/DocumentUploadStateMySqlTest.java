package com.alex.backend.service;

import com.alex.backend.mapper.DocumentMapper;
import com.alex.backend.mapper.DocumentChunkMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "jwt.secret=document-state-test-only-secret-0123456789abcdef")
@EnabledIfSystemProperty(named = "document.mysql-test", matches = "true")
class DocumentUploadStateMySqlTest {
    @Autowired DocumentUploadState state;
    @Autowired DocumentMapper documents;
    @Autowired DocumentChunkMapper chunks;

    @Test
    void failedChunkTransactionRollsBackButFailedDocumentPersists() {
        long user = -987654321L;
        Long id = state.create(user, "transaction-test.txt", "unused-test-path", ".txt", 2L);
        try {
            assertEquals("UPLOADED", documents.findByIdAndUserId(id, user).getStatus());
            // Null content violates the real table's NOT NULL constraint.
            assertThrows(RuntimeException.class, () -> state.complete(id, user, Arrays.asList("ok", null)));
            assertTrue(chunks.findContentsByDocumentId(id).isEmpty());
            state.fail(id, user);
            assertEquals("FAILED", documents.findByIdAndUserId(id, user).getStatus());
        } finally {
            chunks.deleteByDocumentId(id);
            documents.deleteByIdAndUserId(id, user);
        }
    }
}
