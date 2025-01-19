#!/bin/bash

# 脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

PID_FILE="application.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "Application is not running (PID file not found)"
    exit 0
fi

PID=$(cat "$PID_FILE")

if ! ps -p "$PID" > /dev/null; then
    echo "Application is not running (PID: $PID not found)"
    rm "$PID_FILE"
    exit 0
fi

echo "Stopping application (PID: $PID)..."
kill "$PID"

# 等待进程结束
TIMEOUT=30
while ps -p "$PID" > /dev/null && [ "$TIMEOUT" -gt 0 ]; do
    sleep 1
    TIMEOUT=$((TIMEOUT-1))
done

if ps -p "$PID" > /dev/null; then
    echo "Force killing application..."
    kill -9 "$PID"
fi

rm "$PID_FILE"
echo "Application stopped" 