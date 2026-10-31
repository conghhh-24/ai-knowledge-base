package com.alex.backend.service;

import com.alex.backend.client.AiClient;
import com.alex.backend.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.util.Collections;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AiVectorCleanupTest {
    @Test
    void failedSecondBatchCanBeCleanedWithDeleteRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient.Builder configured = spy(builder);
        doReturn(configured).when(configured).requestFactory(any());
        AiClient client = new AiClient(configured, "http://ai.test");
        server.expect(requestTo("http://ai.test/vectors/upsert"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chunk_start").value(0))
                .andRespond(withSuccess("{\"count\":20}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://ai.test/vectors/upsert"))
                .andExpect(jsonPath("$.chunk_start").value(20))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo("http://ai.test/vectors/12"))
                .andExpect(method(HttpMethod.DELETE)).andRespond(withNoContent());
        assertThrows(BusinessException.class, () -> client.upsertVectors(12L, Collections.nCopies(21, "chunk")));
        client.deleteVectors(12L);
        server.verify();
    }

    @Test
    void deleteServerFailureIsNotReportedAsSuccess() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient.Builder configured = spy(builder);
        doReturn(configured).when(configured).requestFactory(any());
        AiClient client = new AiClient(configured, "http://ai.test");
        server.expect(requestTo("http://ai.test/vectors/12"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(BusinessException.class, () -> client.deleteVectors(12L));
        server.verify();
    }
}
