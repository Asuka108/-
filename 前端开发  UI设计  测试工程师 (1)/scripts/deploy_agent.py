import paramiko
import sys
import os

base = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(base)

files_to_upload = [
    (os.path.join(project_root, 'docs', 'index.html'), '/var/www/download/index.html'),
    (os.path.join(project_root, 'docs', 'agent.html'), '/var/www/download/agent.html'),
]

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
try:
    client.connect('8.137.205.18', username='root', password='Dsj123456.', timeout=15)
except Exception as e:
    print(f'SSH connect failed: {e}')
    sys.exit(1)

sftp = client.open_sftp()
for local, remote in files_to_upload:
    if not os.path.exists(local):
        print(f'NOT FOUND: {local}')
        continue
    try:
        sftp.put(local, remote)
        size_kb = os.path.getsize(local) / 1024
        print(f'OK  {os.path.basename(local)} -> {remote} ({size_kb:.1f} KB)')
    except Exception as e:
        print(f'FAIL {os.path.basename(local)}: {e}')

sftp.close()

# Verify
stdin, stdout, stderr = client.exec_command(
    'echo "=== index.html ===" && head -3 /var/www/download/index.html && '
    'echo "=== agent.html ===" && grep -c "lastMsgId.*Date.now" /var/www/download/agent.html || echo "agent.html: Bug line NOT found (OK)"'
)
print('\n' + stdout.read().decode())

client.close()
print('\nDeploy complete.')
