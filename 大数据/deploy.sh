#!/bin/bash
# 苹果耳机售后机器人 - 一键部署脚本
# 服务器: 阿里云 ECS Ubuntu 22.04

set -e

echo "========== 1. 安装系统依赖 =========="
apt update && apt install -y python3 python3-pip python3-venv git nginx

echo "========== 2. 创建项目目录 =========="
mkdir -p /var/www/pineapple-after-sales-robot
cd /var/www/pineapple-after-sales-robot

echo "========== 3. 创建Python虚拟环境 =========="
python3 -m venv venv
source venv/bin/activate

echo "========== 4. 上传后端代码 =========="
# 请先在本地执行以下命令上传代码:
# scp -r 后端/* root@8.137.205.18:/var/www/pineapple-after-sales-robot/
echo "请先在本地执行: scp -r 后端/* root@8.137.205.18:/var/www/pineapple-after-sales-robot/"
echo "然后重新运行此脚本"

if [ ! -f "main.py" ]; then
    echo "未找到 main.py，请先上传代码"
    exit 1
fi

echo "========== 5. 安装Python依赖 =========="
pip install --upgrade pip
pip install -r requirements.txt 2>/dev/null || pip install fastapi uvicorn requests python-dotenv jieba

echo "========== 6. 创建 .env 文件 =========="
if [ ! -f ".env" ]; then
    cat > .env << 'ENVEOF'
DEEPSEEK_API_KEY=你的API_KEY
DEEPSEEK_API_URL=https://api.deepseek.com/v1/chat/completions
ADMIN_PHONES=13800000000
ENVEOF
    echo "已创建 .env 文件，请修改 DEEPSEEK_API_KEY 为你的实际Key"
fi

echo "========== 7. 创建 systemd 服务 =========="
cat > /etc/systemd/system/after-sales-robot.service << 'SVCEOF'
[Unit]
Description=Pineapple After Sales Robot API
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/var/www/pineapple-after-sales-robot
Environment="PATH=/var/www/pineapple-after-sales-robot/venv/bin"
ExecStart=/var/www/pineapple-after-sales-robot/venv/bin/uvicorn main:app --host 127.0.0.1 --port 8000
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
SVCEOF

systemctl daemon-reload
systemctl enable after-sales-robot
systemctl start after-sales-robot

echo "========== 8. 配置 Nginx 反向代理 =========="
cat > /etc/nginx/sites-available/after-sales-robot << 'NGXEOF'
server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 60s;
        proxy_connect_timeout 10s;
    }
}
NGXEOF

ln -sf /etc/nginx/sites-available/after-sales-robot /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

echo ""
echo "========== 部署完成! =========="
echo "API地址: http://8.137.205.18"
echo "API文档: http://8.137.205.18/docs"
echo ""
echo "常用命令:"
echo "  查看服务状态: systemctl status after-sales-robot"
echo "  查看日志:     journalctl -u after-sales-robot -f"
echo "  重启服务:     systemctl restart after-sales-robot"
echo ""
echo "重要: 请确保阿里云安全组已开放 80 端口!"
