#!/bin/bash

# 脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# 基础配置
LOG_DIR="logs"
JAR_NAME="file-monitor-sftp-1.0-SNAPSHOT-jar-with-dependencies.jar"
MAPPING_FILE="file_mappings.txt"
RECORD_FILE="./processed_files.json"

# SFTP配置
SFTP_HOST="remote-server.com"
SFTP_PORT="22"
SFTP_USERNAME="your-username"
SFTP_PRIVATE_KEY="/path/to/.ssh/id_rsa"
SFTP_KEY_PASSPHRASE=""  # 如果私钥有密码，在这里设置

# 监控配置
POLLING_INTERVAL="5000"

# 检查必要文件
if [ ! -f "$MAPPING_FILE" ]; then
    echo "Error: Mapping file $MAPPING_FILE not found!"
    exit 1
fi

if [ ! -f "$JAR_NAME" ]; then
    echo "Error: JAR file $JAR_NAME not found!"
    exit 1
fi

# 创建日志目录
mkdir -p "$LOG_DIR"

# JVM参数
JAVA_OPTS="-Xms256m -Xmx512m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=$LOG_DIR"

# 系统属性参数
SYSTEM_PROPS="\
    -Dsftp.host=$SFTP_HOST \
    -Dsftp.port=$SFTP_PORT \
    -Dsftp.username=$SFTP_USERNAME \
    -Dsftp.privateKeyPath=$SFTP_PRIVATE_KEY \
    -Dsftp.privateKeyPassphrase=$SFTP_KEY_PASSPHRASE \
    -Dmonitor.recordFile=$RECORD_FILE \
    -Dmonitor.mappingFile=$MAPPING_FILE \
    -Dmonitor.pollingInterval=$POLLING_INTERVAL"

# 检查是否已经运行
PID_FILE="application.pid"
if [ -f "$PID_FILE" ]; then
    PID=$(cat "$PID_FILE")
    if ps -p "$PID" > /dev/null; then
        echo "Application is already running with PID: $PID"
        exit 1
    else
        rm "$PID_FILE"
    fi
fi

# 启动应用
echo "Starting File Monitor Application..."
nohup java $JAVA_OPTS $SYSTEM_PROPS -jar "$JAR_NAME" > "$LOG_DIR/application.log" 2>&1 &

# 保存PID
echo $! > "$PID_FILE"
echo "Application started with PID: $!"
echo "Logs are available at: $LOG_DIR/application.log" 