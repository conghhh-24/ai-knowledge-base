package com.alex.backend.client;

import com.alex.backend.common.ErrorCode;
import com.alex.backend.exception.BusinessException;
import com.alex.backend.request.AiChatRequest;
import com.alex.backend.request.AiVectorUpsertRequest;
import com.alex.backend.vo.AiChatVO;
import com.alex.backend.vo.AiVectorUpsertVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

@Component
public class AiClient {
    private static final int UPSERT_BATCH_SIZE = 20;

    private final RestClient restClient;
    //这是 Spring 提供的 HTTP 客户端构造器，用来创建发送请求的对象

    private final RestClient indexClient;

    public AiClient(RestClient.Builder builder, String url) {
        this(builder, url, Duration.ofSeconds(3), Duration.ofSeconds(30), Duration.ofSeconds(60));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AiClient(RestClient.Builder builder,
            @Value("${ai.service.url}") String url,
            @Value("${ai.connect-timeout:3s}") Duration connectTimeout,
            @Value("${ai.chat-timeout:30s}") Duration chatTimeout,
            @Value("${ai.index-timeout:60s}") Duration indexTimeout) {
        this.restClient = createClient(builder, url, connectTimeout, chatTimeout);
        this.indexClient = createClient(builder, url, connectTimeout, indexTimeout);
    }

    private RestClient createClient(RestClient.Builder builder, String url, Duration connect, Duration read) {
        if (connect.isNegative() || connect.isZero() || read.isNegative() || read.isZero())
            throw new IllegalArgumentException("AI timeouts must be positive");
        HttpClient client = HttpClient.newBuilder().connectTimeout(connect)
                .version(HttpClient.Version.HTTP_1_1).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(read);
        return builder.requestFactory(factory).baseUrl(url).build();
    }

    private BusinessException transportFailure(ResourceAccessException ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.SocketTimeoutException)
                return new BusinessException(ErrorCode.AI_SERVICE_TIMEOUT);
        }
        return new BusinessException(ErrorCode.AI_SERVICE_UNAVAILABLE);
    }

    public AiChatVO chat(AiChatRequest request) {
        try{
            return restClient.post()
                    .uri("/chat")
                    //表示发送：POST http://localhost:8000/chat
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    //把 AiChatRequest 转成 JSON
                    .retrieve()
                    .body(AiChatVO.class);
            //读取 FastAPI 返回的 JSON，并转换成 AiChatVO
        }catch(ResourceAccessException e){
            throw transportFailure(e);
        }catch(HttpServerErrorException e){//表示下游返回了 HTTP 5xx
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
        }catch (HttpClientErrorException.UnprocessableEntity e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
        }

    }

    public void deleteVectors(Long documentId) {
        try {
            indexClient.delete().uri("/vectors/{documentId}", documentId)
                    .retrieve().toBodilessEntity();
        } catch (ResourceAccessException e) {
            throw transportFailure(e);
        } catch (HttpServerErrorException | HttpClientErrorException e) {
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
        }
    }

    public void upsertVectors(Long documentId, List<String> chunks) {
        for (int start = 0; start < chunks.size(); start += UPSERT_BATCH_SIZE) {
            int end = Math.min(start + UPSERT_BATCH_SIZE, chunks.size());
            AiVectorUpsertRequest request = new AiVectorUpsertRequest(
                    documentId, start, chunks.subList(start, end));
            try {
                AiVectorUpsertVO response = indexClient.post()
                        .uri("/vectors/upsert")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .retrieve()
                        .body(AiVectorUpsertVO.class);
                if (response == null || response.getCount() != end - start) {
                    throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
                }
            } catch (ResourceAccessException e) {
                throw transportFailure(e);
            } catch (HttpServerErrorException | HttpClientErrorException e) {
                throw new BusinessException(ErrorCode.AI_SERVICE_ERROR);
            }
        }
    }
}
