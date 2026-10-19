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
@RequestMapping("/api/documents")
public class DocumentController {
    private final DocumentService documentService;

    public DocumentController(DocumentService documentService){
        this.documentService = documentService;
    }
    @PostMapping("/upload")
    public Result<DocumentVO> upload(@RequestParam("file") MultipartFile file){
        return Result.success(documentService.upload(file));
    }
}
