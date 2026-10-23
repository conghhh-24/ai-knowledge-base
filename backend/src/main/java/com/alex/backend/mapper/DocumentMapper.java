package com.alex.backend.mapper;

import com.alex.backend.vo.DocumentVO;

import java.util.List;

public interface DocumentMapper {
    Long save(Long userId, String fileName, String filePath, String fileType, Long fileSize, String status);

    List<DocumentVO> findByUserId(Long userId);

    DocumentVO findByIdAndUserId(Long id, Long userId);

    String findFilePathByIdAndUserId(Long id, Long userId);

    int deleteByIdAndUserId(Long id, Long userId);
}
