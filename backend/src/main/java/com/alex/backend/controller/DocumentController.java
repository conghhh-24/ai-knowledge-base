package com.alex.backend.controller;

import com.alex.backend.common.Result;
import com.alex.backend.service.DocumentService;
import com.alex.backend.vo.DocumentVO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")//@RequestMapping给这个类里的接口设置公共路径
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService){
        this.documentService = documentService;
    }
    @PostMapping("/upload")
    public Result<DocumentVO> upload(@RequestParam("file") MultipartFile file){//@RequestParam 用于 Controller 从 HTTP 请求中接收参数
        return Result.success(documentService.upload(file));
    }
}
