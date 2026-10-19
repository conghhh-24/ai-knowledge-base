package com.alex.backend.vo;

public class DocumentVO {
    private long id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String status;

    public long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public String getFileType() {
        return fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public String getStatus() {
        return status;
    }
}
