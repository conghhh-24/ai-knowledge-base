package com.alex.backend.service;


import com.alex.backend.vo.DocumentVO;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentService {
    DocumentVO upload(MultipartFile file);
}
