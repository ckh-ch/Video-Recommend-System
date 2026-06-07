import socket
host = '192.168.126.130'
ports = [22, 3306, 6379, 9092, 9000, 8020, 9870, 8088, 2181]

for p in ports:
    s = socket.socket()
    s.settimeout(1)
    r = s.connect_ex((host, p))
    status = "open" if r == 0 else "closed"
    print(f"Port {p}: {status}")
    s.close()
