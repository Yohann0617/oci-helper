#!/bin/bash

clear

# ======================
# 参数解析
# ======================
DEV_MODE=false
DEV_OCI_HELPER_IMAGE="oci-helper:dev"

while [[ $# -gt 0 ]]; do
    case "$1" in
        --dev)
            DEV_MODE=true
            # 可选指定自定义镜像: --dev 或 --dev my-image:tag
            if [[ -n "$2" && "$2" != -* ]]; then
                DEV_OCI_HELPER_IMAGE="$2"
                shift
            fi
            shift
            ;;
        *)
            shift
            ;;
    esac
done

# 定义颜色
YELLOW='\033[33m'
GREEN='\033[32m'
RED='\033[31m'
BLUE='\033[34m'
RESET='\033[0m'

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 设置目标目录为脚本所在目录下的 oci-helper 文件夹
TARGET_DIR="$SCRIPT_DIR/oci-helper"
KEYS_DIR="$TARGET_DIR/keys"

DOCKER_COMPOSE_CMD=""

# ======================
# Docker 环境检查函数
# ======================
check_docker_daemon() {
    if ! docker info > /dev/null 2>&1; then
        return 1
    fi
    return 0
}

# 检测可用的 Docker Compose 命令
detect_compose_cmd() {
    # 优先检测插件版 docker compose（现代 Docker 推荐方式）
    if docker compose version &> /dev/null 2>&1; then
        DOCKER_COMPOSE_CMD="docker compose"
        return 0
    fi

    # 回退检测独立版 docker-compose（兼容旧版本）
    if command -v docker-compose &> /dev/null; then
        DOCKER_COMPOSE_CMD="docker-compose"
        return 0
    fi

    return 1
}

# ======================
# 卸载逻辑
# ======================
uninstall() {
    echo "🛑 开始卸载 oci-helper ..."

    # 停止并删除容器
    echo "🔍 停止并删除相关容器..."
    for name in "oci-helper-watcher" "websockify" "oci-helper"; do
        if docker ps -a --filter "name=$name" -q | grep -q .; then
            docker rm -f "$name"
            echo "✅ 已删除容器 $name"
        else
            echo "ℹ️ 未找到容器 $name"
        fi
    done

    # 删除相关镜像
    echo "🧹 删除相关镜像..."
    docker images --format "{{.Repository}}:{{.Tag}} {{.ID}}" | grep "oci-helper" | awk '{print $2}' | sort -u | xargs -r docker rmi -f
    echo "✅ 镜像清理完成"

    # 询问是否删除目录
    read -p "是否清空所有数据并删除 $TARGET_DIR 目录？(y/N): " DEL_DIR
    if [[ "$DEL_DIR" =~ ^[Yy]$ ]]; then
        rm -rf "$TARGET_DIR"
        echo "✅ 已删除目录 $TARGET_DIR"
    else
        echo "ℹ️ 保留目录 $TARGET_DIR"
    fi

    echo "😢 oci-helper 卸载完成~"
    exit 0
}

