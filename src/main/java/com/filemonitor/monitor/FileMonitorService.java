package com.filemonitor.monitor;

import com.filemonitor.config.MonitorConfig;
import com.filemonitor.sftp.SftpService;
import com.filemonitor.persistence.FileRecordService;
import com.filemonitor.util.FileStabilityChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.io.monitor.FileAlterationListener;
import org.apache.commons.io.monitor.FileAlterationMonitor;
import org.apache.commons.io.monitor.FileAlterationObserver;

import java.io.File;
import java.util.concurrent.*;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 文件监控服务
 * 负责监控指定目录下的文件变化，并通过SFTP传输到远程服务器
 */
public class FileMonitorService {
    private static final Logger log = LoggerFactory.getLogger(FileMonitorService.class);

    private static final long LARGE_FILE_THRESHOLD = 100 * 1024 * 1024; // 100MB
    private final MonitorConfig monitorConfig;
    private final SftpService sftpService;
    private final FileRecordService fileRecordService;
    private final ExecutorService executorService;
    // 用于跟踪正在处理的文件任务
    private final Map<String, Future<?>> pendingTasks = new ConcurrentHashMap<>();
    private final long pollingInterval;

    public FileMonitorService(MonitorConfig monitorConfig, SftpService sftpService, FileRecordService fileRecordService) {
        this.monitorConfig = monitorConfig;
        this.sftpService = sftpService;
        this.fileRecordService = fileRecordService;
        this.pollingInterval = monitorConfig.getPollingInterval();
        
        // 使用固定的线程池配置
        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "file-processor-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        };

        this.executorService = new ThreadPoolExecutor(
            4, 8, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            threadFactory,
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 启动文件监控
     * 为每个配置的目录创建观察者并开始监控
     */
    public void startMonitoring() {
        try {
            FileAlterationMonitor monitor = new FileAlterationMonitor(pollingInterval);

            for (MonitorConfig.FileMapping mapping : monitorConfig.getFileMappings()) {
                File directory = new File(mapping.getSourcePath());
                if (!directory.exists() || !directory.isDirectory()) {
                    log.error("Directory not found or not a directory: {}", mapping.getSourcePath());
                    continue;
                }

                // 创建文件观察者，使用文件名模式过滤器
                FileAlterationObserver observer = new FileAlterationObserver(
                    directory,
                    pathname -> pathname.getName().matches(mapping.getPattern())
                );

                observer.addListener(createFileListener(mapping));
                monitor.addObserver(observer);
                log.info("Monitoring directory: {}", mapping.getSourcePath());
            }

            monitor.start();
            log.info("File monitoring started with {} file mappings", monitorConfig.getFileMappings().size());
        } catch (Exception e) {
            log.error("Error starting file monitor: {}", e.getMessage(), e);
        }
    }

    /**
     * 创建文件变化监听器
     */
    private FileAlterationListener createFileListener(MonitorConfig.FileMapping mapping) {
        return new FileAlterationListener() {
            @Override
            public void onFileChange(File file) {
                submitFileProcessing(file, mapping);
            }

            @Override
            public void onFileCreate(File file) {
                submitFileProcessing(file, mapping);
            }

            // 其他方法默认空实现
            @Override public void onStart(FileAlterationObserver observer) {}
            @Override public void onDirectoryCreate(File directory) {}
            @Override public void onDirectoryChange(File directory) {}
            @Override public void onDirectoryDelete(File directory) {}
            @Override public void onFileDelete(File file) {}
            @Override public void onStop(FileAlterationObserver observer) {}
        };
    }

    /**
     * 提交文件处理任务到线程池
     */
    private void submitFileProcessing(File file, MonitorConfig.FileMapping mapping) {
        String fileKey = file.getAbsolutePath();
        
        // 如果文件正在处理中，取消之前的任务
        Future<?> existingTask = pendingTasks.remove(fileKey);
        if (existingTask != null && !existingTask.isDone()) {
            existingTask.cancel(true);
            log.debug("Cancelled existing task for file: {}", fileKey);
        }

        // 提交新的处理任务
        Future<?> future = executorService.submit(() -> handleFileChange(file, mapping));
        pendingTasks.put(fileKey, future);
    }

    /**
     * 处理文件变化
     * 包括文件稳定性检查、SFTP传输和记录保存
     */
    private void handleFileChange(File file, MonitorConfig.FileMapping mapping) {
        try {
            String filePath = file.getAbsolutePath();

            // 避免重复处理
            if (fileRecordService.isFileProcessed(file)) {
                log.debug("File already processed: {}", filePath);
                return;
            }

            // 对大文件进行稳定性检查
            if (file.length() > LARGE_FILE_THRESHOLD) {
                if (!FileStabilityChecker.isFileStable(file)) {
                    log.debug("Large file {} is not stable yet, will retry later", filePath);
                    return;
                }
            }

            // 构建目标路径
            String relativePath = file.getAbsolutePath().substring(mapping.getSourcePath().length());
            String targetPath = mapping.getTargetPath() + relativePath;

            // 上传文件
            sftpService.uploadFile(filePath, targetPath);
            log.info("Successfully uploaded file: {} -> {}", filePath, targetPath);
            
            // 记录已处理的文件
            fileRecordService.addProcessedFile(file);
        } catch (Exception e) {
            log.error("Error processing file {}: {}", file.getPath(), e.getMessage(), e);
        } finally {
            pendingTasks.remove(file.getAbsolutePath());
        }
    }

    /**
     * 关闭服务，确保资源正确释放
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
} 