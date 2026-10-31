package com.alex.backend.service;

import com.alex.backend.client.AiClient;
import com.alex.backend.common.ErrorCode;
import com.alex.backend.context.AuthContext;
import com.alex.backend.exception.BusinessException;
import com.alex.backend.mapper.UploadRequestMapper;
import com.alex.backend.vo.DocumentVO;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = "jwt.secret=upload-test-only-secret-0123456789abcdef")
@EnabledIfSystemProperty(named = "document.mysql-test", matches = "true")
class UploadIdempotencyMySqlTest {
    private static final long USER = -987654330L;
    @Autowired DocumentService service;
    @Autowired DocumentUploadState state;
    @Autowired UploadRequestMapper requests;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean AiClient ai;

    private DocumentVO upload(long user, String key, String text) {
        AuthContext.setUserId(user);
        try {
            return service.upload(new MockMultipartFile("file", "idempotent.txt", "text/plain",
                    text.getBytes(java.nio.charset.StandardCharsets.UTF_8)), key);
        } finally { AuthContext.clear(); }
    }

    @AfterEach
    void cleanup() throws Exception {
        for (long user : new long[]{USER, USER - 1}) {
            Path directory = Path.of("storage", Long.toString(user)).toAbsolutePath().normalize();
            for (String file : jdbc.queryForList("SELECT file_path FROM document WHERE user_id = ?", String.class, user)) {
                Path target = Path.of(file).toAbsolutePath().normalize();
                if (target.startsWith(directory)) Files.deleteIfExists(target);
            }
            jdbc.update("DELETE c FROM document_chunk c JOIN document d ON c.document_id=d.id WHERE d.user_id=?", user);
            jdbc.update("DELETE FROM document WHERE user_id=?", user);
            jdbc.update("DELETE FROM upload_request WHERE user_id=?", user);
            if (Files.exists(directory)) Files.delete(directory);
        }
    }

    @Test
    void successReplayPreservesSnapshotAndSkipsAiEvenAfterDeletion() {
        var first = upload(USER, "success", "content");
        var replay = upload(USER, "success", "content");
        assertEquals(first.getId(), replay.getId());
        assertEquals(first.getFileName(), replay.getFileName());
        assertEquals("SUCCEEDED", requests.find(USER, "success").status());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM document WHERE user_id=?", Long.class, USER));
        verify(ai, times(1)).upsertVectors(anyLong(), anyList());
        AuthContext.setUserId(USER);
        try { service.deleteDocument(first.getId()); } finally { AuthContext.clear(); }
        assertEquals(first.getId(), upload(USER, "success", "content").getId());
        verify(ai, times(1)).upsertVectors(anyLong(), anyList());
    }

    @Test
    void concurrentDuplicateSeesProcessingThenReplaysSuccess() throws Exception {
        var started = new CountDownLatch(1);
        var finish = new CountDownLatch(1);
        doAnswer(call -> {
            started.countDown();
            if (!finish.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return null;
        }).when(ai).upsertVectors(anyLong(), anyList());
        var pool = Executors.newSingleThreadExecutor();
        Future<DocumentVO> first = pool.submit(() -> upload(USER, "parallel", "content"));
        try {
            assertTrue(started.await(10, TimeUnit.SECONDS));
            assertEquals(ErrorCode.UPLOAD_PROCESSING,
                    assertThrows(BusinessException.class, () -> upload(USER, "parallel", "content")).getErrorCode());
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM document WHERE user_id=?", Long.class, USER));
            finish.countDown();
            assertEquals(first.get(10, TimeUnit.SECONDS).getId(), upload(USER, "parallel", "content").getId());
            verify(ai, times(1)).upsertVectors(anyLong(), anyList());
        } finally { finish.countDown(); pool.shutdown(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS)); }
    }

    @Test
    void failedRequestReturnsStoredErrorWithoutRetryingExternalWork() {
        doThrow(new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE)).when(ai).upsertVectors(anyLong(), anyList());
        assertEquals(ErrorCode.AI_SERVICE_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> upload(USER, "failed", "content")).getErrorCode());
        assertEquals("FAILED", requests.find(USER, "failed").status());
        assertEquals(ErrorCode.AI_SERVICE_UNAVAILABLE,
                assertThrows(BusinessException.class, () -> upload(USER, "failed", "content")).getErrorCode());
        assertEquals("FAILED", jdbc.queryForObject("SELECT status FROM document WHERE user_id=?", String.class, USER));
        verify(ai, times(1)).upsertVectors(anyLong(), anyList());
        verify(ai, times(1)).deleteVectors(anyLong());
    }

    @Test
    void sameKeyDifferentPayloadRejectedAndUsersAndNewKeysAreIndependent() {
        long first = upload(USER, "key", "content").getId();
        assertEquals(ErrorCode.IDEMPOTENCY_KEY_CONFLICT,
                assertThrows(BusinessException.class, () -> upload(USER, "key", "different")).getErrorCode());
        assertNotEquals(first, upload(USER - 1, "key", "content").getId());
        assertNotEquals(first, upload(USER, "new-key", "content").getId());
        verify(ai, times(3)).upsertVectors(anyLong(), anyList());
    }

    @Test
    void claimAndDocumentRollbackTogetherAndKeyCanBeClaimedAfterRollback() {
        assertThrows(RuntimeException.class, () -> state.createUpload(USER, "rollback", "a".repeat(64),
                "x".repeat(256), "not-written", ".txt", 1));
        assertNull(requests.find(USER, "rollback"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM document WHERE user_id=?", Long.class, USER));
        assertNotNull(upload(USER, "rollback", "content"));
    }

    @Test
    void uniqueConstraintElectsOnlyOneConcurrentClaim() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        Callable<Boolean> claim = () -> {
            assertTrue(start.await(5, TimeUnit.SECONDS));
            try {
                state.createUpload(USER, "race", "a".repeat(64), "race.txt", "not-written", ".txt", 1);
                return true;
            } catch (org.springframework.dao.DuplicateKeyException duplicate) {
                assertEquals("PROCESSING", requests.find(USER, "race").status());
                return false;
            }
        };
        try {
            var one = pool.submit(claim); var two = pool.submit(claim); start.countDown();
            assertNotEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM document WHERE user_id=?", Long.class, USER));
        } finally { start.countDown(); pool.shutdown(); assertTrue(pool.awaitTermination(15, TimeUnit.SECONDS)); }
    }

    @Test
    void completionRollbackDoesNotMarkRequestSucceeded() {
        Long id = state.createUpload(USER, "complete-fail", "a".repeat(64), "test.txt", "not-written", ".txt", 1);
        var result = new DocumentVO(); result.setId(id);
        assertThrows(RuntimeException.class, () -> state.completeUpload(id, USER, "complete-fail",
                java.util.Arrays.asList("valid", null), result));
        assertEquals("PROCESSING", requests.find(USER, "complete-fail").status());
        assertEquals("UPLOADED", jdbc.queryForObject("SELECT status FROM document WHERE id=?", String.class, id));
        state.failUpload(id, USER, "complete-fail", ErrorCode.UPLOAD_FAILED);
        assertEquals("FAILED", requests.find(USER, "complete-fail").status());
    }
}
