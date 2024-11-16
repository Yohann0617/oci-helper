#!/bin/bash

# 设置目标目录
TARGET_DIR="/app/oci-helper"
KEYS_DIR="$TARGET_DIR/keys"

# 创建目录并进入
mkdir -p "$KEYS_DIR" && cd "$TARGET_DIR" || { echo "无法进入目录 $TARGET_DIR"; exit 1; }

# 公共下载URL前缀
BASE_URL="https://github.com/Yohann0617/oci-helper/releases/latest/download"

# 文件列表
FILES=("application.yml" "oci-helper.db" "docker-compose.yml")

# 下载文件
echo "下载文件..."
for file in "${FILES[@]}"; do
    curl -LO "$BASE_URL/$file" || { echo "下载 $file 失败"; exit 1; }
done

# 检查并安装 Docker
echo "检查 Docker..."
if ! command -v docker &> /dev/null; then
    echo "Docker 未安装，正在安装..."
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        if [[ "$ID" == "centos" || "$ID" == "rhel" ]]; then
            sudo yum install -y yum-utils
            sudo yum-config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
            sudo yum install -y docker-ce docker-ce-cli containerd.io
        elif [[ "$ID" == "debian" || "$ID" == "ubuntu" ]]; then
            sudo apt update
            sudo apt install -y docker.io
        elif [[ "$ID" == "alpine" ]]; then
            sudo apk add docker
        else
            echo "未识别的操作系统: $ID"
            exit 1
        fi
    else
        echo "无法识别操作系统，手动安装 Docker。"
        exit 1
    fi
    sudo systemctl start docker
    sudo systemctl enable docker
else
    echo "Docker 已安装。"
fi

# 检查并安装 Docker Compose
echo "检查 Docker Compose..."
if ! command -v docker-compose &> /dev/null; then
    echo "Docker Compose 未安装，正在安装..."
    if ! curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose; then
        echo "下载 Docker Compose 失败"
        exit 1
    fi
    chmod +x /usr/local/bin/docker-compose
else
    echo "Docker Compose 已安装。"
fi

# 启动服务
echo "启动 Docker Compose 服务..."
cd "$TARGET_DIR" || { echo "无法进入目录 $TARGET_DIR"; exit 1; }
docker compose up -d || { echo "启动失败"; exit 1; }

echo "脚本执行完成。"
