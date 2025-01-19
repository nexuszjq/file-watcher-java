package com.filemonitor.sftp;

import com.filemonitor.config.SftpConfig;
import com.jcraft.jsch.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;

public class SftpService {
    private static final Logger log = LoggerFactory.getLogger(SftpService.class);
    private final SftpConfig sftpConfig;
    
    public SftpService(SftpConfig sftpConfig) {
        this.sftpConfig = sftpConfig;
    }
    
    public void uploadFile(String localFilePath, String remoteFilePath) {
        Session session = null;
        ChannelSftp channelSftp = null;
        
        try {
            JSch jsch = new JSch();
            
            // 添加私钥
            if (sftpConfig.getPrivateKeyPassphrase() != null) {
                jsch.addIdentity(sftpConfig.getPrivateKeyPath(), sftpConfig.getPrivateKeyPassphrase());
            } else {
                jsch.addIdentity(sftpConfig.getPrivateKeyPath());
            }
            
            session = jsch.getSession(sftpConfig.getUsername(), sftpConfig.getHost(), sftpConfig.getPort());
            
            Properties config = new Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            
            session.connect();
            log.debug("SSH Session connected to {}:{}", sftpConfig.getHost(), sftpConfig.getPort());
            
            channelSftp = (ChannelSftp) session.openChannel("sftp");
            channelSftp.connect();
            
            // 创建远程目录（如果不存在）
            createRemoteDirectories(channelSftp, remoteFilePath);
            
            // 上传文件
            channelSftp.put(localFilePath, remoteFilePath);
            log.info("File uploaded successfully: {} -> {}", localFilePath, remoteFilePath);
            
        } catch (JSchException e) {
            log.error("SSH/SFTP connection error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to establish SFTP connection", e);
        } catch (SftpException e) {
            log.error("SFTP operation error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to perform SFTP operation", e);
        } finally {
            if (channelSftp != null && channelSftp.isConnected()) {
                channelSftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }
    
    private void createRemoteDirectories(ChannelSftp channelSftp, String remoteFilePath) {
        try {
            String[] dirs = remoteFilePath.substring(0, remoteFilePath.lastIndexOf('/')).split("/");
            String currentPath = "";
            for (String dir : dirs) {
                if (dir.isEmpty()) continue;
                currentPath += "/" + dir;
                try {
                    channelSftp.cd(currentPath);
                } catch (SftpException e) {
                    log.debug("Creating remote directory: {}", currentPath);
                    channelSftp.mkdir(currentPath);
                }
            }
        } catch (Exception e) {
            log.error("Error creating remote directories: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create remote directories", e);
        }
    }
} 