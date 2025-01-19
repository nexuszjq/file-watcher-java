#!/bin/bash

# 脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

PID_FILE="application.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "Application is not running (PID file not found)"
    exit 1
fi

PID=$(cat "$PID_FILE")

if ps -p "$PID" > /dev/null; then
    echo "Application is running (PID: $PID)"
    echo "Memory usage:"
    ps -o pid,ppid,%cpu,%mem,rss,command -p "$PID"
    exit 0
else
    echo "Application is not running (PID: $PID not found)"
    rm "$PID_FILE"
    exit 1
fi 