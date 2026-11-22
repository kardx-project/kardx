import sys, json, socket, base64, os, struct, urllib.request, urllib.error, time
port = int(sys.argv[1]); base = f'http://localhost:{port}'
# 샘플 앱의 메시지 API(STOMP·WebSocket·SSE)는 데모 토큰을 요구한다 (samples/*/src/main/.../common/DemoAuth.kt)
TOKEN = 'demo-token'
results = []
def check(name, cond, detail=''):
    results.append((name, cond, detail))
def http(method, path, data=None, headers=None):
    req = urllib.request.Request(base + path, data=data, method=method, headers=headers or {})
    try:
        with urllib.request.urlopen(req, timeout=10) as r: return r.status, dict(r.headers), r.read().decode('utf-8')
    except urllib.error.HTTPError as e: return e.code, dict(e.headers), e.read().decode('utf-8')

# 앱이 뜰 때까지 기다린다. 아직 연결이 안 되거나(예외) 200 이 아니면(404·503 등 시작 중) 1초 쉬고 다시 묻는다
STARTUP_TRIES = 60
for attempt in range(STARTUP_TRIES):
    try:
        status = http('GET', '/docs/index.html')[0]
    except Exception as error:
        status = error
    if status == 200: break
    if attempt == STARTUP_TRIES - 1:
        sys.exit(f'App at {base} did not serve /docs/index.html within {STARTUP_TRIES} s (last result: {status})')
    time.sleep(1)

s, h, b = http('GET', '/api/v1/products/1', headers={'Authorization': 'Bearer x'})
d = json.loads(b); check('상품 조회 200 + ETag + 래퍼', s == 200 and h.get('ETag') == '"3"' and d['data']['options'][1]['additionalPrice'] == 3000)
s, _, b = http('GET', '/api/v1/products?keyword=%ED%82%A4%EB%B3%B4%EB%93%9C&page=2&size=5')
d = json.loads(b); check('상품 검색 (검색 조건 바인딩)', s == 200 and d['data']['page'] == 2 and d['data']['size'] == 5)
s, h, b = http('POST', '/api/v1/products', json.dumps({'name': '키보드', 'price': 1000, 'tags': ['a']}).encode(), {'Content-Type': 'application/json'})
check('상품 등록 201 + Location', s == 201 and h.get('Location') == '/api/v1/products/10')
s, _, _ = http('POST', '/api/v1/products', json.dumps({'name': '', 'price': 1000}).encode(), {'Content-Type': 'application/json'})
check('상품 등록 검증 실패 400 (@NotBlank)', s == 400)
s, _, _ = http('POST', '/api/v1/products', json.dumps({'name': 'x', 'price': 1000, 'tags': ['1','2','3','4','5','6']}).encode(), {'Content-Type': 'application/json'})
check('상품 등록 검증 실패 400 (@Size 태그 6개)', s == 400)
s, _, b = http('GET', '/api/v1/products/recent'); check('배열 데이터 응답', s == 200 and len(json.loads(b)['data']) == 2)
s, _, b = http('GET', '/api/v1/products/1/recommendation'); check('null 데이터 응답', s == 200 and json.loads(b)['data'] is None)
s, h, b = http('GET', '/api/v1/products/1/resource'); check('HAL 응답 (_links)', s == 200 and 'hal+json' in h.get('Content-Type','') and '_links' in json.loads(b))
s, _, _ = http('DELETE', '/api/v1/products/1'); check('상품 삭제 204', s == 204)
boundary = 'XBOUNDARY'
body = (f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="k.png"\r\nContent-Type: image/png\r\n\r\n').encode() + b'\x89PNG' * 8 + \
       (f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="meta"\r\nContent-Type: application/json\r\n\r\n{{"alt":"정면","primary":true}}\r\n--{boundary}--\r\n').encode()
s, _, b = http('POST', '/api/v1/products/1/images', body, {'Content-Type': f'multipart/form-data; boundary={boundary}'})
check('멀티파트 업로드 (파일 + JSON 파트)', s == 200 and json.loads(b)['data']['size'] == 32, b[:120])
s, h, _ = http('POST', '/api/v1/auth/login', b'username=hong&password=pw', {'Content-Type': 'application/x-www-form-urlencoded'})
cookie = h.get('Set-Cookie', '')
check('폼 로그인 → Set-Cookie', s == 204 and cookie.startswith('SESSION=s3cr3t-hong') and 'HttpOnly' in cookie)
s, _, b = http('GET', '/api/v1/auth/me', headers={'Cookie': cookie.split(';')[0]})
check('쿠키로 내 정보', s == 200 and json.loads(b)['sessionPrefix'] == 's3cr', b)
s, _, b = http('GET', '/api/v1/coupons/WELCOME', ); check('쿠폰 (Java record 직렬화)', s == 200 and json.loads(b)['data']['discountRate'] == 15)
s, _, _ = http('GET', '/api/v1/notifications/stream', headers={'Accept': 'text/event-stream'})
check('SSE 인증: 토큰 없으면 401', s == 401, str(s))
s, _, _ = http('GET', '/api/v1/notifications/stream', headers={'Accept': 'text/event-stream', 'Authorization': 'Bearer wrong'})
check('SSE 인증: 틀린 토큰이면 401', s == 401, str(s))
s, h, b = http('GET', '/api/v1/notifications/stream', headers={'Accept': 'text/event-stream', 'Authorization': f'Bearer {TOKEN}'})
check('SSE 인증: Authorization 헤더로 200', s == 200 and b.count('event:') == 2, str(s))
s, h, b = http('GET', f'/api/v1/notifications/stream?access_token={TOKEN}', headers={'Accept': 'text/event-stream'})
check('SSE 스트림 (access_token 쿼리, event-stream, 이벤트 2개)', s == 200 and 'text/event-stream' in h.get('Content-Type','') and b.count('event:') == 2 and 'event:notification' in b)

