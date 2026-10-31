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
import com.alex.backend.service.DocumentUploadState;
import org.springframework.beans.factory.annotation.Value;
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
    private final DocumentUploadState uploadState;
    private final com.alex.backend.service.DocumentOperationLock operationLock;
    private final int chunkSize;
    private final int chunkOverlap;

    public DocumentServiceImpl(DocumentMapper documentMapper,
                               DocumentChunkMapper documentChunkMapper,
                               DocumentParser documentParser,
                               AiClient aiClient, RagAnswerCache answerCache, DocumentUploadState uploadState, com.alex.backend.service.DocumentOperationLock operationLock,
                               @Value("${rag.chunk-size:500}") int chunkSize,
                               @Value("${rag.chunk-overlap:50}") int chunkOverlap) {
        if (chunkSize <= 0 || chunkOverlap < 0 || chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("Require chunk-size > 0 and 0 <= chunk-overlap < chunk-size");
        }
        this.operationLock = operationLock;
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.documentParser = documentParser;
        this.aiClient = aiClient;
        this.answerCache = answerCache;
        this.uploadState = uploadState;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }
    @Override
    public DocumentVO upload(MultipartFile file, String idempotencyKey) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9_-]{1,128}"))
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
        validateFile(file);
        Long userId = AuthContext.getUserId();
        if (userId == null) throw new BusinessException(ErrorCode.TOKEN_INVALID);
        String fileName = file.getOriginalFilename();
        String suffix = fileName.substring(fileName.lastIndexOf('.')).toLowerCase();
        Path target = Path.of("storage", userId.toString(), UUID.randomUUID() + suffix).toAbsolutePath();
        String hash = uploadFingerprint(file);
        Long documentId;
        try {
            documentId = uploadState.createUpload(userId, idempotencyKey, hash,
                    fileName, target.toString(), suffix, file.getSize());
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            return uploadState.replayUpload(userId, idempotencyKey, hash);
        }
        DocumentVO documentVO = new DocumentVO();
        documentVO.setId(documentId);
        documentVO.setFileName(fileName);
        documentVO.setFileType(suffix);
        documentVO.setFileSize(file.getSize());
        documentVO.setStatus("PARSED");
        boolean indexingStarted = false;
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
            List<String> chunks = documentParser.parse(target, chunkSize, chunkOverlap);
            if (chunks.isEmpty() || chunks.stream().allMatch(String::isBlank)) {
                throw new BusinessException(ErrorCode.PARAM_ERROR);
            }
            indexingStarted = true;
            aiClient.upsertVectors(documentId, chunks);
            uploadState.completeUpload(documentId, userId, idempotencyKey, chunks, documentVO);
        } catch (IOException | RuntimeException failure) {
            ErrorCode error = failure instanceof BusinessException business ? business.getErrorCode() : ErrorCode.UPLOAD_FAILED;
            try { uploadState.failUpload(documentId, userId, idempotencyKey, error); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            if (indexingStarted) {
                try { aiClient.deleteVectors(documentId); }
                catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
            try { Files.deleteIfExists(target); }
            catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            log.error("Document {} upload failed; inspect suppressed cleanup failures", documentId, failure);
            if (failure instanceof RuntimeException runtime) throw runtime;
            throw new RuntimeException("文档上传处理失败", failure);
        }
        return documentVO;
    }
    @Override
    public com.alex.backend.vo.PageResult<DocumentVO> listDocuments(int page, int pageSize){
        if (page < 1 || pageSize < 1 || pageSize > 100 || (long)(page - 1) * pageSize > Integer.MAX_VALUE)
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        Long userId = AuthContext.getUserId();
        if (userId == null) throw new BusinessException(ErrorCode.TOKEN_INVALID);
        return new com.alex.backend.vo.PageResult<>(documentMapper.countByUserId(userId), page, pageSize,
                documentMapper.findPageByUserId(userId, (page - 1) * pageSize, pageSize));
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
        try (var ignored = operationLock.acquire(id)) {
            deleteLocked(id);
        }
    }

    private void deleteLocked(Long id) {
        Long userId = AuthContext.getUserId();
        String filePath = documentMapper.findFilePathByIdAndUserId(id, AuthContext.getUserId());
        if(filePath == null){
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        // Persist before external effects. A failed delete remains retryable and
        // cannot answer from a stale cache after its vectors have disappeared.
        uploadState.deleting(id, userId);
        aiClient.deleteVectors(id);
        try{
            Files.deleteIfExists(Path.of(filePath));
        }catch (IOException exception) {
            throw new RuntimeException("删除文档文件失败", exception);
        }
        documentChunkMapper.deleteByDocumentId(id);
        if (documentMapper.deleteByIdAndUserId(id, userId) != 1) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
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
        try (var ignored = operationLock.acquire(id)) {
            rebuildLocked(id);
        }
    }

    private void rebuildLocked(Long id) {
        String status = getDocumentById(id).getStatus();
        if (!"PARSED".equals(status) && !"FAILED".equals(status)) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_READY);
        }
        Long userId = AuthContext.getUserId();
        List<String> chunks = documentChunkMapper.findContentsByDocumentId(id);
        if (chunks.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR);
        }
        // A failed batch may still have changed vectors; invalidate on both paths.
        answerCache.evictDocument(userId, id);
        uploadState.beginRebuild(id, userId);
        try {
            aiClient.deleteVectors(id);
            aiClient.upsertVectors(id, chunks);
            answerCache.evictDocument(userId, id);
            uploadState.indexed(id, userId);
        } catch (RuntimeException failure) {
            try { uploadState.indexFailed(id, userId); }
            catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            try { aiClient.deleteVectors(id); }
            catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            try {
                answerCache.evictDocument(userId, id);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
            throw failure;
        }
    }
    private String uploadFingerprint(MultipartFile file) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update(file.getOriginalFilename().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            try (var input = new java.security.DigestInputStream(file.getInputStream(), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.UPLOAD_FAILED);
        } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
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
