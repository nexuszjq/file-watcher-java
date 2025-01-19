#!/bin/bash

# 脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# 清理和打包
echo "Building application..."
mvn clean package

# 检查打包结果
JAR_NAME="target/file-monitor-sftp-1.0-SNAPSHOT-jar-with-dependencies.jar"
if [ ! -f "$JAR_NAME" ]; then
    echo "Error: Build failed!"
    exit 1
fi

# 停止现有应用
if [ -f "stop.sh" ]; then
    echo "Stopping existing application..."
    ./stop.sh
fi

# 复制新的jar文件
echo "Deploying new version..."
cp "$JAR_NAME" .

# 设置执行权限
chmod +x start.sh stop.sh status.sh

echo "Deployment completed successfully"
echo "Use './start.sh' to start the application" 