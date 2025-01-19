package com.filemonitor;

import com.filemonitor.monitor.FileMonitorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class FileMonitorApplication {
    private static final Logger log = LoggerFactory.getLogger(FileMonitorApplication.class);

    public static void main(String[] args) {
        try {
            // 加载Spring上下文
            ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
            context.registerShutdownHook();

            // 获取监控服务
            FileMonitorService monitorService = context.getBean(FileMonitorService.class);

            // 启动监控
            monitorService.startMonitoring();
            log.info("File monitor application started successfully");
        } catch (Exception e) {
            log.error("Error starting application: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
} 