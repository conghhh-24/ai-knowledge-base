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
            documents, chunks, parser, ai, mock(RagAnswerCache.class), state, 10, 2);
    private Path uploaded;

    @BeforeEach
    void setup() {
        AuthContext.setUserId(987654321L);
        when(state.create(anyLong(), anyString(), anyString(), anyString(), anyLong()))
                .thenAnswer(call -> { uploaded = Path.of(call.getArgument(2, String.class)); return 12L; });
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
        var result = service.upload(file());
        assertEquals("PARSED", result.getStatus());
        assertTrue(Files.exists(uploaded));
        List<String> expected = List.of("abcdefghij", "ijklmnopqr", "qrstuvwxyz");
        verify(ai).upsertVectors(12L, expected);
        verify(state).complete(12L, 987654321L, expected);
        verify(state, never()).fail(anyLong(), anyLong());
    }

    @Test
    void partialIndexFailureMarksFailedAndRemovesVectorsAndFile() {
        RuntimeException original = new RuntimeException("second batch failed");
        doThrow(original).when(ai).upsertVectors(eq(12L), anyList());
        assertSame(original, assertThrows(RuntimeException.class, () -> service.upload(file())));
        verify(state).fail(12L, 987654321L);
        verify(ai).deleteVectors(12L);
        verify(state, never()).complete(anyLong(), anyLong(), anyList());
        assertFalse(Files.exists(uploaded));
    }

    @Test
    void databaseCompletionFailureAlsoCompensatesVectorWrite() {
        doThrow(new IllegalStateException("DB failed"))
                .when(state).complete(anyLong(), anyLong(), anyList());
        assertThrows(IllegalStateException.class, () -> service.upload(file()));
        verify(state).fail(12L, 987654321L);
        verify(ai).deleteVectors(12L);
        assertFalse(Files.exists(uploaded));
    }

    @Test
    void parsingFailureIsPersistedWithoutUnnecessaryVectorRequest() throws Exception {
        doThrow(new java.io.IOException("bad UTF-8")).when(parser).parse(any(), anyInt(), anyInt());
        assertThrows(RuntimeException.class, () -> service.upload(file()));
        verify(state).fail(12L, 987654321L);
        verifyNoInteractions(ai);
        assertFalse(Files.exists(uploaded));
    }

    @Test
    void cleanupFailurePreservesOriginalErrorAndStillRemovesFile() {
        RuntimeException original = new RuntimeException("index failed");
        doThrow(original).when(ai).upsertVectors(anyLong(), anyList());
        doThrow(new IllegalStateException("cleanup unavailable")).when(ai).deleteVectors(12L);
        assertSame(original, assertThrows(RuntimeException.class, () -> service.upload(file())));
        assertEquals(1, original.getSuppressed().length);
        verify(state).fail(12L, 987654321L);
        assertFalse(Files.exists(uploaded));
    }

    @Test
    void invalidConfigurationIsRejected() {
        for (int[] config : new int[][]{{0,0}, {10,-1}, {10,10}, {10,11}}) {
            assertThrows(IllegalArgumentException.class, () -> new DocumentServiceImpl(
                    documents, chunks, parser, ai, mock(RagAnswerCache.class), state, config[0], config[1]));
        }
    }
}
