package com.alex.backend.service.impl;


import com.alex.backend.common.ErrorCode;
import com.alex.backend.client.AiClient;
import com.alex.backend.context.AuthContext;
import com.alex.backend.exception.BusinessException;
import com.alex.backend.mapper.DocumentMapper;
import com.alex.backend.mapper.DocumentChunkMapper;
import com.alex.backend.service.DocumentParser;
import com.alex.backend.service.DocumentService;
import com.alex.backend.service.RagAnswerCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import com.alex.backend.vo.DocumentVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Service
public class DocumentServiceImpl implements DocumentService {
    private static final Logger log = LoggerFactory.getLogger(DocumentServiceImpl.class);
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final DocumentParser documentParser;
    private final AiClient aiClient;
    private final RagAnswerCache answerCache;

    public DocumentServiceImpl(DocumentMapper documentMapper,
                               DocumentChunkMapper documentChunkMapper,
                               DocumentParser documentParser,
                               AiClient aiClient, RagAnswerCache answerCache) {
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.documentParser = documentParser;
        this.aiClient = aiClient;
        this.answerCache = answerCache;
    }
    @Transactional
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
        } catch (IOException e) {
            throw new RuntimeException("保存文档文件失败", e);
        }
        //文件内容已保存到磁盘，接下来把文件信息写入数据库
        Long documentId;
           try {
               documentId = documentMapper.save(
                       userId,
                       fileName,
                       target.toString(),
                       suffix,
                       file.getSize(),
                       "UPLOADED"
               );
               List<String> chunks = documentParser.parse(target, 500, 50);
               int savedChunks = documentChunkMapper.saveBatch(documentId, chunks);
               if (savedChunks != chunks.size()) {
                   throw new IllegalStateException("保存文档分块失败");
               }
               aiClient.upsertVectors(documentId, chunks);
               if (documentMapper.updateStatus(documentId, userId, "PARSED") != 1) {
                   throw new IllegalStateException("更新文档状态失败");
               }
           } catch (IOException e) {
               try {
                   Files.deleteIfExists(target);
               } catch (IOException cleanupException) {
                   e.addSuppressed(cleanupException);
               }
               throw new RuntimeException("解析文档失败", e);
           } catch(RuntimeException e) {
               try {
                   Files.deleteIfExists(target);
               }catch (IOException cleanupException) {
                   e.addSuppressed(cleanupException);
               }
               throw e;
           }
        DocumentVO documentVO = new DocumentVO();
        documentVO.setId(documentId);
        documentVO.setFileName(fileName);
        documentVO.setFileType(suffix);
        documentVO.setFileSize(file.getSize());
        documentVO.setStatus("PARSED");

        return documentVO;
    }
    @Override
    public List<DocumentVO> listDocuments(){
        Long userId = AuthContext.getUserId();
        return documentMapper.findByUserId(userId);
    }

    @Override
    public DocumentVO getDocumentById(Long id){
       DocumentVO documentVO = documentMapper.findByIdAndUserId(id, AuthContext.getUserId());
       if(documentVO == null){
           throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
       }
       return documentVO;
    }
    @Transactional//表示这个方法里的数据库修改先不要正式生效，等方法正常执行完再一起确认；如果中途抛出需要回滚的异常，就撤销这些修改。
    @Override
    public void deleteDocument(Long id){
        Long userId = AuthContext.getUserId();
        String filePath = documentMapper.findFilePathByIdAndUserId(id, AuthContext.getUserId());
        if(filePath == null){
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        documentChunkMapper.deleteByDocumentId(id);
        int n = documentMapper.deleteByIdAndUserId(id, AuthContext.getUserId());
        if(n == 0){
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        try{
            Files.deleteIfExists(Path.of(filePath));
        }catch (IOException exception) {
            throw new RuntimeException("删除文档文件失败", exception);
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    answerCache.evictDocument(userId, id);
                } catch (DataAccessException e) {
                    // Deletion has already committed. Ownership checks block access;
                    // remaining cache entries expire through their original TTL.
                    log.warn("Document {} deleted, but cache cleanup failed; waiting for TTL", id, e);
                }
            }
        });
    }

    @Override
    public void rebuildIndex(Long id) {
        getDocumentById(id);
        Long userId = AuthContext.getUserId();
        List<String> chunks = documentChunkMapper.findContentsByDocumentId(id);
        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        // A failed batch may still have changed vectors; invalidate on both paths.
        answerCache.evictDocument(userId, id);
        try {
            aiClient.upsertVectors(id, chunks);
        } catch (RuntimeException failure) {
            try {
                answerCache.evictDocument(userId, id);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
        answerCache.evictDocument(userId, id);
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
