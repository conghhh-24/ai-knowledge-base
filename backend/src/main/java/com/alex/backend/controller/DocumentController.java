package com.alex.backend.controller;

import com.alex.backend.common.Result;
import com.alex.backend.service.DocumentService;
import com.alex.backend.vo.DocumentVO;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/documents")//@RequestMapping给这个类里的接口设置公共路径
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public Result<List<DocumentVO>> listDocuments() {
        return Result.success(documentService.listDocuments());
    }

    @PostMapping("/upload")
    public Result<DocumentVO> upload(@RequestParam("file") MultipartFile file) {//@RequestParam 用于 Controller 从 HTTP 请求中接收参数
        return Result.success(documentService.upload(file));
    }

    @GetMapping("/{id}")
    public Result<DocumentVO> getDocumentById(@PathVariable Long id) {
        return Result.success(documentService.getDocumentById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return Result.success(null);
    }
}
