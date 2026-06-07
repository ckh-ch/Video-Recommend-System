import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('192.168.126.130', 22, 'root', '123456')

# 获取所有 profile 和 rec 的 key 数量
stdin, stdout, stderr = c.exec_command("redis-cli keys 'profile:*' | wc -l")
print("旧的 profile key 数:", stdout.read().decode().strip())

# 删除所有 profile: 开头的 key
stdin, stdout, stderr = c.exec_command("redis-cli eval \"return redis.call('del', unpack(redis.call('keys', ARGV[1])))\" 0 'profile:*'")
print("删除结果:", stdout.read().decode().strip())

# 验证
stdin, stdout, stderr = c.exec_command("redis-cli keys 'profile:*' | wc -l")
print("剩余 profile key 数:", stdout.read().decode().strip())

c.close()
