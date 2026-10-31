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
    @Autowired DocumentOperationLock operationLock;
    @Autowired DocumentService service;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Autowired com.alex.backend.mapper.ChatMessageMapper history;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;


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
    @Test
    void locksExcludeRebuildAndDeleteAndReleaseAfterFailure() throws Exception {
        try (var first = operationLock.acquire(-12345L)) {
            var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                assertEquals(com.alex.backend.common.ErrorCode.DOCUMENT_BUSY,
                        executor.submit(() -> assertThrows(com.alex.backend.exception.BusinessException.class,
                                () -> operationLock.acquire(-12345L)).getErrorCode()).get());
                executor.submit(() -> {
                    com.alex.backend.context.AuthContext.setUserId(-987654322L);
                    try {
                        assertEquals(com.alex.backend.common.ErrorCode.DOCUMENT_BUSY,
                                assertThrows(com.alex.backend.exception.BusinessException.class,
                                        () -> service.rebuildIndex(-12345L)).getErrorCode());
                        assertEquals(com.alex.backend.common.ErrorCode.DOCUMENT_BUSY,
                                assertThrows(com.alex.backend.exception.BusinessException.class,
                                        () -> service.deleteDocument(-12345L)).getErrorCode());
                    } finally { com.alex.backend.context.AuthContext.clear(); }
                }).get();
                try (var otherDocument = operationLock.acquire(-12346L)) { }
            } finally { executor.shutdownNow(); }
        }
        try (var retry = operationLock.acquire(-12345L)) { }
    }

    @Test
    void versionIncrementsAndSourcesRoundTripWithUserIsolationAndPaging() {
        long user = -987654322L;
        Long id = state.create(user, "page-test.txt", "test-path", ".txt", 2L);
        try {
            state.complete(id, user, java.util.List.of("chunk"));
            long version = documents.findByIdAndUserId(id, user).getIndexVersion();
            state.beginRebuild(id, user);
            assertEquals(version + 1, documents.findByIdAndUserId(id, user).getIndexVersion());
            assertEquals("INDEXING", documents.findByIdAndUserId(id, user).getStatus());
            assertThrows(RuntimeException.class, () -> state.beginRebuild(id, user));
            state.indexed(id, user);
            var source = new com.alex.backend.vo.AiSourceVO();
            source.setText("original citation"); source.setChunkIndex(3); source.setDistance(0.25);
            history.save(user, id, "q", "a", java.util.List.of(source));
            assertEquals(java.util.List.of(source), history.findPageByUserId(user, 0, 1).get(0).getSources());
            assertTrue(history.findPageByUserId(user - 1, 0, 1).isEmpty());
            assertEquals(1L, documents.countByUserId(user));
            assertEquals(id.longValue(), documents.findPageByUserId(user, 0, 1).get(0).getId());
            assertTrue(documents.findPageByUserId(user, 1, 1).isEmpty());
            assertTrue(documents.findPageByUserId(user - 1, 0, 1).isEmpty());
        } finally {
            jdbc.update("DELETE FROM chat_message WHERE user_id = ?", user);
            chunks.deleteByDocumentId(id);
            documents.deleteByIdAndUserId(id, user);
        }
    }
    @Test
    void deletionLockRemainsHeldUntilTransactionRollsBack() throws Exception {
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> {
            try (var lock = operationLock.acquire(-12347L)) { }
            var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                executor.submit(() -> assertThrows(com.alex.backend.exception.BusinessException.class,
                        () -> operationLock.acquire(-12347L))).get();
            } catch (Exception ex) { throw new RuntimeException(ex); }
            finally { executor.shutdownNow(); }
            status.setRollbackOnly();
        });
        try (var retry = operationLock.acquire(-12347L)) { }
    }
}
