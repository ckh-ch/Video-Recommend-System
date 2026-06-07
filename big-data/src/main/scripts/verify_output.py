import paramiko
client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect('192.168.126.130', 22, 'root', '123456')

# 验证 MySQL
stdin, stdout, stderr = client.exec_command("mysql -u root -p123456 video_recommend -e \"SELECT 'users' as tbl, COUNT(*) as cnt FROM users UNION ALL SELECT 'videos', COUNT(*) FROM videos UNION ALL SELECT 'recommend_results', COUNT(*) FROM recommend_results UNION ALL SELECT 'user_profile', COUNT(*) FROM user_profile;\" 2>&1 | grep -v Warning")
print("=== MySQL 数据 ===")
for line in stdout:
    print("  " + line.strip())

# 验证 Redis
stdin, stdout, stderr = client.exec_command("redis-cli keys 'rec:*' | wc -l")
rec_count = stdout.read().decode().strip()
print(f"Redis rec 数量: {rec_count}")

stdin, stdout, stderr = client.exec_command("redis-cli keys 'profile:*' | wc -l")
profile_count = stdout.read().decode().strip()
print(f"Redis profile 数量: {profile_count}")

stdin, stdout, stderr = client.exec_command("redis-cli get 'rec:85500'")
rec_data = stdout.read().decode().strip()
print(f"用户85500推荐前80字符: {rec_data[:80]}...")

client.close()