# ======================
# 部署/更新逻辑
# ======================
deploy() {
    # 创建目录并进入
    mkdir -p "$KEYS_DIR" && cd "$TARGET_DIR" || { echo "❌ 无法进入目录：$TARGET_DIR，请检查权限或路径是否正确。"; exit 1; }

    # 创建或清空版本更新触发标志文件
    rm -rf update_version_trigger.flag
    : > update_version_trigger.flag

    # 公共下载URL前缀
    BASE_URL="https://github.com/Yohann0617/oci-helper/releases/download/deploy"

    # 文件列表
    FILES=("application.yml" "oci-helper.db" "docker-compose.yml")

    # 下载文件
    echo "🔍 检查所需文件..."
    COMPOSE_DOWNLOADED=false
    for file in "${FILES[@]}"; do
        if [[ -f "$TARGET_DIR/$file" ]]; then
            echo "✔ 文件 '$file' 已存在，跳过下载。"
        else
            echo "⬇️ 正在下载 '$file' ..."
            curl -LO "$BASE_URL/$file" || { echo "❌ 下载文件 '$file' 失败，请检查网络连接或 URL。"; exit 1; }
            # 标记 docker-compose.yml 是本次新下载的
            [[ "$file" == "docker-compose.yml" ]] && COMPOSE_DOWNLOADED=true
        fi
    done

    COMPOSE_FILE="$TARGET_DIR/docker-compose.yml"

    # --dev 模式：docker-compose.yml 是新下载的，替换 oci-helper 主镜像为开发镜像
    if [[ "$DEV_MODE" == true && "$COMPOSE_DOWNLOADED" == true ]]; then
        echo "🔧 [DEV模式] 替换 oci-helper 镜像为开发镜像: $DEV_OCI_HELPER_IMAGE"
        sed -i "s|ghcr.io/yohann0617/oci-helper:.*|$DEV_OCI_HELPER_IMAGE|" "$COMPOSE_FILE"
        echo "✅ 开发镜像替换完成"
    fi

    # 检查并移除 /usr/bin/docker 挂载
    COMPOSE_FILE="$TARGET_DIR/docker-compose.yml"
    BAD_MOUNT="/usr/bin/docker:/usr/bin/docker"

    if grep -- "$BAD_MOUNT" "$COMPOSE_FILE" > /dev/null 2>&1; then
        echo "⚠️ 检测到 docker-compose.yml 中存在不兼容挂载 '$BAD_MOUNT'，正在移除..."
        sed -i "\|$BAD_MOUNT|d" "$COMPOSE_FILE" || {
            echo "❌ 移除挂载失败，请手动检查 docker-compose.yml 文件。"
            exit 1
        }
        echo "✅ 不兼容挂载已移除。"
    fi

    # 自动替换 docker-compose.yml 中的宿主机路径
    # 只替换 volumes 挂载行中冒号左侧的宿主机路径，容器内路径保持不变
    OLD_HOST_PATH="/app/oci-helper"
    NEW_HOST_PATH="$TARGET_DIR"
    
    if grep -q "$OLD_HOST_PATH" "$COMPOSE_FILE"; then
        echo "🔄 替换 docker-compose.yml 中的宿主机路径..."
        echo "   原路径: $OLD_HOST_PATH"
        echo "   新路径: $NEW_HOST_PATH"
        # volumes 行的格式: "  - /host/path:/container/path"
        # 只替换每行第一个匹配的路径（即宿主机路径，在冒号左边）
        # 使用 sed 地址匹配：对包含 volumes 挂载路径的行，只替换第一个出现的 /app/oci-helper
        sed -i "s|\(- \)\?${OLD_HOST_PATH}/|\1${NEW_HOST_PATH}/|" "$COMPOSE_FILE"
        
        if grep -q "$OLD_HOST_PATH" "$COMPOSE_FILE"; then
            echo "⚠️ 部分路径替换失败，请手动检查 $COMPOSE_FILE"
        else
            echo "✅ 宿主机路径替换完成"
        fi
    fi

    # 检查并安装 Docker
    echo "🔍 检查 Docker 安装状态..."
    if ! command -v docker &> /dev/null; then
        echo "⚠️ Docker 未安装，开始安装中..."
        curl -fsSL https://get.docker.com | sh || {
            echo "❌ Docker 安装失败，请检查网络或手动安装。"
            exit 1
        }
        
        systemctl start docker
        systemctl enable docker
        echo "✅ Docker 安装并启动完成。"
    else
        DOCKER_VERSION=$(docker --version 2>/dev/null | awk '{print $3}' | cut -d',' -f1)
        echo "✅ Docker 已安装，版本: $DOCKER_VERSION"
    fi

    # 检查 Docker daemon 状态
    echo "🔍 检查 Docker daemon 运行状态..."
    if ! check_docker_daemon; then
        echo "⚠️ Docker daemon 未运行或无权限访问，尝试启动..."
        if command -v systemctl &> /dev/null; then
            systemctl start docker 2>/dev/null || true
            sleep 2
        fi
        
        if ! check_docker_daemon; then
            echo "❌ 无法连接到 Docker daemon。"
            echo "💡 可能的原因和解决方案："
            echo "   1) Docker 服务未启动: 运行 'systemctl start docker'"
            echo "   2) 当前用户无权限: 运行 'sudo usermod -aG docker $USER' 后重新登录"
            echo "   3) 使用 sudo 运行本脚本"
            exit 1
        fi
    fi
    echo "✅ Docker daemon 运行正常。"

    # 检查 Docker Compose 可用性
    echo "🔍 检查 Docker Compose 安装状态..."
    if detect_compose_cmd; then
        if [[ "$DOCKER_COMPOSE_CMD" == "docker compose" ]]; then
            COMPOSE_VERSION=$(docker compose version 2>/dev/null | awk '{print $4}' | cut -d',' -f1)
            echo "✅ Docker Compose (插件版) 已安装，版本: $COMPOSE_VERSION"
        else
            COMPOSE_VERSION=$(docker-compose --version 2>/dev/null | awk '{print $3}' | cut -d',' -f1)
            echo "⚠️ Docker Compose (独立版) 已安装，版本: $COMPOSE_VERSION，建议升级为插件版"
        fi
    else
        echo "⚠️ Docker Compose 未安装，开始安装..."
        echo "🔧 优先安装 Docker Compose 插件版 (docker compose)..."
        
        if [ -f /etc/os-release ]; then
            . /etc/os-release
            case "$ID" in
                ubuntu|debian|kali|linuxmint|pop|neon)
                    apt update && apt install -y docker-compose-plugin 2>/dev/null || {
                        echo "⚠️ 插件版安装失败，回退安装独立版 docker-compose..."
                        curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
                        chmod +x /usr/local/bin/docker-compose
                    }
                    ;;
                centos|rhel|rocky|almalinux|ol|ancient)
                    [ -x "$(command -v dnf)" ] && dnf install -y docker-compose-plugin 2>/dev/null || yum install -y docker-compose-plugin 2>/dev/null || {
                        echo "⚠️ 插件版安装失败，回退安装独立版 docker-compose..."
                        curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
                        chmod +x /usr/local/bin/docker-compose
                    }
                    ;;
                alpine)
                    apk add --no-cache docker-cli-compose 2>/dev/null || {
                        echo "⚠️ 插件版安装失败，回退安装独立版 docker-compose..."
                        curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
                        chmod +x /usr/local/bin/docker-compose
                    }
                    ;;
                *)
                    echo "⚠️ 无法确定发行版，回退安装独立版 docker-compose..."
                    curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
                    chmod +x /usr/local/bin/docker-compose
                    ;;
            esac
        else
            echo "⚠️ 无法识别系统，回退安装独立版 docker-compose..."
            curl -L "https://github.com/docker/compose/releases/latest/download/docker-compose-$(uname -s)-$(uname -m)" -o /usr/local/bin/docker-compose
            chmod +x /usr/local/bin/docker-compose
        fi

        # 重新检测 Compose 命令
        if detect_compose_cmd; then
            if [[ "$DOCKER_COMPOSE_CMD" == "docker compose" ]]; then
                echo "✅ Docker Compose (插件版) 安装完成"
            else
                echo "⚠️ Docker Compose (独立版) 安装完成，建议升级为插件版"
            fi
        else
            echo "❌ Docker Compose 安装失败，请手动安装。"
            exit 1
        fi
    fi

    # 根据使用的 Compose 命令调整 docker-compose 挂载
    DOCKER_COMPOSE_MOUNT="/usr/local/bin/docker-compose:/usr/local/bin/docker-compose"
    
    if [[ "$DOCKER_COMPOSE_CMD" == "docker compose" ]]; then
        # 插件版不需要挂载独立版 docker-compose，移除该挂载
        if grep -- "$DOCKER_COMPOSE_MOUNT" "$COMPOSE_FILE" > /dev/null 2>&1; then
            echo "🔧 使用 Docker Compose 插件版，移除独立版 docker-compose 挂载..."
            sed -i "\|$DOCKER_COMPOSE_MOUNT|d" "$COMPOSE_FILE"
            echo "✅ 挂载已移除"
        fi
    else
        # 独立版需要确保挂载正确的 docker-compose 路径
        DOCKER_COMPOSE_BIN=$(command -v docker-compose)
        if [[ -n "$DOCKER_COMPOSE_BIN" && "$DOCKER_COMPOSE_BIN" != "/usr/local/bin/docker-compose" ]]; then
            echo "🔄 替换 docker-compose 挂载路径..."
            echo "   原路径: /usr/local/bin/docker-compose"
            echo "   新路径: $DOCKER_COMPOSE_BIN"
            sed -i "s|/usr/local/bin/docker-compose:/usr/local/bin/docker-compose|$DOCKER_COMPOSE_BIN:/usr/local/bin/docker-compose|g" "$COMPOSE_FILE"
            echo "✅ docker-compose 挂载路径替换完成"
        fi
    fi

    # 删除旧的容器和镜像
    clean_container() {
        local name="$1"
        local image_prefix="$2"

        echo "🔍 检查名为 '$name' 的运行中容器..."
        if docker ps --filter "name=$name" -q | grep -q .; then
            echo "🛑 发现运行中的容器 '$name'，正在删除..."
            docker rm -f "$name" || { echo "❌ 停止容器 '$name' 失败"; exit 1; }
            echo "🧹 删除 '$name' 相关旧镜像..."
            docker images --format "{{.Repository}}:{{.Tag}} {{.ID}}" | grep "$image_prefix" | awk '{print $2}' | sort -u | xargs -r docker rmi -f || { echo "❌ 删除镜像失败"; exit 1; }
            echo "✅ 容器和镜像已清理。"
        else
            echo "ℹ️ 没有运行中的容器 '$name'。"
        fi
    }

    clean_container "oci-helper-watcher" "oci-helper-watcher"
    clean_container "websockify" "oci-helper-websockify"
    clean_container "oci-helper" "oci-helper"

    # 检查并安装 SQLite
    echo "🔍 检查 SQLite 安装状态..."
    if ! command -v sqlite3 &> /dev/null; then
        if [ -f /etc/os-release ]; then
            . /etc/os-release
            echo "🖥️ 检测到系统: $ID"
            
            case "$ID" in
                ubuntu|debian|kali|linuxmint|pop|neon)
                    apt update && apt install -y sqlite3 ;;
                centos|rhel|rocky|almalinux|ol|ancient)
                    [ -x "$(command -v dnf)" ] && dnf install -y sqlite || yum install -y sqlite ;;
                fedora|korora)
                    dnf install -y sqlite ;;
                alpine)
                    apk add --no-cache sqlite ;;
                arch|manjaro|endeavouros)
                    pacman -Sy --noconfirm sqlite ;;
                opensuse*|sled|leap|tumbleweed)
                    zypper install -y sqlite3 ;;
                gentoo)
                    emerge --ask n dev-db/sqlite ;;
                slackware)
                    slackpkg install sqlite ;;
                void)
                    xbps-install -S sqlite ;;
                nixos)
                    echo "ℹ️ NixOS 请使用 nix-env -i sqlite" ;;
                *)
                    # 处理衍生发行版
                    case "$ID_LIKE" in
                        *debian*)
                            apt update && apt install -y sqlite3 ;;
                        *rhel*|*fedora*)
                            [ -x "$(command -v dnf)" ] && dnf install -y sqlite || yum install -y sqlite ;;
                        *)
                            echo "❌ 未直接支持的发行版: $ID"
                            echo "💡 尝试手动安装:"
                            echo "Debian系: apt install sqlite3"
                            echo "RHEL系: yum/dnf install sqlite"
                            echo "Arch系: pacman -S sqlite"
                            exit 1
                            ;;
                    esac
                    ;;
            esac
        else
            echo "❌ 无法识别系统，请手动安装SQLite:"
            echo "📚 参考: https://sqlite.org/download.html"
            exit 1
        fi

        # 验证安装
        if sqlite3 --version &> /dev/null; then
            echo "✅ SQLite 安装成功，版本: $(sqlite3 --version)"
        else
            echo "❌ SQLite 安装失败，请检查报错信息"
            exit 1
        fi
    else
        echo "✅ SQLite 已安装。"
    fi

    # 获取 GitHub 项目最新 release tag
    echo "🌐 获取最新发布版本号..."
    LATEST_TAG=$(curl -s https://api.github.com/repos/Yohann0617/oci-helper/releases/latest | grep '"tag_name":' | awk -F '"' '{print $4}')
    if [[ -z "$LATEST_TAG" ]]; then
        echo "❌ 无法获取最新的发布版本号，请检查网络连接。"
        exit 1
    fi
    echo "🏷 最新发布版本：$LATEST_TAG"

    # 更新 SQLite 数据库
    echo "🗃 更新本地数据库版本号记录..."
    DB_FILE="$TARGET_DIR/oci-helper.db"
    if [[ -f "$DB_FILE" ]]; then
        RECORD_EXISTS=$(sqlite3 "$DB_FILE" "SELECT COUNT(*) FROM oci_kv WHERE code = 'Y106' AND type = 'Y003';")
        if [[ "$RECORD_EXISTS" -gt 0 ]]; then
sqlite3 "$DB_FILE" <<EOF
    UPDATE oci_kv SET value = '$LATEST_TAG' WHERE code = 'Y106' AND type = 'Y003';
EOF
            echo "✅ 数据库版本号更新成功。"
        else
            echo "⚠️ 数据库中未找到匹配记录，未进行更新。"
        fi
    else
        echo "❌ 数据库文件 $DB_FILE 不存在，无法更新版本号。"
        exit 1
    fi

    # ======================
    # 设置账号和密码
    # ======================
    APP_YML="$TARGET_DIR/application.yml"

    echo -e "${RESET}"
    echo -e "${YELLOW}请选择账号密码设置方式：${RESET}"
    echo "1) 自动生成随机账号和密码"
    echo "2) 手动输入账号和密码"
    echo "3) 保留当前账号和密码，不作修改"
    read -p "输入选项 (1/2/3): " ACC_MODE

    if [[ "$ACC_MODE" == "1" ]]; then
        NEW_ACC="user_$(tr -dc 'a-z0-9' </dev/urandom | head -c 6)"
        NEW_PASS=$(tr -dc 'A-Za-z0-9' </dev/urandom | head -c 10)
        echo "✅ 已生成账号: $NEW_ACC"
        echo "✅ 已生成密码: $NEW_PASS"

        # 修改 application.yml
        sed -i "s/^.*account:.*/  account: $NEW_ACC/" "$APP_YML"
        sed -i "s/^.*password:.*/  password: $NEW_PASS/" "$APP_YML"

    elif [[ "$ACC_MODE" == "2" ]]; then
        read -p "请输入账号: " NEW_ACC
        read -p "请输入密码: " NEW_PASS
        if [[ -z "$NEW_ACC" || -z "$NEW_PASS" ]]; then
            echo "❌ 账号和密码不能为空"
            exit 1
        fi

        # 修改 application.yml
        sed -i "s/^.*account:.*/  account: $NEW_ACC/" "$APP_YML"
        sed -i "s/^.*password:.*/  password: $NEW_PASS/" "$APP_YML"

    elif [[ "$ACC_MODE" == "3" ]]; then
        # 从 application.yml 读取现有值
        NEW_ACC=$(grep "account:" "$APP_YML" | awk '{print $2}')
        NEW_PASS=$(grep "password:" "$APP_YML" | awk '{print $2}')
        echo "🔒 保留当前账号和密码"
        echo "👤 账号: $NEW_ACC"
        echo "🔑 密码: $NEW_PASS"
    else
        echo "❌ 输入无效，已退出"
        exit 1
    fi

    # 启动服务
    echo "🚀 启动 Docker Compose 服务... (使用: $DOCKER_COMPOSE_CMD)"
    cd "$TARGET_DIR" || { echo "❌ 无法进入目录：$TARGET_DIR"; exit 1; }
    $DOCKER_COMPOSE_CMD pull || { echo "❌ 拉取最新镜像失败，请检查网络或 Docker Hub 状态。"; exit 1; }
    $DOCKER_COMPOSE_CMD up -d || { echo "❌ 启动服务失败，请检查 Docker Compose 配置。"; exit 1; }

    echo -e "${RESET}"
    echo "🎉 oci-helper 部署完成！源码地址：https://github.com/Yohann0617/oci-helper"
    echo "🌐 默认访问地址：http://your_ip_address:8818"
    echo "👤 账号：$NEW_ACC"
    echo "🔑 密码：$NEW_PASS"
}

