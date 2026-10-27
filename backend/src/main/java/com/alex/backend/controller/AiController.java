package com.alex.backend.controller;

import com.alex.backend.service.ChatMessageService;
import com.alex.backend.common.Result;
import com.alex.backend.request.AiChatRequest;
import com.alex.backend.vo.AiChatVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
//它告诉 Spring：
//1. 这是一个处理 HTTP 请求的 Controller；
//2. 类中的方法返回的对象要直接转换成 JSON 返回给客户端。
@RequestMapping("/api/ai")
public class AiController {

    private final ChatMessageService chatMessageService;
    public AiController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }
    @PostMapping("/chat")
    public Result<AiChatVO> chat(@RequestBody @Valid AiChatRequest request) {//@RequestBody它告诉 Spring：把 HTTP 请求体中的 JSON 转换成 AiChatRequest 对象
        return Result.success(chatMessageService.chat(request));
    }
}
