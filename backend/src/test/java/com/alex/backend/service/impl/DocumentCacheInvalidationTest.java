package com.alex.backend.service.impl;

import com.alex.backend.client.AiClient;
import com.alex.backend.context.AuthContext;
import com.alex.backend.exception.BusinessException;
import com.alex.backend.mapper.DocumentChunkMapper;
import com.alex.backend.mapper.DocumentMapper;
import com.alex.backend.service.DocumentParser;
import com.alex.backend.service.DocumentUploadState;
import com.alex.backend.service.RagAnswerCache;
import com.alex.backend.vo.DocumentVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentCacheInvalidationTest {
    private final DocumentMapper documents = mock(DocumentMapper.class);
    private final DocumentChunkMapper chunks = mock(DocumentChunkMapper.class);
    private final AiClient ai = mock(AiClient.class);
    private final RagAnswerCache cache = mock(RagAnswerCache.class);
    private final DocumentServiceImpl service = new DocumentServiceImpl(
            documents, chunks, mock(DocumentParser.class), ai, cache,
            mock(DocumentUploadState.class), mock(com.alex.backend.service.DocumentOperationLock.class), 500, 50);
    @TempDir Path directory;

    @BeforeEach
    void setup() {
        AuthContext.setUserId(7L);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void cleanup() {
        AuthContext.clear();
        TransactionSynchronizationManager.clearSynchronization();
    }

    private Path prepareDelete() throws Exception {
        Path file = Files.writeString(directory.resolve("document.txt"), "content");
        when(documents.findFilePathByIdAndUserId(12L, 7L)).thenReturn(file.toString());
        when(documents.deleteByIdAndUserId(12L, 7L)).thenReturn(1);
        return file;
    }

    private void commit() {
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
    }

    @Test
    void deleteInvalidatesOnlyAfterCommitAndCapturesUser() throws Exception {
        Path file = prepareDelete();
        service.deleteDocument(12L);
        assertFalse(Files.exists(file));
        verifyNoInteractions(cache);
        AuthContext.clear();
        commit();
        verify(cache).evictDocument(7L, 12L);
    }

    @Test
    void rollbackDoesNotInvalidate() throws Exception {
        prepareDelete();
        service.deleteDocument(12L);
        TransactionSynchronizationManager.getSynchronizations().forEach(
                callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(cache);
    }

    @Test
    void vectorDeletionFailureKeepsFileAndDatabaseForRetry() throws Exception {
        Path file = prepareDelete();
        doThrow(new IllegalStateException("AI unavailable")).when(ai).deleteVectors(12L);
        assertThrows(IllegalStateException.class, () -> service.deleteDocument(12L));
        assertTrue(Files.exists(file));
        verify(documents, never()).deleteByIdAndUserId(anyLong(), anyLong());
        verifyNoInteractions(chunks, cache);
    }

    @Test
    void unauthorizedDeleteDoesNotInvalidate() {
        assertThrows(BusinessException.class, () -> service.deleteDocument(12L));
        commit();
        verifyNoInteractions(cache, chunks, ai);
    }

    @Test
    void failedDeleteDoesNotRegisterCleanup() throws Exception {
        prepareDelete();
        when(documents.deleteByIdAndUserId(12L, 7L)).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.deleteDocument(12L));
        commit();
        verifyNoInteractions(cache);
    }

    @Test
    void redisFailureAfterDeletionDoesNotPretendDeletionRolledBack() throws Exception {
        prepareDelete();
        service.deleteDocument(12L);
        doThrow(new DataAccessResourceFailureException("offline"))
                .when(cache).evictDocument(7L, 12L);
        assertDoesNotThrow(this::commit);
    }

    private void prepareRebuild() {
        DocumentVO document = new DocumentVO();
        document.setStatus("PARSED");
        when(documents.findByIdAndUserId(12L, 7L)).thenReturn(document);
        when(chunks.findContentsByDocumentId(12L)).thenReturn(List.of("first", "second"));
    }

    @Test
    void rebuildInvalidatesBeforeAndAfterWritingOrderedChunks() {
        prepareRebuild();
        service.rebuildIndex(12L);
        var order = inOrder(documents, chunks, cache, ai);
        order.verify(documents).findByIdAndUserId(12L, 7L);
        order.verify(chunks).findContentsByDocumentId(12L);
        order.verify(cache).evictDocument(7L, 12L);
        order.verify(ai).upsertVectors(12L, List.of("first", "second"));
        order.verify(cache).evictDocument(7L, 12L);
    }

    @Test
    void failedRebuildAlsoInvalidatesPotentiallyChangedIndex() {
        prepareRebuild();
        RuntimeException failure = new RuntimeException("second batch failed");
        doThrow(failure).when(ai).upsertVectors(12L, List.of("first", "second"));
        assertSame(failure, assertThrows(RuntimeException.class, () -> service.rebuildIndex(12L)));
        verify(cache, times(2)).evictDocument(7L, 12L);
    }

    @Test
    void redisFailureBeforeRebuildPreventsIndexChanges() {
        prepareRebuild();
        doThrow(new DataAccessResourceFailureException("offline"))
                .when(cache).evictDocument(7L, 12L);
        assertThrows(DataAccessResourceFailureException.class, () -> service.rebuildIndex(12L));
        verifyNoInteractions(ai);
    }

    @Test
    void unauthorizedRebuildCannotReadChunksOrClearCache() {
        assertThrows(BusinessException.class, () -> service.rebuildIndex(12L));
        verifyNoInteractions(chunks, cache, ai);
    }

    @Test
    void emptyChunksDoNotChangeIndexOrCache() {
        prepareRebuild();
        when(chunks.findContentsByDocumentId(12L)).thenReturn(List.of());
        assertThrows(BusinessException.class, () -> service.rebuildIndex(12L));
        verifyNoInteractions(cache, ai);
    }
}
