# -*- coding: utf-8 -*-
import socket, struct, sys

HOST = "127.0.0.1"; PORT = 25575; PWD = "test123"

def recv_exact(s, n):
    buf = b''
    while len(buf) < n:
        c = s.recv(n - len(buf))
        if not c: raise EOFError("closed")
        buf += c
    return buf

def make_pkt(rid, typ, payload):
    data = payload.encode() + b'\x00'
    length = 4 + 4 + len(data) + 2  # requestID + type + data + \x00\x00 padding
    return struct.pack('<iii', length, rid, typ) + data + b'\x00\x00'

def read_pkt(s):
    length, = struct.unpack('<i', recv_exact(s, 4))
    rid, typ, = struct.unpack('<ii', recv_exact(s, 8))
    body = recv_exact(s, length - 8)
    if body.endswith(b'\x00\x00'): body = body[:-2]
    elif body.endswith(b'\x00'): body = body[:-1]
    return rid, typ, body.decode('utf-8', 'replace')

s = socket.create_connection((HOST, PORT), timeout=8)
s.sendall(make_pkt(1, 3, PWD))
rid, typ, body = read_pkt(s)
print("auth: rid=%s typ=%s body=%r" % (rid, typ, body))
for cmd in sys.argv[1:]:
    s.sendall(make_pkt(2, 2, cmd))
    rid, typ, body = read_pkt(s)
    print("RCON>", cmd)
    print(body)
s.close()

