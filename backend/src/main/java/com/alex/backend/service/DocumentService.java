package com.alex.backend.service;


import com.alex.backend.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
    DocumentVO upload(MultipartFile file, String idempotencyKey);
    com.alex.backend.vo.PageResult<DocumentVO> listDocuments(int page, int pageSize);
    DocumentVO getDocumentById(Long id);
    void deleteDocument(Long id);
    void rebuildIndex(Long id);
}
