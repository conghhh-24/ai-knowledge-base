package com.alex.backend.service.impl;


import com.alex.backend.service.DocumentService;
import com.alex.backend.vo.DocumentVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentServiceImpl implements DocumentService {
    @Override
    public DocumentVO upload( MultipartFile file){
        return null;
    }
}
