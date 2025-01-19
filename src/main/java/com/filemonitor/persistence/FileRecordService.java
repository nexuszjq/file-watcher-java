package com.filemonitor.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class FileRecordService {
    private static final Logger log = LoggerFactory.getLogger(FileRecordService.class);
    
    private final String recordFile;
    private final Set<FileRecord> processedFiles;
    private final ObjectMapper objectMapper;

    public static class FileRecord {
        private String filePath;
        private String fileHash;
        private long lastModified;
        private long fileSize;
        private long processTime;

        public FileRecord() {}

        public FileRecord(String filePath, String fileHash, long lastModified, long fileSize) {
            this.filePath = filePath;
            this.fileHash = fileHash;
            this.lastModified = lastModified;
            this.fileSize = fileSize;
            this.processTime = System.currentTimeMillis();
        }

        public String getFilePath() {
            return filePath;
        }

        public void setFilePath(String filePath) {
            this.filePath = filePath;
        }

        public String getFileHash() {
            return fileHash;
        }

        public void setFileHash(String fileHash) {
            this.fileHash = fileHash;
        }

        public long getLastModified() {
            return lastModified;
        }

        public void setLastModified(long lastModified) {
            this.lastModified = lastModified;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }

        public long getProcessTime() {
            return processTime;
        }

        public void setProcessTime(long processTime) {
            this.processTime = processTime;
        }
    }

    public FileRecordService(String recordFile) {
        this.recordFile = recordFile;
        this.processedFiles = ConcurrentHashMap.newKeySet();
        this.objectMapper = new ObjectMapper();
        loadRecords();
    }

    private void loadRecords() {
        try {
            File file = new File(recordFile);
            if (file.exists()) {
                Set<FileRecord> records = objectMapper.readValue(
                    file,
                    new TypeReference<Set<FileRecord>>() {}
                );
                processedFiles.addAll(records);
                log.info("Loaded {} processed file records", records.size());
            }
        } catch (IOException e) {
            log.error("Error loading file records: {}", e.getMessage(), e);
        }
    }

    public void saveRecords() {
        try {
            objectMapper.writeValue(new File(recordFile), processedFiles);
            log.info("Saved {} processed file records", processedFiles.size());
        } catch (IOException e) {
            log.error("Error saving file records: {}", e.getMessage(), e);
        }
    }

    public boolean isFileProcessed(File file) {
        try {
            String fileHash = calculateFileHash(file);
            long fileSize = file.length();
            
            return processedFiles.stream().anyMatch(record -> 
                record.getFileHash().equals(fileHash) && 
                record.getFileSize() == fileSize &&
                record.getLastModified() == file.lastModified()
            );
        } catch (IOException e) {
            log.error("Error checking file process status: {}", e.getMessage(), e);
            return false;
        }
    }

    public void addProcessedFile(File file) {
        try {
            String fileHash = calculateFileHash(file);
            processedFiles.add(new FileRecord(
                file.getAbsolutePath(),
                fileHash,
                file.lastModified(),
                file.length()
            ));
            
            // 定期保存并清理旧记录
            if (processedFiles.size() % 100 == 0) {
                cleanupOldRecords();
                saveRecords();
            }
        } catch (IOException e) {
            log.error("Error adding processed file record: {}", e.getMessage(), e);
        }
    }

    private String calculateFileHash(File file) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            byte[] hash = digest.digest();
            
            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IOException("Error calculating file hash", e);
        }
    }

    private void cleanupOldRecords() {
        long thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000);
        processedFiles.removeIf(record -> record.getProcessTime() < thirtyDaysAgo);
    }
} 