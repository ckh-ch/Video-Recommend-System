import paramiko
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('192.168.126.130', 22, 'root', '123456')

stdin, stdout, stderr = c.exec_command("cat /usr/local/kafka_2.13-3.8.1/config/server.properties | grep -v '^#' | grep -v '^$' | head -30")
print("=== Kafka 配置 ===")
for l in stdout:
    print("  " + l.strip())

print()
stdin, stdout, stderr = c.exec_command("redis-cli hgetall profile:85500:stats | head -10")
print("=== Redis profile:85500:stats ===")
for l in stdout:
    print("  " + l.strip())

c.close()
