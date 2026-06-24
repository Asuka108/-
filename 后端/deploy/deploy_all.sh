#!/bin/bash
# ============================================
# 菠萝耳机商城 - 一键部署脚本
# 服务器: 8.137.205.18
# ============================================

set -e

SERVER="root@8.137.205.18"
REMOTE_DIR="/var/www/pineapple-after-sales-robot"
LOCAL_BACKEND="后端"
LOCAL_APK="../c/android/app/build/outputs/apk/debug/app-debug.apk"

echo "=========================================="
echo "🍍 菠萝耳机商城 - 部署脚本"
echo "=========================================="

# 1. 上传后端代码（含管理后台和下载页面）
echo ""
echo "📦 [1/4] 上传后端代码..."
scp -r "$LOCAL_BACKEND"/* "$SERVER:$REMOTE_DIR/"

echo ""
echo "✅ 后端代码上传完成"

# 2. 上传 APK 文件
echo ""
echo "📱 [2/4] 上传 APK 文件..."
if [ -f "$LOCAL_APK" ]; then
    scp "$LOCAL_APK" "$SERVER:$REMOTE_DIR/download/app-debug.apk"
    echo "✅ APK 上传完成"
else
    echo "⚠️  APK 文件不存在: $LOCAL_APK"
    echo "   请先在 Android Studio 中编译项目"
    echo "   编译后运行: adb pull /path/to/app-debug.apk"
fi

# 3. 创建必要目录
echo ""
echo "📁 [3/4] 创建必要目录..."
ssh "$SERVER" "mkdir -p $REMOTE_DIR/static/uploads $REMOTE_DIR/download $REMOTE_DIR/admin"

# 4. 重启后端服务
echo ""
echo "🔄 [4/4] 重启后端服务..."
ssh "$SERVER" "systemctl restart pineapple-after-sales-robot 2>/dev/null || cd $REMOTE_DIR && pkill -f 'uvicorn main:app' 2>/dev/null; sleep 2; nohup python3 -m uvicorn main:app --host 0.0.0.0 --port 8000 --workers 1 > /var/log/pineapple.log 2>&1 &"

echo ""
echo "=========================================="
echo "✅ 部署完成！"
echo "=========================================="
echo ""
echo "🌐 访问地址："
echo "   首页下载: http://8.137.205.18/download/"
echo "   管理后台: http://8.137.205.18/admin/"
echo "   API 文档: http://8.137.205.18/docs"
echo "   产品接口: http://8.137.205.18/api/v1/products"
echo ""
echo "🔑 管理后台密码: pineapple2026"
echo ""
