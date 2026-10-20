package com.alex.backend.service.impl;


import com.alex.backend.common.ErrorCode;
import com.alex.backend.context.AuthContext;
import com.alex.backend.exception.BusinessException;
import com.alex.backend.service.DocumentService;
import com.alex.backend.vo.DocumentVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class DocumentServiceImpl implements DocumentService {
    @Override
    public DocumentVO upload(MultipartFile file){
        validateFile(file);
        Long userId = AuthContext.getUserId();
        try{
            Path directory = Path.of("storage", userId.toString()).toAbsolutePath();
            Files.createDirectories(directory);
            String fileName = file.getOriginalFilename();
            String suffix = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
            String storedName = UUID.randomUUID().toString() + suffix;
            Path target = directory.resolve(storedName);
            file.transferTo(target);
        }catch(IOException e){
            throw new RuntimeException("保存文档文件失败", e);
        }

        return null;
    }

    private void validateFile(MultipartFile file){
        if(file==null||file.isEmpty()){
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        if(file.getSize()>50L*1024*1024){//L 表示 long 类型整数，用来参与字节数运算
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String filename = file.getOriginalFilename();
        if(filename==null){
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        //当文件名没有.时，lastIndexOf(".") 会返回 -1，传给 substring() 会报错
        //检查点号位置
        int dotIndex = filename.lastIndexOf(".");
        if (dotIndex < 0) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        String suffix = filename.substring(dotIndex).toLowerCase();
            if(!(suffix.equals(".txt")||suffix.equals(".md"))){
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
    }
}