# ======================
# 重启逻辑
# ======================
restart() {
    echo "🔄 正在重启容器：oci-helper-watcher、oci-helper、websockify ..."
    docker restart oci-helper-watcher oci-helper websockify || {
        echo "❌ 容器重启失败，请检查是否已部署。"
        exit 1
    }
    echo "✅ 容器已成功重启"
}

# ======================
# 修改账号和密码
# ======================
change_account_password() {
    APP_YML="$TARGET_DIR/application.yml"

    if [[ ! -f "$APP_YML" ]]; then
        echo "❌ 未找到 $APP_YML，请先部署 oci-helper。"
        exit 1
    fi

    read -p "请输入新的账号: " NEW_ACC
    read -p "请输入新的密码: " NEW_PASS

    if [[ -z "$NEW_ACC" || -z "$NEW_PASS" ]]; then
        echo "❌ 账号和密码不能为空"
        exit 1
    fi

    # 修改 application.yml
    if grep -q "account:" "$APP_YML"; then
        sed -i "s/^.*account:.*/  account: $NEW_ACC/" "$APP_YML"
    else
        echo "❌ 未找到 account 字段，请检查 $APP_YML"
        exit 1
    fi

    if grep -q "password:" "$APP_YML"; then
        sed -i "s/^.*password:.*/  password: $NEW_PASS/" "$APP_YML"
    else
        echo "❌ 未找到 password 字段，请检查 $APP_YML"
        exit 1
    fi

    echo "✅ 已修改账号: $NEW_ACC"
    echo "✅ 已修改密码: $NEW_PASS"

    # 重启服务使配置生效
    echo "🔄 正在重启服务..."
    docker restart oci-helper || {
        echo "⚠️ 修改成功，但容器重启失败，请手动执行 docker restart oci-helper"
        exit 1
    }
    echo "✅ 新账号和密码已生效"
}

