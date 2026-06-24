# -*- coding: utf-8 -*-
"""
菠萝耳机商城 - 自动部署脚本
上传后端代码到服务器并重启服务
"""
import paramiko
import os
import sys
import time
import tarfile
import io

# 修复 Windows 控制台编码
if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')

HOST = os.getenv("DEPLOY_HOST", "your-server-ip")
USER = os.getenv("DEPLOY_USER", "root")
PASSWORD = os.getenv("DEPLOY_PASSWORD", "")
APP_DIR = "/var/www/pineapple-after-sales-robot"
SRC_DIR = os.path.join(os.path.dirname(__file__), "后端")


def run_cmd(ssh, cmd, desc=""):
    if desc:
        print(f"  > {desc}")
    stdin, stdout, stderr = ssh.exec_command(cmd)
    out = stdout.read().decode()
    err = stderr.read().decode()
    if err and "WARNING" not in err and "already exists" not in err:
        print(f"    warn: {err[:200]}")
    return out


def main():
    print("=" * 50)
    print("菠萝耳机商城 - 自动部署")
    print("=" * 50)

    # 连接服务器
    print(f"\n[1/5] 连接服务器 {HOST}...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    sftp = ssh.open_sftp()
    print("  连接成功")

    # 创建目录结构
    print("\n[2/5] 创建目录结构...")
    run_cmd(ssh, f"mkdir -p {APP_DIR}/static/uploads {APP_DIR}/admin {APP_DIR}/download", "创建目录")

    # 打包后端代码
    print("\n[3/5] 打包并上传后端代码...")
    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode='w:gz') as tar:
        for root, dirs, files in os.walk(SRC_DIR):
            dirs[:] = [d for d in dirs if d not in ('__pycache__', '.git', 'venv', 'node_modules')]
            for f in files:
                if f.endswith('.pyc') or f.endswith('.db'):
                    continue
                full_path = os.path.join(root, f)
                arcname = os.path.relpath(full_path, os.path.dirname(SRC_DIR))
                tar.add(full_path, arcname=arcname)
    buf.seek(0)
    size_mb = len(buf.getvalue()) / 1024 / 1024
    print(f"  打包完成 ({size_mb:.1f} MB)")

    sftp.putfo(buf, f"{APP_DIR}/backend_update.tar.gz")
    run_cmd(ssh, f"cd {APP_DIR} && tar -xzf backend_update.tar.gz && rm backend_update.tar.gz", "解压代码")
    print("  上传完成")

    # 安装依赖
    print("\n[4/5] 检查 Python 依赖...")
    run_cmd(ssh, f"cd {APP_DIR} && pip3 install -q fastapi uvicorn requests python-dotenv jieba pydantic pymysql cryptography 2>/dev/null", "安装依赖")

    # 重启服务
    print("\n[5/5] 重启后端服务...")
    check_service = run_cmd(ssh, "systemctl is-active pineapple-after-sales-robot 2>/dev/null || echo 'no_service'")

    if 'no_service' in check_service:
        run_cmd(ssh, "pkill -f 'uvicorn main:app' 2>/dev/null || true", "停止旧进程")
        time.sleep(1)
        run_cmd(ssh, f"cd {APP_DIR} && nohup python3 -m uvicorn main:app --host 0.0.0.0 --port 8000 --workers 1 > /var/log/pineapple.log 2>&1 &", "启动 uvicorn")
    else:
        run_cmd(ssh, "systemctl restart pineapple-after-sales-robot", "重启 systemd 服务")

    time.sleep(3)

    # 验证
    print("\n验证部署...")
    result = run_cmd(ssh, "curl -s http://localhost:8000/health")
    print(f"  健康检查: {result.strip()}")

    result = run_cmd(ssh, "curl -s http://localhost:8000/api/v1/products")
    print(f"  产品接口: {result.strip()[:100]}")

    sftp.close()
    ssh.close()

    print("\n" + "=" * 50)
    print("部署完成！")
    print("=" * 50)
    print(f"\n访问地址：")
    print(f"  首页下载: http://{HOST}/download/")
    print(f"  管理后台: http://{HOST}/admin/")
    print(f"  API 文档: http://{HOST}/docs")
    print(f"  产品接口: http://{HOST}/api/v1/products")
    print(f"\n管理后台密码: pineapple2026")
    print()


if __name__ == "__main__":
    main()
