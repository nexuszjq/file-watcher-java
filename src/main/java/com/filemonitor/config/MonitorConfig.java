package com.filemonitor.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

public class MonitorConfig {
    private List<FileMapping> fileMappings;
    private String recordFile;
    private String mappingFile;
    private long pollingInterval;

    public List<FileMapping> getFileMappings() {
        return fileMappings;
    }

    public void setFileMappings(List<FileMapping> fileMappings) {
        this.fileMappings = fileMappings;
    }

    public String getRecordFile() {
        return recordFile;
    }

    public void setRecordFile(String recordFile) {
        this.recordFile = recordFile;
    }

    public String getMappingFile() {
        return mappingFile;
    }

    public void setMappingFile(String mappingFile) {
        this.mappingFile = mappingFile;
    }

    public long getPollingInterval() {
        return pollingInterval;
    }

    public void setPollingInterval(long pollingInterval) {
        this.pollingInterval = pollingInterval;
    }

    public static class FileMapping {
        private String sourcePath;
        private String targetPath;
        private String pattern;

        public String getSourcePath() {
            return sourcePath;
        }

        public void setSourcePath(String sourcePath) {
            this.sourcePath = sourcePath;
        }

        public String getTargetPath() {
            return targetPath;
        }

        public void setTargetPath(String targetPath) {
            this.targetPath = targetPath;
        }

        public String getPattern() {
            return pattern;
        }

        public void setPattern(String pattern) {
            this.pattern = pattern;
        }

        public static FileMapping fromLine(String line) {
            String[] parts = line.trim().split("\\|");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid mapping line format: " + line);
            }
            FileMapping mapping = new FileMapping();
            mapping.setPattern(parts[0].trim());
            mapping.setSourcePath(parts[1].trim());
            mapping.setTargetPath(parts[2].trim());
            return mapping;
        }
    }

    public void loadFileMappings() throws IOException {
        if (mappingFile == null || mappingFile.isEmpty()) {
            throw new IllegalStateException("Mapping file path not configured");
        }

        List<String> lines = Files.readAllLines(Paths.get(mappingFile));
        fileMappings = lines.stream()
            .filter(line -> !line.trim().isEmpty() && !line.startsWith("#"))
            .map(FileMapping::fromLine)
            .collect(Collectors.toList());
    }
} 