# ======================
# 状态检查逻辑
# ======================
status() {
    echo "📊 当前容器状态："
    for name in "oci-helper-watcher" "oci-helper" "websockify"; do
        container_info=$(docker ps --filter "name=^/${name}$" --format "{{.Status}}")
        if [[ "$container_info" == Up* ]]; then
            echo "✅ $name 状态: $container_info"
        else
            echo "❌ $name 未运行"
        fi
    done
    echo

    # 显示账号密码
    APP_YML="$TARGET_DIR/application.yml"
    if [[ -f "$APP_YML" ]]; then
        acc=$(grep "account:" "$APP_YML" | awk '{print $2}')
        pass=$(grep "password:" "$APP_YML" | awk '{print $2}')
        echo "👤 当前账号: $acc"
        echo "🔑 当前密码: $pass"
        echo
    else
        echo "⚠️ 未找到 $APP_YML，无法显示账号密码"
        echo
    fi
}

# ======================
# 清理 oci-helper docker 日志
# ======================
clean_log() {
    for name in "oci-helper-watcher" "oci-helper" "websockify"; do
        cid=$(docker ps -a --filter "name=^/${name}$" --format "{{.ID}}")
        if [[ -n "$cid" ]]; then
            log_file=$(docker inspect --format='{{.LogPath}}' "$cid" 2>/dev/null)
            if [[ -n "$log_file" && -f "$log_file" ]]; then
                : > "$log_file"
                echo "🧹 已清理日志: $name -> $log_file"
            else
                echo "⚠️ 未找到日志文件: $name"
            fi
        else
            echo "❌ 未找到容器: $name"
        fi
    done
    echo "✅ oci-helper 服务日志已清空"
}

