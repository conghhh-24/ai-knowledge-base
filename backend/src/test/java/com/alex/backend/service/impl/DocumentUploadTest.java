package com.alex.backend.service.impl;

import com.alex.backend.client.AiClient;
import com.alex.backend.context.AuthContext;
import com.alex.backend.mapper.DocumentMapper;
import com.alex.backend.mapper.DocumentChunkMapper;
import com.alex.backend.service.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DocumentUploadTest {
    private final DocumentMapper documents = mock(DocumentMapper.class);
    private final DocumentChunkMapper chunks = mock(DocumentChunkMapper.class);
    private final AiClient ai = mock(AiClient.class);
    private final DocumentUploadState state = mock(DocumentUploadState.class);
    private final DocumentParser parser = spy(new DocumentParser());
    private final DocumentServiceImpl service = new DocumentServiceImpl(
            documents, chunks, parser, ai, mock(RagAnswerCache.class), state, mock(DocumentOperationLock.class), 10, 2);
    private Path uploaded;

    @BeforeEach
    void setup() {
        AuthContext.setUserId(987654321L);
        when(state.createUpload(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong()))
                .thenAnswer(call -> { uploaded = Path.of(call.getArgument(4, String.class)); return 12L; });
    }

    @AfterEach
    void cleanup() throws Exception {
        AuthContext.clear();
        if (uploaded != null) {
            Files.deleteIfExists(uploaded);
            // Only remove our empty test directory, never recursively delete storage.
            try { Files.deleteIfExists(uploaded.getParent()); }
            catch (DirectoryNotEmptyException ignored) { }
        }
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "test.txt", "text/plain",
                "abcdefghijklmnopqrstuvwxyz".getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void configuredChunkBoundariesAreUsedAndSuccessKeepsFile() {
        var result = service.upload(file(), "test-key");
        assertEquals("PARSED", result.getStatus());
        assertTrue(Files.exists(uploaded));
        List<String> expected = List.of("abcdefghij", "ijklmnopqr", "qrstuvwxyz");
        verify(ai).upsertVectors(12L, expected);
        verify(state).completeUpload(eq(12L), eq(987654321L), eq("test-key"), eq(expected), any());
        verify(state, never()).failUpload(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void partialIndexFailureMarksFailedAndRemovesVectorsAndFile() {
        RuntimeException original = new RuntimeException("second batch failed");
        doThrow(original).when(ai).upsertVectors(eq(12L), anyList());
        assertSame(original, assertThrows(RuntimeException.class, () -> service.upload(file(), "test-key")));
        verify(state).failUpload(eq(12L), eq(987654321L), eq("test-key"), any());
        verify(ai).deleteVectors(12L);
        verify(state, never()).completeUpload(anyLong(), anyLong(), anyString(), anyList(), any());
        assertFalse(Files.exists(uploaded));
    }

    @Test
    void databaseCompletionFailureAlsoCompensatesVectorWrite() {
        doThrow(new IllegalStateException("DB failed"))
                .when(state).completeUpload(anyLong(), anyLong(), anyString(), anyList(), any());
        assertThrows(IllegalStateException.class, () -> service.upload(file(), "test-key"));
        verify(state).failUpload(eq(12L), eq(987654321L), eq("test-key"), any());
        verify(ai).deleteVectors(12L);
        assertFalse(Files.exists(uploaded));
    }

    @Test
    void parsingFailureIsPersistedWithoutUnnecessaryVectorRequest() throws Exception {
        doThrow(new java.io.IOException("bad UTF-8")).when(parser).parse(any(), anyInt(), anyInt());
        assertThrows(RuntimeException.class, () -> service.upload(file(), "test-key"));
        verify(state).failUpload(eq(12L), eq(987654321L), eq("test-key"), any());
        verifyNoInteractions(ai);
        assertFalse(Files.exists(uploaded));
    }

    @Test
    void cleanupFailurePreservesOriginalErrorAndStillRemovesFile() {
        RuntimeException original = new RuntimeException("index failed");
        doThrow(original).when(ai).upsertVectors(anyLong(), anyList());
        doThrow(new IllegalStateException("cleanup unavailable")).when(ai).deleteVectors(12L);
        assertSame(original, assertThrows(RuntimeException.class, () -> service.upload(file(), "test-key")));
        assertEquals(1, original.getSuppressed().length);
        verify(state).failUpload(eq(12L), eq(987654321L), eq("test-key"), any());
        assertFalse(Files.exists(uploaded));
    }

    @Test
    void invalidConfigurationIsRejected() {
        for (int[] config : new int[][]{{0,0}, {10,-1}, {10,10}, {10,11}}) {
            assertThrows(IllegalArgumentException.class, () -> new DocumentServiceImpl(
                    documents, chunks, parser, ai, mock(RagAnswerCache.class), state, mock(DocumentOperationLock.class), config[0], config[1]));
        }
    }
    @Test
    void repeatedSuccessReturnsOriginalWithoutFilesParsingOrIndexing() throws Exception {
        doThrow(new org.springframework.dao.DuplicateKeyException("duplicate"))
                .when(state).createUpload(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
        var original = new com.alex.backend.vo.DocumentVO();
        original.setId(12L);
        when(state.replayUpload(eq(987654321L), eq("test-key"), anyString())).thenReturn(original);
        assertSame(original, service.upload(file(), "test-key"));
        verifyNoInteractions(ai, parser);
        verify(state, never()).completeUpload(anyLong(), anyLong(), anyString(), anyList(), any());
    }

    @Test
    void missingOrInvalidKeyRejectedBeforeCreatingAnything() {
        for (String key : new String[]{null, "", "contains space", "x".repeat(129)}) {
            assertThrows(com.alex.backend.exception.BusinessException.class, () -> service.upload(file(), key));
        }
        verifyNoInteractions(ai, parser);
        verify(state, never()).createUpload(anyLong(), anyString(), anyString(), anyString(), anyString(), anyString(), anyLong());
    }
}
