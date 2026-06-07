import paramiko

c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect('192.168.126.130', 22, 'root', '123456')

stdin, stdout, stderr = c.exec_command("redis-cli del profile:85500:stats")
print("删除结果:", stdout.read().decode().strip())

stdin, stdout, stderr = c.exec_command("redis-cli type profile:85500:stats")
print("删除后类型:", stdout.read().decode().strip())

c.close()
