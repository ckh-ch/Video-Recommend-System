import paramiko
client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect('192.168.126.130', 22, 'root', '123456')

stdin, stdout, stderr = client.exec_command("grep -E 'advertised.listeners|listeners|broker.id' /usr/local/kafka_2.13-3.8.1/config/server.properties")
print("=== Kafka 网络配置 ===")
for line in stdout:
    print("  " + line.strip())

client.close()
