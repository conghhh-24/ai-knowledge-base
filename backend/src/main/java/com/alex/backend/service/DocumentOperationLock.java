package com.alex.backend.service;

import com.alex.backend.common.ErrorCode;
import com.alex.backend.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/** MySQL session lock shared by rebuild and delete, including across Java instances. */
@Component
public class DocumentOperationLock {
    private final DataSource dataSource;
    public DocumentOperationLock(DataSource dataSource) { this.dataSource = dataSource; }

    public Guard acquire(Long documentId) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            String name = "ai-kb:document:" + documentId;
            try (var statement = connection.prepareStatement("SELECT GET_LOCK(?, 0)")) {
                statement.setString(1, name);
                try (var result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1) {
                        connection.close();
                        throw new BusinessException(ErrorCode.DOCUMENT_BUSY);
                    }
                }
            }
            return new Guard(connection, name);
        } catch (SQLException ex) {
            if (connection != null) try { connection.close(); } catch (SQLException close) { ex.addSuppressed(close); }
            throw new IllegalStateException("无法获取文档操作锁", ex);
        }
    }

    public static class Guard implements AutoCloseable {
        private final Connection connection;
        private final String name;
        private boolean closed;
        Guard(Connection connection, String name) { this.connection = connection; this.name = name; }
        @Override
        public void close() {
            if (closed) return;
            closed = true;
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) { release(); }
                });
            } else release();
        }
        private void release() {
            try (var statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                statement.setString(1, name);
                statement.execute();
            } catch (SQLException ex) {
                // Never return a possibly locked physical connection to the pool.
                try { connection.abort(Runnable::run); } catch (SQLException abort) { ex.addSuppressed(abort); }
                org.slf4j.LoggerFactory.getLogger(DocumentOperationLock.class).error("释放文档锁失败", ex);
            } finally {
                try { connection.close(); } catch (SQLException ex) {
                    org.slf4j.LoggerFactory.getLogger(DocumentOperationLock.class).warn("关闭锁连接失败", ex);
                }
            }
        }
    }
}