# ======================
# 欢迎语 & Banner
# ======================
echo -e "${GREEN}"
cat << "EOF"
 ,-----. ,-----.,--.       ,--.  ,--.,------.,--.   ,------. ,------.,------.  
'  .-.  '  .--./|  |,-----.|  '--'  ||  .---'|  |   |  .--. '|  .---'|  .--. ' 
|  | |  |  |    |  |'-----'|  .--.  ||  `--, |  |   |  '--' ||  `--, |  '--'.' 
'  '-'  '  '--'\|  |       |  |  |  ||  `---.|  '--.|  | --' |  `---.|  |\  \  
 `-----' `-----'`--'       `--'  `--'`------'`-----'`--'     `------'`--' '--' 
                                                                               
© 2024 Yohann. All Rights Reserved                                                    
EOF
echo -e "${RESET}"

echo -e "${YELLOW}欢迎使用 oci-helper 自动部署工具 🐢${RESET}"
echo -e "项目地址：${BLUE}https://github.com/Yohann0617/oci-helper${RESET}"
echo -e "系统推荐使用 Debian、Ubuntu、Centos、Alpine，其他系统暂未测试"
echo

# 显示容器状态
status

# ======================
# 菜单入口
# ======================
echo -e "${YELLOW}请选择操作：${RESET}"
printf "${GREEN}%-3s%-30s${RESET}\n" "1)" "🤖 部署/更新 oci-helper"
printf "%-3s%-30s\n" "2)" "🔄 重启 oci-helper"
printf "%-3s%-30s\n" "3)" "🔒 修改 oci-helper 网页账号和密码"
printf "%-3s%-30s\n" "4)" "🔍 查看 docker 容器服务资源占用情况"
printf "%-3s%-30s\n" "5)" "📄 查看 oci-helper 服务实时日志"
printf "%-3s%-30s\n" "6)" "🧹 清理 oci-helper 服务日志"
printf "${RED}%-3s%-30s${RESET}\n" "88)" "😢 卸载 oci-helper"
printf "%-3s%-30s\n" "0)" "⏪ 退出"

read -p "输入选项 (例如：1): " choice

case "$choice" in
    1)
        deploy
        ;;
    2)
        restart
        ;;
    3)
        change_account_password
        ;;
    4)
        docker stats
        ;;
    5)
        docker logs -f oci-helper
        ;;
    6)
        clean_log
        ;;
    88)
        uninstall
        ;;
    0)
        echo "👋 已退出脚本"
        exit 0
        ;;
    *)
        echo "❌ 无效的选项"
        exit 1
        ;;
esac