# ---- WebSocket (raw socket) ----
def ws_connect(path):
    sock = socket.create_connection(('localhost', port), timeout=5)
    key = base64.b64encode(os.urandom(16)).decode()
    sock.sendall((f'GET {path} HTTP/1.1\r\nHost: localhost:{port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n'
                  f'Sec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\n\r\n').encode())
    resp = b''
    while b'\r\n\r\n' not in resp: resp += sock.recv(1)
    return sock, resp.decode().split('\r\n')[0]
def ws_send(sock, text):
    data = text.encode(); mask = os.urandom(4)
    head = bytes([0x81]) + (bytes([0x80 | len(data)]) if len(data) < 126 else bytes([0x80 | 126]) + struct.pack('>H', len(data)))
    sock.sendall(head + mask + bytes(b ^ mask[i % 4] for i, b in enumerate(data)))
def recv_exact(sock, n):
    buf = b''
    while len(buf) < n: buf += sock.recv(n - len(buf))
    return buf
def ws_recv(sock):
    b1, b2 = recv_exact(sock, 2); n = b2 & 0x7f
    if n == 126: n = struct.unpack('>H', recv_exact(sock, 2))[0]
    elif n == 127: n = struct.unpack('>Q', recv_exact(sock, 8))[0]
    return b1 & 0x0f, recv_exact(sock, n).decode('utf-8', 'replace')

sock, status = ws_connect('/ws/echo')
check('WebSocket 인증: 토큰 없으면 핸드셰이크 401', ' 401' in status, status)
sock.close()
sock, status = ws_connect(f'/ws/echo?access_token={TOKEN}')
_, welcome = ws_recv(sock); ws_send(sock, '{"text":"hello"}'); _, reply = ws_recv(sock)
check('WebSocket 핸들러: access_token 쿼리로 핸드셰이크 101 + 환영(user=hong) + 응답', '101' in status and json.loads(welcome)['type'] == 'welcome' and json.loads(welcome)['user'] == 'hong' and json.loads(reply)['upper'] == 'HELLO', reply)
ws_send(sock, '{"text":""}'); op, payload = ws_recv(sock)
check('WebSocket 핸들러: 빈 text → 1008 종료', op == 8 and payload.encode('latin-1', 'ignore')[:0] == b'' and status)
sock.close()

def stomp(frame): ws_send(sock, frame + '\x00')
sock, status = ws_connect('/ws')
stomp('CONNECT\naccept-version:1.2\nhost:localhost\n\n')
_, rejected = ws_recv(sock)
check('STOMP 인증: 토큰 없는 CONNECT → ERROR 프레임', rejected.startswith('ERROR'), rejected[:200])
sock.close()
sock, status = ws_connect('/ws')
stomp('CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer wrong\n\n')
_, rejected = ws_recv(sock)
check('STOMP 인증: 틀린 토큰 CONNECT → ERROR 프레임', rejected.startswith('ERROR'), rejected[:200])
sock.close()
sock, status = ws_connect('/ws')
stomp(f'CONNECT\naccept-version:1.2\nhost:localhost\nAuthorization:Bearer {TOKEN}\n\n')
_, connected = ws_recv(sock)
stomp('SUBSCRIBE\nid:sub-0\ndestination:/topic/rooms.7\n\n')
time.sleep(0.3)
stomp('SEND\ndestination:/app/chat.send\ncontent-type:application/json\n\n{"roomId":7,"content":"라이브"}')
_, message = ws_recv(sock)
body = message.split('\n\n', 1)[1].rstrip('\x00')
check('STOMP: CONNECT → SUBSCRIBE → SEND → MESSAGE', connected.startswith('CONNECTED') and message.startswith('MESSAGE') and json.loads(body)['content'] == '라이브', message[:200])
sock.close()

fails = 0
for name, ok, detail in results:
    if not ok: fails += 1
    print(('PASS  ' if ok else 'FAIL  ') + name + ('' if ok or not detail else f'   [{detail}]'))
print(f'--- {len(results) - fails}/{len(results)} PASS')
sys.exit(1 if fails else 0)
