#!/bin/bash
# 苹果蓝牙耳机售后AI聊天机器人 - 服务器初始化脚本
# 适用于: Ubuntu 22.04 LTS
# 使用方法: sudo bash setup.sh

set -e  # 遇到错误立即退出

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查是否为root用户
check_root() {
    if [ "$EUID" -ne 0 ]; then
        print_error "请使用sudo运行此脚本"
        exit 1
    fi
}

# 配置变量
APP_NAME="after-sales-robot"
APP_DIR="/var/www/$APP_NAME"
APP_USER="www-data"
VENV_DIR="$APP_DIR/venv"
REPO_URL="https://github.com/your-username/pineapple-after-sales-robot.git"  # 替换为实际仓库地址

# 步骤1: 系统更新
step1_system_update() {
    print_info "步骤1/8: 系统更新..."
    apt update
    apt upgrade -y
    apt install -y curl wget git vim
    print_info "系统更新完成"
}

# 步骤2: 安装Python
step2_install_python() {
    print_info "步骤2/8: 安装Python 3.11..."
    apt install -y python3.11 python3.11-venv python3-pip

    # 验证安装
    python3.11 --version
    print_info "Python安装完成"
}

# 步骤3: 安装Nginx
step3_install_nginx() {
    print_info "步骤3/8: 安装Nginx..."
    apt install -y nginx
    systemctl enable nginx
    systemctl start nginx
    print_info "Nginx安装完成"
}

# 步骤4: 创建应用目录
step4_create_app_dir() {
    print_info "步骤4/8: 创建应用目录..."
    mkdir -p $APP_DIR
    mkdir -p $APP_DIR/backend/data
    mkdir -p $APP_DIR/logs

    # 克隆代码（如果提供仓库地址）
    if [ "$REPO_URL" != "https://github.com/your-username/pineapple-after-sales-robot.git" ]; then
        git clone $REPO_URL $APP_DIR
    else
        print_warn "请手动将代码上传到 $APP_DIR"
    fi

    # 设置权限
    chown -R $APP_USER:$APP_USER $APP_DIR
    print_info "应用目录创建完成"
}

# 步骤5: 创建Python虚拟环境
step5_create_venv() {
    print_info "步骤5/8: 创建Python虚拟环境..."
    python3.11 -m venv $VENV_DIR
    source $VENV_DIR/bin/activate

    # 安装依赖
    if [ -f "$APP_DIR/backend/requirements.txt" ]; then
        pip install --upgrade pip
        pip install -r $APP_DIR/backend/requirements.txt
    else
        print_warn "未找到requirements.txt，请手动安装依赖"
        pip install fastapi uvicorn sqlalchemy python-jose passlib python-multipart aiofiles jieba httpx
    fi

    deactivate
    print_info "虚拟环境创建完成"
}

# 步骤6: 配置环境变量
step6_configure_env() {
    print_info "步骤6/8: 配置环境变量..."

    # 创建.env文件
    cat > $APP_DIR/.env << 'EOF'
# DeepSeek API配置
DEEPSEEK_API_KEY=your-api-key-here
DEEPSEEK_API_URL=https://api.deepseek.com/v1/chat/completions

# 数据库配置
DATABASE_URL=sqlite:///./data/after_sales.db

# 应用配置
APP_NAME=苹果蓝牙耳机售后AI客服
APP_VERSION=1.0.0
DEBUG=false

# 安全配置
SECRET_KEY=your-secret-key-change-this
ACCESS_TOKEN_EXPIRE_MINUTES=1440

# CORS配置
ALLOWED_ORIGINS=http://localhost,http://your-domain.com
EOF

    chown $APP_USER:$APP_USER $APP_DIR/.env
    chmod 600 $APP_DIR/.env
    print_warn "请编辑 $APP_DIR/.env 填入实际的API Key"
    print_info "环境变量配置完成"
}

# 步骤7: 配置systemd服务
step7_configure_systemd() {
    print_info "步骤7/8: 配置systemd服务..."

    # 复制服务文件
    cp $APP_DIR/deploy/after-sales-robot.service /etc/systemd/system/

    # 重载systemd
    systemctl daemon-reload

    # 启用服务
    systemctl enable $APP_NAME

    print_info "systemd服务配置完成"
}

# 步骤8: 配置Nginx
step8_configure_nginx() {
    print_info "步骤8/8: 配置Nginx..."

    # 复制Nginx配置
    cp $APP_DIR/deploy/nginx.conf /etc/nginx/sites-available/$APP_NAME

    # 创建软链接
    ln -sf /etc/nginx/sites-available/$APP_NAME /etc/nginx/sites-enabled/

    # 删除默认配置
    rm -f /etc/nginx/sites-enabled/default

    # 测试配置
    nginx -t

    # 重载Nginx
    systemctl reload nginx

    print_info "Nginx配置完成"
}

# 启动服务
start_services() {
    print_info "启动服务..."
    systemctl start $APP_NAME
    systemctl status $APP_NAME --no-pager
    print_info "服务启动完成"
}

# 打印完成信息
print_completion() {
    echo ""
    echo "=========================================="
    echo "  部署完成！"
    echo "=========================================="
    echo ""
    echo "应用目录: $APP_DIR"
    echo "虚拟环境: $VENV_DIR"
    echo "环境配置: $APP_DIR/.env"
    echo "Nginx配置: /etc/nginx/sites-available/$APP_NAME"
    echo ""
    echo "常用命令:"
    echo "  启动服务: sudo systemctl start $APP_NAME"
    echo "  停止服务: sudo systemctl stop $APP_NAME"
    echo "  重启服务: sudo systemctl restart $APP_NAME"
    echo "  查看状态: sudo systemctl status $APP_NAME"
    echo "  查看日志: sudo journalctl -u $APP_NAME -f"
    echo ""
    echo "访问地址:"
    echo "  API文档: http://your-domain.com/docs"
    echo "  健康检查: http://your-domain.com/health"
    echo ""
    echo "注意事项:"
    echo "  1. 请编辑 $APP_DIR/.env 填入DeepSeek API Key"
    echo "  2. 请将代码上传到 $APP_DIR"
    echo "  3. 如需HTTPS，请安装certbot配置SSL证书"
    echo ""
}

# 主函数
main() {
    echo "=========================================="
    echo "  苹果蓝牙耳机售后AI聊天机器人"
    echo "  服务器初始化脚本"
    echo "=========================================="
    echo ""

    check_root

    step1_system_update
    step2_install_python
    step3_install_nginx
    step4_create_app_dir
    step5_create_venv
    step6_configure_env
    step7_configure_systemd
    step8_configure_nginx
    start_services
    print_completion
}

# 运行主函数
main
