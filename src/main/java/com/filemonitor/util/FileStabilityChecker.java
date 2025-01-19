package com.filemonitor.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.TimeUnit;

/**
 * 文件稳定性检查器
 * 用于确保大文件在传输前已经完全写入完成
 */
public class FileStabilityChecker {
    private static final Logger log = LoggerFactory.getLogger(FileStabilityChecker.class);
    private static final long CHECK_INTERVAL = 1000; // 1秒
    private static final int MAX_RETRIES = 30;       // 最多等待30次
    private static final long MIN_STABLE_TIME = 3000; // 文件需要保持3秒稳定

    /**
     * 检查文件是否稳定（不再被写入）
     * @param file 要检查的文件
     * @return 如果文件稳定返回true，否则返回false
     */
    public static boolean isFileStable(File file) {
        if (!isFileWritable(file)) {
            log.debug("File {} is locked or not writable", file.getPath());
            return false;
        }

        try {
            long lastSize = -1;
            long lastModified = -1;
            int stableCount = 0;
            int retryCount = 0;

            while (retryCount < MAX_RETRIES) {
                long currentSize = file.length();
                long currentModified = file.lastModified();

                if (currentSize == lastSize && currentModified == lastModified) {
                    stableCount++;
                    if (stableCount * CHECK_INTERVAL >= MIN_STABLE_TIME) {
                        log.debug("File {} is stable after {} checks", file.getPath(), stableCount);
                        return true;
                    }
                } else {
                    log.debug("File {} size or modification time changed, resetting stability counter", file.getPath());
                    stableCount = 0;
                }

                lastSize = currentSize;
                lastModified = currentModified;
                retryCount++;

                TimeUnit.MILLISECONDS.sleep(CHECK_INTERVAL);
            }

            log.warn("File {} not stable after {} retries", file.getPath(), MAX_RETRIES);
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while checking file stability: {}", file.getPath(), e);
            return false;
        }
    }

    /**
     * 检查文件是否可写（未被其他进程锁定）
     * @param file 要检查的文件
     * @return 如果文件可写返回true，否则返回false
     */
    private static boolean isFileWritable(File file) {
        if (!file.exists()) {
            log.debug("File {} does not exist", file.getPath());
            return false;
        }

        try (RandomAccessFile raf = new RandomAccessFile(file, "rw");
             FileChannel channel = raf.getChannel()) {
            
            FileLock lock = channel.tryLock();
            if (lock != null) {
                lock.release();
                return true;
            }
            return false;
        } catch (Exception e) {
            log.debug("File {} is locked or being written: {}", file.getPath(), e.getMessage());
            return false;
        }
    }
} 