"""
ECS部署脚本 - 上传后端代码到服务器并启动服务
"""
import paramiko
import os
import time
import tarfile
import io
import tempfile

HOST = os.getenv("DEPLOY_HOST", "your-server-ip")
USER = os.getenv("DEPLOY_USER", "root")
PASSWORD = os.getenv("DEPLOY_PASSWORD", "")
APP_DIR = "/var/www/after-sales-robot"
SRC_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), "backend")


def run_cmd(ssh, cmd, desc=""):
    if desc:
        print(f"  $ {desc}")
    stdin, stdout, stderr = ssh.exec_command(cmd)
    out = stdout.read().decode()
    err = stderr.read().decode()
    if err and "WARNING" not in err:
        print(f"    stderr: {err[:200]}")
    return out


def main():
    print("Connecting to server...")
    ssh = paramiko.SSHClient()
    ssh.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    ssh.connect(HOST, username=USER, password=PASSWORD, timeout=30)
    sftp = ssh.open_sftp()

    # Step 1: Install system deps
    print("\n[1/7] Installing system dependencies...")
    run_cmd(ssh, "apt-get update", "apt update")
    run_cmd(ssh, "DEBIAN_FRONTEND=noninteractive apt-get install -y -qq nginx python3-pip python3-venv git", "install nginx, python")

    # Step 2: Create directories
    print("\n[2/7] Creating app directory structure...")
    run_cmd(ssh, f"mkdir -p {APP_DIR}/backend/data {APP_DIR}/logs", "mkdir")

    # Step 3: Clean old files and upload backend
    print("\n[3/7] Preparing backend files...")
    run_cmd(ssh, f"rm -rf {APP_DIR}/backend/*", "clean old files")

    buf = io.BytesIO()
    with tarfile.open(fileobj=buf, mode='w:gz') as tar:
        for root, dirs, files in os.walk(SRC_DIR):
            dirs[:] = [d for d in dirs if d not in ('__pycache__', '.git', 'data')]
            for f in files:
                if f.endswith('.pyc') or f.endswith('.db'):
                    continue
                full_path = os.path.join(root, f)
                arcname = os.path.relpath(full_path, os.path.dirname(SRC_DIR))
                tar.add(full_path, arcname=arcname)
    buf.seek(0)

    print("\n[4/7] Uploading backend code...")
    sftp.putfo(buf, f"{APP_DIR}/backend.tar.gz")
    run_cmd(ssh, f"cd {APP_DIR} && tar -xzf backend.tar.gz && rm backend.tar.gz")
    run_cmd(ssh, f"mkdir -p {APP_DIR}/backend/data")
    print("  Upload complete")

    # Step 5: Python venv and deps
    print("\n[5/7] Setting up Python virtual environment...")
    run_cmd(ssh, f"python3 -m venv {APP_DIR}/backend/venv", "create venv")
    run_cmd(ssh, f"cd {APP_DIR}/backend && ./venv/bin/pip install fastapi uvicorn requests python-dotenv jieba pydantic pymysql cryptography 2>&1 | tail -5", "pip install")

    # Step 6: Configure .env
    print("\n[6/7] Configuring environment...")
    mysql_password = os.getenv("MYSQL_PASSWORD", "")
    deepseek_api_key = os.getenv("DEEPSEEK_API_KEY", "")
    admin_phones = os.getenv("ADMIN_PHONES", "")
    env_cmd = f"""cat > {APP_DIR}/backend/.env << 'ENVEOF'
MYSQL_HOST=127.0.0.1
MYSQL_PORT=3306
MYSQL_USER=airpods
MYSQL_PASSWORD={mysql_password}
MYSQL_DB=after_sales_robot
DEEPSEEK_API_KEY={deepseek_api_key}
DEEPSEEK_API_URL=https://api.deepseek.com/v1/chat/completions
ADMIN_PHONES={admin_phones}
ENVEOF"""
    run_cmd(ssh, env_cmd, "write .env")

    # Set permissions
    run_cmd(ssh, f"chown -R root:root {APP_DIR} && chmod -R 755 {APP_DIR}")

    # Step 7: Start service
    print("\n[7/7] Starting backend service...")
    # Kill existing uvicorn
    run_cmd(ssh, "pkill -f uvicorn || true", "stop old uvicorn")
    time.sleep(1)

    # Start with nohup
    run_cmd(ssh, f"cd {APP_DIR}/backend && nohup ./venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000 > /tmp/uvicorn.log 2>&1 &", "start uvicorn")
    time.sleep(3)

    # Verify
    print("\n  Testing health check...")
    stdin, stdout, stderr = ssh.exec_command(f"curl -s http://localhost:8000/health")
    result = stdout.read().decode()
    print(f"  Health check: {result}")

    # Test API with AI chat
    print("\n  Testing AI API...")
    test_cmd = f"""curl -s -X POST {APP_DIR}/backend/venv/python3 -c 'import httpx; r = httpx.post("http://localhost:8000/", timeout=5); print(r.text)' 2>/dev/null || curl -s http://localhost:8000/"""
    stdin, stdout, stderr = ssh.exec_command("curl -s http://localhost:8000/")
    print(f"  API root: {stdout.read().decode()}")

    sftp.close()
    ssh.close()

    print("\n" + "=" * 60)
    print("  Deployment complete!")
    print(f"  API: http://{HOST}:8000/")
    print(f"  Docs: http://{HOST}:8000/docs")
    print("=" * 60)


if __name__ == "__main__":
    main()
