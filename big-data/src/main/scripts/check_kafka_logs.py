import paramiko
client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect('192.168.126.130', 22, 'root', '123456')

stdin, stdout, stderr = client.exec_command("tail -20 /usr/local/kafka_2.13-3.8.1/logs/server.log 2>&1 | grep -E 'ERROR|FATAL|Started'")
print("=== Kafka 日志 ===")
for l in stdout:
    print("  " + l.strip())

client.close()
