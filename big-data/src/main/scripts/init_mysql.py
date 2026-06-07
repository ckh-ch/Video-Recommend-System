import paramiko

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect('192.168.126.130', 22, 'root', '123456')

# 创建数据库
stdin, stdout, stderr = client.exec_command('mysql -u root -p123456 -e "CREATE DATABASE IF NOT EXISTS video_recommend DEFAULT CHARSET utf8mb4;" 2>&1')
out = stdout.read().decode().strip()
err = stderr.read().decode().strip()
if out or err:
    print(f"输出: {out} {err}")
else:
    print("数据库 video_recommend 创建成功（或已存在）")

# 建表 - 读取本地 schema.sql
import os
script_dir = os.path.dirname(os.path.abspath(__file__))
schema_path = os.path.join(script_dir, '..', '..', '..', '..', 'video-recommend', 'src', 'main', 'resources', 'schema.sql')
schema_path = os.path.normpath(schema_path)

with open(schema_path, 'r', encoding='utf-8') as f:
    sql = f.read()

# 远程建表
stdin, stdout, stderr = client.exec_command(f'mysql -u root -p123456 video_recommend -e "{sql}" 2>&1')
out = stdout.read().decode().strip()
err = stderr.read().decode().strip()
if err and 'ERROR' in err:
    print(f"建表错误: {err}")
else:
    print("表结构初始化完成")

# 验证表
stdin, stdout, stderr = client.exec_command('mysql -u root -p123456 video_recommend -e "SHOW TABLES;" 2>&1')
print("当前表:")
for line in stdout:
    print(f"  {line.strip()}")

client.close()
