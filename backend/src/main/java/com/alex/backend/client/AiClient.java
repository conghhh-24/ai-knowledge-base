package com.alex.backend.client;

import com.alex.backend.request.AiChatRequest;
import com.alex.backend.vo.AiChatVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

@Component
public class AiClient {

    private final RestClient restClient;
    //这是 Spring 提供的 HTTP 客户端构造器，用来创建发送请求的对象

    public AiClient(
            RestClient.Builder builder,
            @Value("${ai.service.url}") String aiServiceUrl
    ) {
        this.restClient = builder
                .baseUrl(aiServiceUrl)
                .build();
    }

    public AiChatVO chat(AiChatRequest request) {
        return restClient.post()
                .uri("/chat")
                //表示发送：POST http://localhost:8000/chat
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                //把 AiChatRequest 转成 JSON
                .retrieve()
                .body(AiChatVO.class);
                //读取 FastAPI 返回的 JSON，并转换成 AiChatVO
    }
}