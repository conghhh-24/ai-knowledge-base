package com.alex.backend.mapper;

public interface DocumentMapper {
    Long save(Long userId,
              String fileName,
              String filePath,
              String fileType,
              Long fileSize,
              String status);
}
