package com.alex.backend.service.impl;


import com.alex.backend.common.ErrorCode;
import com.alex.backend.context.AuthContext;
import com.alex.backend.exception.BusinessException;
import com.alex.backend.mapper.DocumentMapper;
import com.alex.backend.service.DocumentService;
import com.alex.backend.vo.DocumentVO;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@Service
public class DocumentServiceImpl implements DocumentService {
    private final DocumentMapper documentMapper;

    public DocumentServiceImpl(DocumentMapper documentMapper) {
        this.documentMapper = documentMapper;
    }
    @Override
    public DocumentVO upload(MultipartFile file) {
        validateFile(file);
        Long userId = AuthContext.getUserId();
        String fileName;
        Path target;
        String suffix;
        try {
            Path directory = Path.of("storage", userId.toString()).toAbsolutePath();
            Files.createDirectories(directory);
            fileName = file.getOriginalFilename();
            suffix = fileName.substring(fileName.lastIndexOf(".")).toLowerCase();
            String storedName = UUID.randomUUID().toString() + suffix;
            target = directory.resolve(storedName);
            file.transferTo(target);
            //文件内容已保存到磁盘，接下来把文件信息写入数据库
        } catch (IOException e) {
            throw new RuntimeException("保存文档文件失败", e);
        }

        Long documentId = documentMapper.save(
                userId,
                fileName,
                target.toString(),
                suffix,
                file.getSize(),
                "UPLOADED"
        );
        DocumentVO documentVO = new DocumentVO();
        documentVO.setId(documentId);
        documentVO.setFileName(fileName);
        documentVO.setFileType(suffix);
        documentVO.setFileSize(file.getSize());
        documentVO.setStatus("UPLOADED");

        return documentVO;
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
