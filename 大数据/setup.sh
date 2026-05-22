#!/bin/bash
# 一键部署后半段

# 创建 systemd 服务文件
python3 -c "
content = '''[Unit]
Description=Pineapple After Sales Robot API
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/var/www/pineapple-after-sales-robot
Environment=PATH=/var/www/pineapple-after-sales-robot/venv/bin
ExecStart=/var/www/pineapple-after-sales-robot/venv/bin/uvicorn main:app --host 127.0.0.1 --port 8000
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
'''
with open('/etc/systemd/system/after-sales-robot.service', 'w') as f:
    f.write(content)
print('service file created')
"

# 配置 Nginx
python3 -c "
content = '''server {
    listen 80;
    server_name _;

    location / {
        proxy_pass http://127.0.0.1:8000;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_read_timeout 60s;
        proxy_connect_timeout 10s;
    }
}
'''
with open('/etc/nginx/sites-available/after-sales-robot', 'w') as f:
    f.write(content)
print('nginx config created')
"

# 启用配置
ln -sf /etc/nginx/sites-available/after-sales-robot /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx

# 启动服务
systemctl daemon-reload
systemctl enable after-sales-robot
systemctl start after-sales-robot
systemctl status after-sales-robot

echo ""
echo "========== 部署完成! =========="
echo "API地址: http://8.137.205.18"
echo "API文档: http://8.137.205.18/docs"
