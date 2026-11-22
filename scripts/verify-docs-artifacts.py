import sys, os, re, json
root = sys.argv[1]
results = []
# Boot 4 샘플만 켠 선택 기능: 공통 오류 응답(openApiCommonResponses), 기준 스펙, async/index 파일 이름
BOOT4 = root.rstrip('/').endswith('spring-boot-4')
def read(p):
    with open(os.path.join(root, p), encoding='utf-8') as f: return f.read()
def check(feature, cond, detail=''):
    results.append((feature, 'PASS' if cond else 'FAIL', detail))
S = 'build/generated-snippets/'
A = 'build/generated-asciidoc/'
# apiDocsIndex 출력. test 가 비우는 generated-asciidoc 밖에 있다
INDEX = 'build/kardx/index/index.adoc'
def snip(cls, m, f): return read(f'{S}{cls}/{m}/{f}')
def rows(table):
    """표의 행 목록. 첫 칸이 `+..+` 인 줄에서 다음 같은 줄 전까지를 한 행으로 본다 (칸 안의 빈 줄 포함)."""
    body = table.split('\n')
    out, cur = [], None
    for line in body:
        if line.startswith('|`+') and (cur is None or line.startswith('|`+')):
            if cur is not None and not cur_is_cell(line, cur): pass
        if re.match(r'^\|`\+[^+]+\+`$', line) and (cur is None or len([l for l in cur if l.startswith('|')]) >= ncols(table)):
            if cur is not None: out.append(cur)
            cur = [line]
        elif cur is not None:
            if line.startswith('|==='): out.append(cur); cur = None
            else: cur.append(line)
    return out
def cur_is_cell(line, cur): return True
def ncols(table):
    header = [l for l in table.split('\n') if l.startswith('|') and not l.startswith('|===')][0]
    return header.count('|')
def row(table, path):
    for r in rows(table):
        if r[0] == f'|`+{path}+`': return '\n'.join(r)
    return ''
def paths(table): return [r[0][3:-2] for r in rows(table)]
def toc_index_link(html):
    """하위 문서 왼쪽 목차(<div id="toc">) 의 제목 자리에 있는 index 링크 대상. 없으면 None."""
    m = re.search(r'<div id="toc"[^>]*>\s*<div id="toctitle">(.*?)</div>', html, re.S)
    link = m and re.search(r'<a [^>]*href="([^"]+)"', m.group(1))
    return link.group(1) if link else None
def check_language(labels):
    """샘플은 kardx { language = "ko" } 다. 라이브러리 문구(core·플러그인)가 모두 한국어이고 영어 문구가 섞이지 않았는지.
    labels: (파일, 한국어 문구, 같은 자리의 영어 문구). 한쪽만 언어를 받으면(예: yml 에만 language) 섞여서 실패한다."""
    for path, korean, english in labels:
        text = read(path)
        check(f'언어 ko: {path} 에 "{korean}" (영어 "{english}" 없음)', korean in text and english not in text,
              f'ko={korean in text} en={english in text}')
def check_viewers_without_comments(pages):
    """플러그인·core 가 만든 뷰어 페이지에는 템플릿 주석(<!-- -->)이 남지 않는다 (한국어 주석 노출 방지)."""
    for page in pages:
        check(f'뷰어 주석 없음: {page}', '<!--' not in read(page), str(read(page).count('<!--')))
def check_async_api_auth(aa):
    """asyncApi { bearerAuth() }: components.securitySchemes 와 모든 서버의 security, 설명의 데모 토큰."""
    servers = re.search(r'\nservers:\n((?:  .*\n)+)', aa)
    names = re.findall(r'^  (\w+):$', servers.group(1), re.M) if servers else []
    refs = servers.group(1).count('- $ref: "#/components/securitySchemes/bearerAuth"') if servers else 0
    check('AsyncAPI 인증: components.securitySchemes.bearerAuth (http bearer)', re.search(r'\n  securitySchemes:\n    bearerAuth:\n      type: "http"\n      scheme: "bearer"\n', aa) is not None)
    check('AsyncAPI 인증: 모든 서버의 security 가 bearerAuth', names and refs == len(names), f'servers={names} refs={refs}')
    check('AsyncAPI 인증: 설명에 데모 토큰 (뷰어 연결 시험용)', '`demo-token`' in aa.split('\nservers:')[0])
def check_query_token_masked(feature, cls, method, wiremock=True):
    """mask-sensitive-headers: true 는 잘 알려진 자격 증명 쿼리(access_token 등)도 가린다. 스니펫에 원래 값이 없고, WireMock 은 아무 값이나 받는다."""
    d = f'{S}{cls}/{method}/'
    reqs = {f: read(d + f) for f in ['curl-request.adoc', 'http-request.adoc', 'httpie-request.adoc']}
    masked = all('access_token=****' in t and 'access_token=demo-token' not in t for t in reqs.values())
    check(f'{feature}: curl·http·httpie 스니펫의 access_token=**** (원래 값 없음)', masked, ' | '.join(l for t in reqs.values() for l in t.split('\n') if 'access_token' in l))
    if wiremock:
        wm = read(f'{S}wiremock/mappings/{cls}/{method}.json')
        check(f'{feature}: WireMock 매핑의 access_token 은 matches ".*" (토큰 값 없음)', re.search(r'"access_token" : \{\s*"matches" : "\.\*"', wm) is not None and 'demo-token' not in wm, wm[:300])
def check_coverage():
    """apiDocsCoverage: 샘플 앱의 엔드포인트를 모두 문서화했다 (문서화하지 않은 것 0, 빼는 것은 기본 제외 경로뿐)."""
    cov = json.loads(read('build/api-docs/coverage/coverage.json'))['summary']
    md = read('build/api-docs/coverage/coverage.md')
    check('커버리지: coverage.json 100% (문서화하지 않은 엔드포인트 0)', cov['undocumented'] == 0 and cov['documented'] == cov['endpoints'] > 0, str(cov))
    check('커버리지: coverage.md 요약 줄', f"**{cov['documented']}/{cov['endpoints']}** endpoints documented (0 undocumented" in md, md[:200])
    excluded = md.split('## Excluded', 1)[1] if '## Excluded' in md else ''
    check('커버리지: springdoc 은 기본 제외 (/v3/api-docs.yaml, /swagger-ui.html 포함)', all(f'GET {p}\n' in excluded + '\n' for p in ['/v3/api-docs', '/v3/api-docs.yaml', '/swagger-ui.html']), excluded.strip()[:300])
def report():
    fails = 0
    for f, s, d in results:
        if s == 'FAIL': fails += 1
        print(f"{s}  {f}" + (f"   [{d}]" if s == 'FAIL' and d else ''))
    print(f"--- {len(results)-fails}/{len(results)} PASS")
    sys.exit(1 if fails else 0)

# WebFlux 샘플 (samples/spring-boot-4-webflux): WebTestClient 로 만든 산출물만 따로 검사한다. 아래 MVC 샘플 검사는 돌지 않는다
if root.rstrip('/').endswith('spring-boot-4-webflux'):
    P = 'product-api-docs-test'
    lf = snip(P, 'list-products', 'response-fields.adoc')
    check('WebFlux: Flow 응답 배열 필드', paths(lf) == ['[].id', '[].name', '[].price', '[].status'], ','.join(paths(lf)))
    check('WebFlux: 쿼리 파라미터 (선택)', '|false' in row(snip(P, 'list-products', 'query-parameters.adoc'), 'status'))
    gf = snip(P, 'get-product', 'response-fields.adoc')
    check('WebFlux: suspend 핸들러 응답 필드 + @Masked 표시', '*Masked*: 가운데 자리를 *' in row(gf, 'sellerPhone') and 'SALE : 판매중' in gf, row(gf, 'sellerPhone'))
    check('WebFlux: 경로 변수', '`+id+`' in snip(P, 'get-product', 'path-parameters.adoc'))
    check('WebFlux: 오류 응답 api-summary (404)', '`GET /api/v1/products/{id}` → `404 Not Found`' in snip(P, 'get-product-not-found', 'api-summary.adoc'))
    cr = snip(P, 'create-product', 'http-request.adoc') + snip(P, 'create-product', 'curl-request.adoc')
    check('WebFlux: Authorization 마스킹 (방식은 남김)', 'Authorization: Bearer ****' in cr and 'real-token' not in cr)
    check('WebFlux: 201 Location 헤더', '`+Location+`' in snip(P, 'create-product', 'response-headers.adoc'))
    check('WebFlux: 요청 필드 제약조건', 'NotBlank' in row(snip(P, 'create-product', 'request-fields.adoc'), 'name'))
    ir = snip(P, 'create-product-invalid', 'http-response.adoc')
    check('WebFlux: 검증 실패 응답 (WebExchangeBindException → ErrorResponse)', 'HTTP/1.1 400 Bad Request' in ir and '"field" : "sellerPhone"' in ir, ir[:300])
    check('WebFlux: 본문 없는 Mono 응답 (204)', 'HTTP/1.1 204 No Content' in snip(P, 'delete-product', 'http-response.adoc'))
    E = 'product-event-api-docs-test'
    se = snip(E, 'subscribe', 'sse-events.adoc')
    check('WebFlux: SSE 이벤트 표 (optional 포함)', all(f'`+{e}+`' in se for e in ['price-changed', 'sold-out', 'restocked']) and '|false' in row(se, 'restocked'))
    check('WebFlux: SSE 이벤트 data 필드', paths(snip(E, 'subscribe', 'response-fields-event-price-changed.adoc')) == ['productId', 'before', 'after'])
    check('WebFlux: 스트림에 없는 optional 이벤트는 필드 표 없음', not os.path.exists(os.path.join(root, S, E, 'subscribe/response-fields-event-restocked.adoc')))
    idx = read(INDEX)
    check('WebFlux: index 링크 (문서화 테스트 2개)', re.findall(r'\[\[([a-z-]+-test)]]', idx) == [P, E] and 'web-application-type-test' not in idx)
    check('WebFlux: Enum 코드 문서', '| SALE\n| 판매중' in read(A + 'code/enum-code.adoc'))
    y = read('build/api-docs/openapi3.yaml')
    check('WebFlux OpenAPI: 경로', all(f'\n  {p}:\n' in y for p in ['/api/v1/products', '/api/v1/products/{id}', '/api/v1/product-events']))
    check('WebFlux OpenAPI: 한 operation 에 성공·오류 응답 (GET 200+404, POST 201+400)', re.search(r'"404":\n(?:.*\n)*?\s+"200":', y) is not None and re.search(r'"201":\n(?:.*\n)*?\s+"400":', y) is not None)
    check('WebFlux OpenAPI: 공통 오류 스키마 (yml open-api.error-schema)', 'ErrorResponse:' in y and y.count('#/components/schemas/ErrorResponse') >= 2)
    # 선언한 오류 응답 (errorResponse): 테스트가 만들지 않은 상태가 OpenAPI 와 문서에 들어간다
    wl = y[y.index('\n  /api/v1/products:\n'):]
    check('WebFlux OpenAPI 선언 오류 응답: 상태·설명만 (400, 본문은 공통 오류 스키마)', re.search(r'"400":\n\s+description: 상태 값이 정의되지 않은 값\n\s+content:\n\s+application/json:\n\s+schema:\n\s+\$ref: "#/components/schemas/ErrorResponse"', wl) is not None)
    check('WebFlux OpenAPI 선언 오류 응답: 필드 + Content-Type (409 application/problem+json → ErrorResponse)', re.search(r'"409":\n\s+description: 같은 이름의 상품이 이미 있음\n\s+content:\n\s+application/problem\+json:\n\s+schema:\n\s+\$ref: "#/components/schemas/ErrorResponse"', y) is not None)
    check('WebFlux OpenAPI 선언 오류 응답: 예시 객체 한 줄 (404 + example)', re.search(r'"404":\n\s+description: 상품이 없음\n(?:.*\n){1,4}?\s+\$ref: "#/components/schemas/ErrorResponse"\n\s+example:\n\s+code: 404\n\s+message: 상품을 찾을 수 없습니다', y) is not None)
    check('WebFlux OpenAPI 선언 오류 응답: 같은 모양의 스키마를 새로 만들지 않음 (ConflictResponse 없음)', 'ConflictResponse' not in y and 'NotFoundResponse' not in y)
    check('WebFlux 선언 오류 응답 스니펫·HTML (error-responses)', '|`404 Not Found`\n|상품이 없음' in snip(P, 'delete-product', 'error-responses.adoc') and 'Error Responses' in read(f'build/api-docs/{P}.html') and '상품을 찾을 수 없습니다' in read(f'build/api-docs/{P}.html'))
    check('WebFlux OpenAPI: Enum 쿼리 파라미터 (ofType 없이 핸들러의 ProductStatus)', re.search(r'- name: status\n\s+in: query\n(?:.*\n){0,5}?\s+enum:\n\s+- SALE', y) is not None)
    wid = re.search(r'\n  /api/v1/products/\{id\}:\n    get:\n(?:      .*\n)*?      - name: id\n        in: path\n((?:        .*\n)*)', y)
    check('WebFlux OpenAPI: 경로 변수 타입은 핸들러에서 (ofType 없이 Long → integer/int64)', wid is not None and 'type: integer\n          format: int64' in wid.group(1), wid.group(1) if wid else '')
    check('WebFlux OpenAPI: Bearer 보안 스키마, 토큰 없음', 'scheme: bearer' in y and 'real-token' not in y)
    check('WebFlux OpenAPI: @Masked → x-masked', 'x-masked: true' in y and 'x-masked-rule: 가운데 자리를 *' in y)
    # Kotest 스펙 (kotest/ProductSummaryDocsSpec.kt): WebTestClient 로 문서화한 Kotest 문서가 JUnit 문서와 나란히 들어간다
    K = 'product-summary-docs-spec'
    KS = '상품-요약-상품-하나의-이름-가격-상태를-돌려준다'
    check('WebFlux Kotest: context/test 이름 스니펫 디렉터리, pathParameters<T>() 경로 파라미터', '`+id+`' in snip(K, KS, 'path-parameters.adoc') and paths(snip(K, KS, 'response-fields.adoc')) == ['id', 'name', 'price', 'status'])
    kd = read(A + K + '.adoc')
    check('WebFlux Kotest: 문서 제목(@DisplayName)·섹션(context - test)', kd.startswith('= 상품 요약 API (Kotest)\n') and f'[[{KS}]]\n== icon:link[] 상품 요약 - 상품 하나의 이름·가격·상태를 돌려준다' in kd)
    check('WebFlux Kotest: index 항목과 섹션 링크', f'[[{K}]]\n=== 상품 요약 API (Kotest)' in idx and f'link:./{K}.html#{KS}[' in idx)
    check('WebFlux Kotest HTML', f'id="{KS}"' in read(f'build/api-docs/{K}.html') and f'href="./{K}.html"' in read('build/api-docs/index.html'))
    check_coverage()
    ks = y[y.index('\n  /api/v1/products/{id}/summary:\n'):]
    ks = re.split(r'\n  /|\n[a-z]', ks[2:], maxsplit=1)[0]
    check('WebFlux Kotest OpenAPI: 태그·요약·operationId(핸들러 메서드 이름)·정수 경로 파라미터·스키마', '- 상품 요약 API (Kotest)' in ks and 'summary: 상품 요약 - 상품 하나의' in ks and 'operationId: summary\n' in ks and 'format: int64' in ks and '#/components/schemas/ProductSummary' in ks, ks[:400])
    wids = re.findall(r'operationId: (\S+)', y)
    check('WebFlux OpenAPI: operationId 는 핸들러 메서드 이름 (bindToController + recordHandlers 포함), 중복 없음', sorted(wids) == ['create', 'delete', 'events', 'get', 'list', 'summary'], ','.join(wids))
    aa = read('build/api-docs/asyncapi.yaml')
    check('WebFlux AsyncAPI: 3.0 문서 + SSE 메시지', aa.startswith('asyncapi: "3.0.0"') and all(f'\n    {n}:\n' in aa for n in ['price-changed', 'sold-out', 'restocked']))
    check('WebFlux AsyncAPI: SSE 구독 요청 헤더', 'x-request-headers:' in aa and 'Last-Event-ID' in aa and 'x-protocol: "SSE"' in aa)
    check_async_api_auth(aa)
    check('WebFlux SSE 인증: access_token 쿼리 문서화 (OpenAPI)', re.search(r'- name: access_token\n\s+in: query', y) is not None)
    check_query_token_masked('WebFlux 쿼리 자격 증명 마스킹', 'product-event-api-docs-test', 'subscribe', wiremock=os.path.isdir(os.path.join(root, S, 'wiremock')))
    check_viewers_without_comments(['build/api-docs/asyncapi.html'])
    h = read(f'build/api-docs/{P}.html')
    check('WebFlux HTML 변환', 'Response Fields' in h and 'sellerPhone' in h and 'Unresolved directive' not in h)
    check('WebFlux HTML index 링크', len(set(re.findall(r'href="\./([a-z-]+-test)\.html"', read('build/api-docs/index.html')))) == 2)
    check('WebFlux HTML: 하위 문서 목차 안에 index 링크', toc_index_link(h) == 'index.html', str(toc_index_link(h)))
    check('WebFlux HTML: Enum 코드 문서 목차 안에 상위 index 링크', toc_index_link(read('build/api-docs/code/enum-code.html')) == '../index.html')
    check('WebFlux HTML: index 문서 자신에는 목차 링크 없음', toc_index_link(read('build/api-docs/index.html')) is None)
    check_language([
        ('build/api-docs/index.html', '문서 열기', 'Open document'),  # core index
        (A + P + '.adoc', '메인으로 돌아가기', 'Back to index'),  # core 하위 문서 링크
        ('build/api-docs/asyncapi.yaml', '구독 요청 헤더', 'Subscription request headers'),  # core AsyncAPI
        ('build/api-docs/asyncapi.html', '<html lang="ko"', '<html lang="en"'),  # core AsyncAPI 뷰어
    ])
    report()

# 1. 자동 추출: 중첩·enum·nullable·BigDecimal·LocalDateTime
rf = snip('product-api-docs-test','get-product','response-fields.adoc')
ps = paths(rf)
check('자동 추출 경로', ps == ['code','message','data','data.id','data.name','data.price','data.status','data.tags','data.options','data.options[].name','data.options[].additionalPrice','data.category','data.category.id','data.category.name','data.createdAt'], ','.join(ps))
check('nullable 객체 optional 전파', row(rf,'data.category').split('\n')[2] == '|false' and row(rf,'data.category.name').split('\n')[2] == '|false')
check('enum 상수 설명', 'SALE : 판매중' in rf and 'SOLD_OUT : 품절' in rf)
check('BigDecimal → Number, LocalDateTime → String', '`+Number+`' in row(rf,'data.price') and '`+String+`' in row(rf,'data.createdAt'))
# 2. 제약조건 컬럼
cf = snip('product-api-docs-test','create-product','request-fields.adoc')
check('제약조건: 기본 메시지', 'NotBlank - Must not be blank' in row(cf,'name') and 'Size - Size must be between 0 and 50 inclusive' in row(cf,'name'))
check('제약조건: 사용자 메시지 치환', 'Size - 태그는 최대 5개' in row(cf,'tags'))
check('제약조건: DecimalMin', 'DecimalMin' in row(cf,'price'))
check('기본값 있는 요청 필드 optional', '|false' in row(cf,'tags') and '|false' in row(cf,'categoryId') and '|true' in row(cf,'name'))
# 3. 파라미터 추출 + 기본값
qp = snip('product-api-docs-test','search-products','query-parameters.adoc')
check('검색 조건 → 쿼리 파라미터', all(f'`+{p}+`' in qp for p in ['keyword','status','minPrice','page','size']))
check('기본값 파라미터 optional', '|false' in row(qp,'page') and '|false' in row(qp,'size'))
# 4. 페이지 중간 래퍼
sf = snip('product-api-docs-test','search-products','response-fields.adoc')
check('페이지 래퍼 경로', all(f'`+{p}+`' in sf for p in ['data.total','data.content','data.content[].id']))
# 5. 배열 데이터 / nullable 데이터
af = snip('product-api-docs-test','array-data','response-fields.adoc')
check('배열 data 래퍼', '`+data[].id+`' in af and '`+Array+`' in row(af,'data'))
nf = snip('product-api-docs-test','nullable-data','response-fields.adoc')
check('dataOptional', '|false' in row(nf,'data') and '|false' in row(nf,'data.id'))
# 6. 헤더 마스킹 + api-summary + 응답 헤더 + Location
hr = snip('product-api-docs-test','get-product','http-request.adoc') + snip('product-api-docs-test','get-product','curl-request.adoc')
check('Authorization 마스킹 (방식은 남김)', 'Authorization: Bearer ****' in hr and 'real-token' not in hr)
summ = snip('product-api-docs-test','get-product','api-summary.adoc')
check('API 요약 (메서드+URL 템플릿+설명)', '`GET /api/v1/products/{id}`' in summ and 'ETag' in summ)
check('응답 헤더', '`+ETag+`' in snip('product-api-docs-test','get-product','response-headers.adoc'))
check('201 Location 헤더', '`+Location+`' in snip('product-api-docs-test','create-product','response-headers.adoc'))
# 7. 멀티파트
check('요청 파트', '`+file+`' in snip('product-api-docs-test','upload-image','request-parts.adoc'))
pf = snip('product-api-docs-test','upload-image','request-part-meta-fields.adoc')
check('파트 본문 필드 + 제약', '`+alt+`' in pf and 'NotBlank' in pf)
# 8. HAL 링크 + subsection
check('HAL 링크', all(f'`+{r}+`' in snip('product-api-docs-test','product-resource','links.adoc') for r in ['self','images']))
# 9. 폼 + 쿠키 마스킹
check('폼 파라미터', '`+username+`' in snip('auth-api-docs-test','login','form-parameters.adoc'))
lr = snip('auth-api-docs-test','login','http-response.adoc')
check('Set-Cookie 값 마스킹, 이름·속성 유지', 'SESSION=****; Path=/; HttpOnly' in lr and 's3cr3t' not in lr)
check('응답 쿠키 문서', '`+SESSION+`' in snip('auth-api-docs-test','login','response-cookies.adoc'))
mr = snip('auth-api-docs-test','me','curl-request.adoc') + snip('auth-api-docs-test','me','http-request.adoc')
check('요청 쿠키 마스킹 (curl --cookie 포함)', 'SESSION=****' in mr and 's3cr3t' not in mr)
# 10. SSE
se = snip('notification-api-docs-test','subscribe','sse-events.adoc')
check('SSE 이벤트 표 (optional 포함)', all(f'`+{e}+`' in se for e in ['connected','notification','maintenance']) and '|false' in row(se,'maintenance'))
ne = snip('notification-api-docs-test','subscribe','response-fields-event-notification.adoc')
check('SSE 이벤트 data 필드', all(f'`+{p}+`' in ne for p in ['id','type','title','productId']) and 'PRICE_DROP' in ne)
check('스트림에 없는 optional 이벤트는 필드 표 없음', not os.path.exists(os.path.join(root, S, 'notification-api-docs-test/subscribe/response-fields-event-maintenance.adoc')))
# 11. STOMP
m0 = snip('chat-message-docs-test','chat','message-01.adoc')
m1 = snip('chat-message-docs-test','chat','message-02.adoc'); m2 = snip('chat-message-docs-test','chat','message-03.adoc')
check('STOMP CONNECT 헤더 문서 (인증 헤더 마스킹)', '`CONNECT /ws`' in m0 and '`+X-Client-Version+`' in m0 and '`+Bearer ****+`' in m0 and 'Bearer demo-token' not in m0, m0[:200])
check('STOMP SEND 요약·헤더·페이로드', '`SEND /app/chat.send` (STOMP)' in m1 and '`+content-type+`' in m1 and '"content" : "안녕하세요"' in m1)
check('STOMP SUBSCRIBE (실제 수신 프레임)', '`SUBSCRIBE /topic/rooms.{roomId}` (STOMP)' in m2 and '"sender" : "hong"' in m2)
check('STOMP 페이로드 필드 + 제약', 'NotBlank' in snip('chat-message-docs-test','chat','request-fields-message-02.adoc'))
# 12. WebSocket 핸들러
e1 = snip('echo-web-socket-docs-test','echo','message-01.adoc'); e3 = snip('echo-web-socket-docs-test','echo','message-03.adoc')
check('WebSocket 핸들러: 연결 메시지(세션 속성 반영)', '(WebSocket)' in e1 and '"user" : "hong"' in e1)
check('WebSocket 핸들러: 응답 메시지', '"upper" : "HELLO"' in e3)
# 13. 직접 기술 DSL
hw = snip('dsl-showcase-docs-test','hand-written','response-fields.adoc')
check('직접 기술: example / default / constraint', 'Example: `+1+`' in snip('dsl-showcase-docs-test','hand-written','path-parameters.adoc') and 'Default: `+0+`' in hw and '1~50자' in hw)
check('직접 기술: isIgnored 필드는 문서에서 제외', 'createdAt' not in hw)
rl = snip('dsl-showcase-docs-test','relaxed-fields','response-fields.adoc')
check('relaxed: 일부 필드만', paths(rl) == ['data.id','data.name','data.price'], ','.join(paths(rl)))
gt = snip('dsl-showcase-docs-test','generic-type','response-fields.adoc')
check('제네릭 추출 (타입 인자 치환)', '`+data.content[].status+`' in gt)
# 14. 커스텀 설명 + Java record
cp = snip('coupon-api-docs-test','get-coupon','response-fields.adoc')
check('descriptionResolver 교체 + Java record', all(s in cp for s in ['쿠폰 코드','할인율 (%)','`+data.discountRate+`','`+Number+`']))
# 15. 문서 조립
idx = read(INDEX)
links = re.findall(r'\[\[([a-z-]+-test)]]', idx)
DOC_TESTS = 13 if BOOT4 else 11  # Boot 4 는 RestTestClient(coupon-list-docs-test)·API 버전(shipping-fee-docs-test) 예제가 더 있다
check(f'index 링크 (문서화 테스트 {DOC_TESTS}개, @DisplayName 순)', len(links) == DOC_TESTS and links[0] == 'payment-api-docs-test' and links[-1] == ('coupon-list-docs-test' if BOOT4 else 'coupon-api-docs-test') and 'extraction-contract-test' not in idx, ','.join(links))
pd = read(A+'product-api-docs-test.adoc')
check('문서 생성기 커스터마이징 (toclevels 2)', ':toclevels: 2' in pd)
check('공통 include (landing)', 'include::_landing.adoc[]' in pd)
ud = read(A+'user-controller-test.adoc')
check('@AsciiDescription 괄호', '= 사용자 API (회원 관리)' in ud)
check('문서화 안 한 테스트 섹션 제외', 'create-user-with-blank-nickname' not in ud)
cd = read(A+'chat-message-docs-test.adoc')
check('메시지 스니펫 순번별 묶음', cd.index('message-01.adoc') < cd.index('message-02.adoc') < cd.index('request-fields-message-02') < cd.index('message-03.adoc'))
ec = read(A+'code/enum-code.adoc')
check('Enum 코드 문서 (@Deprecated 제외 없음 확인용 전체 enum)', all(s in ec for s in ['상품 상태','알림 종류','사용자 상태','| 판매중','| SALE']))
check('Enum 코드 문서: Code | Description 머리글', ec.count('| Code | Description') == 3 and '| Name | Code' not in ec)
check('Enum 코드 문서: 기본값 (코드 = JSON 값, 설명 = description)', '| SALE\n| 판매중' in ec and '| PRICE_DROP\n| 관심 상품 가격 인하' in ec)
check('Enum 코드 문서: descriptionProperty 직접 지정', '| ACTIVE\n| 활성' in ec)
# 16. OpenAPI
y = read('build/api-docs/openapi3.yaml')
check('OpenAPI: deprecated', re.search(r'delete:\n(?:.*\n){0,60}?\s+deprecated: true', y) is not None)
check('OpenAPI: openApi { excluded } 제외', '/recommendation' not in y)
check('OpenAPI: 계약·쇼케이스 제외', '/contract/' not in y and '/wrapped/' not in y)
check('OpenAPI: 스키마 이름', 'CreateProductRequest:' in y and 'ProductResponse:' in y)
def responses(path, method):
    seg = y[y.index('\n  ' + path + ':\n'):]
    seg = seg[:seg.find('\n  /', 5)] if seg.find('\n  /', 5) > 0 else seg
    seg = seg[seg.index('\n    ' + method + ':'):]
    nxt = re.search(r'\n    (get|post|put|delete|patch):', seg[5:])
    return set(re.findall(r'\n        "(\d{3})":', seg[:nxt.start() + 5] if nxt else seg))
# Boot 4 는 공통 오류 응답(401: 인증이 필요한 operation 만, 500: 모두)이 더해진다
COMMON = {'401', '500'} if BOOT4 else set()
check('OpenAPI: 한 operation 에 성공·오류 응답 (GET 200+404)', responses('/api/v1/products/{id}', 'get') == {'200', '404'} | COMMON, str(responses('/api/v1/products/{id}', 'get')))
check('OpenAPI: 한 operation 에 성공·오류 응답 (POST 201+400, 선언한 409)', responses('/api/v1/products', 'post') == {'201', '400', '409'} | (COMMON - {'401'}), str(responses('/api/v1/products', 'post')))
# 선언한 오류 응답 (errorResponse): 테스트가 만들지 않은 상태가 OpenAPI·문서에 들어간다. 실제 응답 > 선언 > 공통 응답
pl = y[y.index('\n  /api/v1/products:\n'):]
pl = pl[:pl.find('\n  /', 5)]
check('OpenAPI 선언 오류 응답: 상태·설명만 (400, 본문은 공통 오류 스키마)', re.search(r'"400":\n\s+description: "검색 조건 형식이 잘못됨 \(예: `minPrice` 가 숫자가 아님\)"\n\s+content:\n\s+application/json:\n\s+schema:\n\s+\$ref: "#/components/schemas/ErrorResponse"', pl) is not None, pl[pl.find('"400"'):][:300])
check('OpenAPI 선언 오류 응답: 필드 + Content-Type (409 application/problem+json → 같은 모양의 ErrorResponse)', re.search(r'"409":\n\s+description: 같은 이름의 상품이 이미 있음\n\s+content:\n\s+application/problem\+json:\n\s+schema:\n\s+\$ref: "#/components/schemas/ErrorResponse"', pl) is not None)
ul = y[y.index('\n  /api/v1/users/{id}:\n'):]
ul = ul[:ul.find('\n  /', 5)]
check('OpenAPI 선언 오류 응답: 예시 객체 한 줄 (404 + example)', re.search(r'"404":\n\s+description: 사용자가 없음\n(?:.*\n){1,4}?\s+\$ref: "#/components/schemas/ErrorResponse"\n\s+example:\n\s+code: 404\n\s+message: 사용자를 찾을 수 없습니다', ul) is not None, ul[ul.find('"404"'):][:300])
check('OpenAPI 선언 오류 응답: 같은 모양의 스키마를 새로 만들지 않음 (ConflictResponse 없음)', 'ConflictResponse' not in y and 'NotFoundResponse' not in y)
if BOOT4:
    check('OpenAPI 선언 오류 응답이 공통 응답보다 우선 (getUser 404 는 선언, 401·500 은 공통)', '"404":\n          description: 사용자가 없음' in ul and 'description: 서버 오류' in ul)
er = snip('product-api-docs-test', 'create-product', 'error-responses.adoc')
check('선언 오류 응답 스니펫 (error-responses: 상태·설명 표 + 필드 표)', '|`409 Conflict`\n|같은 이름의 상품이 이미 있음' in er and '.409 Conflict' in er and '`+errors[].reason+`' in er, er[:200])
check('선언 오류 응답 스니펫: 본문 없는 선언은 표만', '|`400 Bad Request`' in snip('product-api-docs-test', 'search-products', 'error-responses.adoc') and '[source' not in snip('product-api-docs-test', 'search-products', 'error-responses.adoc'))
ph = read('build/api-docs/product-api-docs-test.html'); uh = read('build/api-docs/user-controller-test.html')
check('HTML: error-responses 절 (Error Responses)', 'Error Responses' in ph and '같은 이름의 상품이 이미 있음' in ph and 'Error Responses' in uh and '사용자를 찾을 수 없습니다' in uh)
if BOOT4:
    gp = y[y.index('\n  /api/v1/products/{id}:\n'):]
    gp = gp[:gp.find('\n  /', 5)]
    check('OpenAPI 공통 오류 응답: 테스트 응답은 그대로, 없던 401·500 만 더함 (본문은 ErrorResponse)',
          re.search(r'"401":\n\s+description: 토큰이 없거나 만료됨\n\s+content:\n(?:.*\n){1,2}?\s+schema:\n\s+\$ref: "#/components/schemas/ErrorResponse"', gp) is not None
          and 'description: "Not Found: 상품 조회 - 없는 상품 (404)"' in gp and gp.count('description: 서버 오류') >= 1, gp[:300])
    check('OpenAPI 공통 오류 응답: 401 은 보안 요구사항이 있는 operation 에만', responses('/api/v1/coupons/{code}', 'get') == {'200', '500'}, str(responses('/api/v1/coupons/{code}', 'get')))
    ci = read('build/api-docs/index.html')
    check('HTML index: 공통 오류 응답 표 (앵커 common-error-responses)', 'id="common-error-responses"' in ci and '토큰이 없거나 만료됨' in ci and '서버 오류' in ci)
    check('공통 오류 응답은 클래스별 문서에 없음', '토큰이 없거나 만료됨' not in read('build/api-docs/product-api-docs-test.html'))
else:
    check('공통 오류 응답 없음 (설정하지 않은 샘플)', '서버 오류' not in y and 'common-error-responses' not in read('build/api-docs/index.html'))
# OpenAPI info·태그 (kardx { openApiInfo { } }, Boot 4 만): 연락처·라이선스, tag() 순서가 앞, 나머지는 원래 순서
tags = re.findall(r'^- name: (.+)$', y[y.index('\ntags:\n'):y.index('\npaths:\n')], re.M) if '\ntags:\n' in y else []
if BOOT4:
    check('openApiInfo: info.contact·license', '\n  contact:\n    name: API 팀\n    email: api@example.com\n' in y and '\n  license:\n    name: Apache 2.0\n    url: https://www.apache.org/licenses/LICENSE-2.0\n' in y)
    check('openApiInfo: tag() 순서가 앞 (사용자·상품·결제), 나머지는 원래 순서', tags[:3] == ['사용자 API', '상품 API', '결제 API'] and len(tags) == len(set(tags)) and '메모 API' in tags[3:], ','.join(tags))
    check('openApiInfo: 태그 설명 (빌드 설정 > @AsciiDescription, 정하지 않은 태그는 그대로)', '- name: 상품 API\n  description: 상품 조회·등록·검색\n' in y and '- name: 사용자 API\n  description: 회원 관리\n' in y)
else:
    check('openApiInfo 없음 (설정하지 않은 샘플): contact·license 없음', '\n  contact:\n' not in y and '\n  license:\n' not in y)
# 클래스 KDoc → 컴포넌트 스키마 description (Gradle 플러그인). 자동 이름·직접 정한 이름(openApiErrorSchema) 모두, 이름에 클래스가 없으면 건너뜀
def component_schema(name):
    comp = y[y.index('\ncomponents:\n'):]
    start = comp.find(f'\n    {name}:\n')
    if start < 0: return ''
    m = re.search(r'\n    \S', comp[start + 5:])
    return comp[start:start + 5 + m.start()] if m else comp[start:]
check('스키마 설명: 클래스 KDoc (자동 이름 MemoResponse)', '\n      description: 메모. 프로퍼티 설명은 클래스 KDoc 의 `@property` 에서 온다.' in component_schema('MemoResponse'), component_schema('MemoResponse')[:200])
check('스키마 설명: 직접 정한 이름도 본문 타입의 KDoc (openApiErrorSchema → ErrorResponse)', '\n      description: 모든 오류 응답의 형식.' in component_schema('ErrorResponse'), component_schema('ErrorResponse')[:200])
check('스키마 설명: 이름에 클래스가 없는 직접 정한 이름은 짐작하지 않음 (ProductDetailResponse 에 래퍼 설명 없음)', component_schema('ProductDetailResponse') != '' and '\n      description:' not in component_schema('ProductDetailResponse'), component_schema('ProductDetailResponse')[:200])
ds = snip('product-api-docs-test', 'delete-product', 'api-summary.adoc')
check('deprecated API: 컨트롤러 @Deprecated → api-summary *Deprecated*: 이유 (설명 앞)', '*Deprecated*: 다음 버전의 숨김 처리 API 를 쓰세요\n\n상품을 삭제한다.' in ds, ds)
check('deprecated API: HTML 문서에 표시', 'Deprecated</strong>: 다음 버전의 숨김 처리 API 를 쓰세요' in read('build/api-docs/product-api-docs-test.html'))
check('deprecated 아닌 API 의 api-summary 에는 표시 없음', 'Deprecated' not in snip('product-api-docs-test', 'get-product', 'api-summary.adoc'))
check('OpenAPI: Bearer 보안 스키마 (Swagger Authorize)', 'securitySchemes:' in y and 'scheme: bearer' in y and 'real-token' not in y)
check('OpenAPI: 커스텀 응답 헤더 (X-Request-Id)', 'X-Request-Id:' in y)
check('OpenAPI: 전역 헤더 → apiKey 보안 스키마 (Swagger Authorize)',
      'type: apiKey' in y and 'in: header' in y and 'name: X-Request-Id' in y
      and '- name: X-Request-Id\n        in: header' not in y and 'X-Request-Id: []' in y)
check('OpenAPI: 공통 오류 스키마 (404·400 모두 ErrorResponse)', 'ErrorResponse:' in y and 'ValidationErrorResponse' not in y and y.count('#/components/schemas/ErrorResponse') >= 2)
nf = snip('product-api-docs-test','get-product-not-found','api-summary.adoc')
check('API 요약에 응답 상태', '`GET /api/v1/products/{id}` → `404 Not Found`' in nf, nf)
nfr = snip('product-api-docs-test','get-product-not-found','response-fields.adoc')
check('오류 응답 필드 (ErrorResponse, errors 는 선택)', '`+errors+`' in nfr and '`+message+`' in nfr, nfr)
check('OpenAPI: 필드 설명', '기계식' in y or '상품명' in y)
check('OpenAPI: 추가 태그', '마이페이지' in y)
check('OpenAPI: 상대 서버', re.search(r'servers:\n- url: /\n', y) is not None)
# 17. AsyncAPI
ASYNC = 'async/index' if root.rstrip('/').endswith('spring-boot-4') else 'asyncapi'  # Boot 4 는 asyncApi { fileName } 예시
aa = read(f'build/api-docs/{ASYNC}.yaml')
check('AsyncAPI: STOMP 연결 헤더 (ws 바인딩, 토큰 예시는 가림)', 'bindings:' in aa and 'X-Client-Version' in aa and '- "Bearer ****"' in aa and '- "Bearer demo-token"' not in aa)
check_async_api_auth(aa)
check('SSE 인증: access_token 쿼리 문서화 (OpenAPI)', re.search(r'- name: access_token\n\s+in: query', y) is not None)
check_query_token_masked('쿼리 자격 증명 마스킹', 'notification-api-docs-test', 'subscribe')
check_viewers_without_comments([f'build/api-docs/{ASYNC}.html', 'build/api-docs/openapi.html'])
check('AsyncAPI: SSE 구독 요청 헤더 (x-request-headers)', 'x-request-headers:' in aa and 'Last-Event-ID' in aa)
check('AsyncAPI: 3.0 문서', aa.startswith('asyncapi: "3.0.0"'))
check('AsyncAPI: 메시지 이름', all(f'{n}:' in aa for n in ['chatSend','chatMessage','welcome','echoRequest','echoResponse','connected','notification','maintenance']))
check('AsyncAPI: 방향 (SEND→receive)', re.search(r'receive-chatSend:\n\s+action: "receive"', aa) is not None and re.search(r'send-chatMessage:\n\s+action: "send"', aa) is not None)
check('AsyncAPI: 프로토콜별 서버 연결', '"#/servers/stomp"' in aa and '"#/servers/http"' in aa)
check('AsyncAPI: 실제 페이로드 예시', '"안녕하세요"' in aa and '"HELLO"' in aa)
check('AsyncAPI: 검증용 메시지 제외', '/app/x' not in aa)
viewer = read(f'build/api-docs/{ASYNC}.html')
check('AsyncAPI 뷰어 (스펙 포함 + Try it out)', 'const spec = {"asyncapi":"3.0.0"' in viewer and 'Try it out' in viewer and 'stompjs@7.5.0' in viewer)
# 18. HTML
h = read('build/api-docs/product-api-docs-test.html')
check('HTML 변환 (스니펫 include 해석)', 'Response Fields' in h and 'data.options[].additionalPrice' in h and 'Unresolved directive' not in h)
hi = read('build/api-docs/index.html')
check('HTML index 링크', len(set(re.findall(r'href="\./([a-z-]+-test)\.html"', hi))) == DOC_TESTS)
check('HTML: 하위 문서 목차 안에 index 링크', toc_index_link(h) == 'index.html' and 'Table of Contents' in h, str(toc_index_link(h)))
check('HTML: Enum 코드 문서 목차 안에 상위 index 링크', toc_index_link(read('build/api-docs/code/enum-code.html')) == '../index.html')
check('HTML: index 문서 자신에는 목차 링크 없음', toc_index_link(hi) is None)
check('index 목차 앵커가 서로 다름 (display-none 제거)', 'display-none' not in idx and len(set(links)) == len(links))
check('index 에서 API 섹션으로 바로 가는 링크', 'link:./product-api-docs-test.html#get-product[상품 조회]' in idx)
# 19. 엣지 케이스 (정규식 경로, 같은 이름 스키마, 요청·응답 모양 차이, Spring Data Page, @Nested·@ParameterizedTest·오버로드)
def schema(name):
    m = re.search(r'\n    ' + re.escape(name) + r':\n((?:      .*\n|\n)*)', y)
    return m.group(1) if m else ''
def props(block):
    m = re.search(r'\n      properties:\n((?:        .*\n)*)', '\n' + block)
    return re.findall(r'^        ([A-Za-z_]+):$', m.group(1), re.M) if m else []
check('OpenAPI: 정규식 경로 변수 → /files/{name}', '/api/v1/edge/files/{name}:' in y and '/{name:.+}:' not in y and '- name: name:.+' not in y)
check('OpenAPI: 정규식 경로 파라미터 설명', re.search(r'- name: name\n\s+in: path\n\s+description: 파일 이름 \(점 포함\)', y) is not None)
check('OpenAPI: 같은 이름 다른 패키지 → OrderItem·CartItem', sorted(props(schema('OrderItem'))) == ['id', 'name'] and sorted(props(schema('CartItem'))) == ['code', 'quantity'] and not schema('Item'),
      f"OrderItem={props(schema('OrderItem'))} CartItem={props(schema('CartItem'))}")
check('OpenAPI: 요청·응답 모양 차이 → Account·AccountRequest', sorted(props(schema('Account'))) == ['createdAt', 'username'] and sorted(props(schema('AccountRequest'))) == ['password', 'username'],
      f"Account={props(schema('Account'))} AccountRequest={props(schema('AccountRequest'))}")
check('OpenAPI: 배열 응답은 목록 스키마 이름 (ListUserResponse)', 'ListUserResponse:' in y and 'type: array' in schema('ListUserResponse') and 'id' in props(schema('UserResponse')))
check('OpenAPI: Spring Data Page 응답 스키마', all(p in props(schema('PageArticle')) for p in ['content', 'pageable', 'totalElements', 'totalPages', 'sort']), str(props(schema('PageArticle'))))
pq = snip('edge-case-docs-test', 'articles', 'query-parameters.adoc')
check('Pageable → page·size·sort 파라미터', paths(pq) == ['page', 'size', 'sort'], ','.join(paths(pq)))
pr = snip('edge-case-docs-test', 'articles', 'response-fields.adoc')
check('Page 응답 필드 (content[]·totalElements·pageable.pageNumber)', all(f'`+{p}+`' in pr for p in ['content[].title', 'totalElements', 'pageable.pageNumber', 'sort.sorted']))
check('@ParameterizedTest 실행별 스니펫', all(os.path.isfile(os.path.join(root, f'{S}edge-case-docs-test/order-item/{n}/http-request.adoc')) for n in (1, 2)))
check('오버로드 테스트 스니펫 분리', all(os.path.isfile(os.path.join(root, f'{S}edge-case-docs-test/{m}/http-request.adoc')) for m in ('overloaded', 'overloaded-test-info')))
check('@Nested 스니펫은 바깥 클래스 아래', os.path.isfile(os.path.join(root, f'{S}edge-case-docs-test/files-dotted-name/http-request.adoc')) and not os.path.exists(os.path.join(root, f'{S}files')))
ed = read(A + 'edge-case-docs-test.adoc')
check('엣지 문서: @Nested 섹션 (바깥 문서, 제목 앞 @Nested 이름)', '[[files-dotted-name]]\n== icon:link[] 파일 - 점이 든 파일 이름' in ed and not os.path.exists(os.path.join(root, A + 'files.adoc')))
check('엣지 문서: 파라미터 테스트 실행별 섹션', '[[order-item-1]]\n== icon:link[] 주문 품목 주문 1' in ed and '[[order-item-2]]' in ed and 'order-item/2/http-request.adoc' in ed)
check('엣지 문서: 오버로드 섹션 둘', '[[overloaded]]' in ed and '[[overloaded-test-info]]\n== icon:link[] 계정 만들기 (TestInfo 오버로드)' in ed)

# 20. OpenAPI 출력 품질: 타입·제약·기본값·예시·multipart·쿠키·세부 (라이브러리 속성 + Gradle 플러그인 후처리)
def param(path, method, name, kind):
    """operation 의 파라미터 블록 (다음 파라미터나 operation 키 전까지)."""
    seg = y[y.index('\n  ' + path + ':\n'):]
    seg = seg[seg.index('\n    ' + method + ':'):]
    m = re.search(r'\n      - name: ' + re.escape(name) + r'\n        in: ' + kind + r'\n((?:        .*\n)*)', seg)
    return m.group(1) if m else ''
def prop(schema_block, name):
    """가장 바깥(들여쓰기가 가장 얕은) [name] 속성의 블록."""
    found = re.findall(r'^( +)' + re.escape(name) + r':\n((?:\1  .*\n|\n)*)', schema_block, re.M)
    return min(found, key=lambda f: len(f[0]))[1] if found else ''
check('OpenAPI: 경로 변수 타입은 핸들러에서 (ofType 없이 @PathVariable id: Long → integer/int64)', all('type: integer\n          format: int64' in param('/api/v1/products/{id}', m, 'id', 'path') for m in ('get', 'delete')), param('/api/v1/products/{id}', 'delete', 'id', 'path'))
check('OpenAPI: 쿼리 파라미터 타입은 핸들러에서 (ofType 없이 @RequestParam page: Int → integer/int32)', 'type: integer\n          format: int32' in param('/api/v1/users', 'get', 'page', 'query'), param('/api/v1/users', 'get', 'page', 'query'))
check('OpenAPI: 폼 파라미터 타입은 핸들러에서 (ofType 없이 rememberDays: Int? → integer)', re.search(r'\n        rememberDays:\n          type: integer\n', y) is not None)
check('OpenAPI: 태그·요약은 모든 operation 에 (문서 클래스 제목, 테스트 표시 이름)', len(re.findall(r'^      summary: ', y, re.M)) == len(re.findall(r'\n    (?:get|post|put|patch|delete):\n      tags:\n      - ', y)) == len(re.findall(r'\n    (?:get|post|put|patch|delete):\n', y)))
check('OpenAPI: parametersOf 타입 (Long·Int → integer)', 'format: int64' in param('/api/v1/products', 'get', 'minPrice', 'query') and 'format: int32' in param('/api/v1/products', 'get', 'page', 'query'))
lu = param('/api/v1/users', 'get', 'size', 'query')
check('OpenAPI: withDefaultValue·example → default·example (타입 맞춤)', 'default: 10\n' in lu and 'example: 20\n' in lu and 'default: 0\n' in param('/api/v1/users', 'get', 'page', 'query'), lu)
cpr = schema('CreateProductRequest')
check('OpenAPI: Bean Validation → minLength·maxLength·minimum·maxItems', 'minLength: 1' in prop(cpr, 'name') and 'maxLength: 50' in prop(cpr, 'name') and 'minimum: 0' in prop(cpr, 'price') and 'maxItems: 5' in prop(cpr, 'tags'), cpr)
check('OpenAPI: @Email → format: email', 'format: email' in prop(schema('CreateUserRequest'), 'email'))
check('OpenAPI: LocalDateTime → format: date-time', 'format: date-time' in prop(schema('ProductResponse'), 'createdAt'))
check('OpenAPI: 값 배열 items 는 원소 타입 (oneOf 없음)', 'items:\n            type: string' in prop(cpr, 'tags') and 'oneOf' not in prop(cpr, 'tags'), prop(cpr, 'tags'))
opt = prop(cpr, 'options')
check('OpenAPI: optional 부모의 non-null 하위 필드는 부모 안 required, nullable 아님', 'required:\n' in opt and '- name' in opt and 'nullable' not in prop(opt, 'name') and 'nullable' not in prop(cpr, 'tags'), opt)
check('OpenAPI: Long·Int 필드 → integer + format','type: integer' in prop(cpr, 'categoryId') and 'format: int64' in prop(cpr, 'categoryId'))
check('Enum 필드 표 Type 은 String, 스펙은 enum', '`+String+`' in row(rf, 'data.status') and 'enum:\n' in prop(schema('ProductResponse'), 'status'), row(rf, 'data.status'))
up = y[y.index('\n  /api/v1/products/{id}/images:'):]
up = up[:up.find('\n  /', 5)]
check('OpenAPI: multipart 요청 본문 (파일 binary, JSON 파트 encoding)', 'multipart/form-data:' in up and 'format: binary' in up and 'contentType: application/json' in up and 'alt:' in up, up[:300])
lg = y[y.index('\n  /api/v1/auth/login:'):]
lg = lg[:lg.find('\n  /', 5)]
ref = re.search(r'x-www-form-urlencoded:\n\s+schema:\n\s+\$ref: "#/components/schemas/([^"]+)"', lg)
form = schema(ref.group(1)) if ref else lg
check('OpenAPI: 폼 파라미터 타입 (ofType Int → integer)', re.search(r'rememberDays:\n\s+type: integer\n', form) is not None, form[:600])
check('OpenAPI: 요청 쿠키 → in: cookie', 'in: cookie' in y and '- name: SESSION\n        in: cookie' in y)
check('OpenAPI: 응답 쿠키 → Set-Cookie 설명', '`SESSION`: 세션 ID (HttpOnly)' in y)
check('OpenAPI: 응답 description 은 reason phrase (+오류 테스트 summary)', 'description: "200"' not in y and 'description: OK\n' in y and 'Not Found: 상품 조회 - 없는 상품 (404)' in y)
ids = re.findall(r'operationId: (\S+)', y)
check('OpenAPI: operationId 에 / 없음·중복 없음', ids and all('/' not in i for i in ids) and len(ids) == len(set(ids)), ','.join(i for i in ids if '/' in i))
check('OpenAPI: operationId 는 영문 핸들러 메서드 이름 (getUser), 겹친 것만 컨트롤러 이름을 붙인다 (productGet·couponGet)', all(re.fullmatch(r'[A-Za-z][A-Za-z0-9]*', i) for i in ids) and {'getUser', 'createUser', 'productGet', 'couponGet'} <= set(ids) and 'get' not in ids, ','.join(ids))
check('OpenAPI: 최상위 tags (@DisplayName + @AsciiDescription 설명)', '\ntags: []' not in y and '- name: 사용자 API\n  description: 회원 관리' in y)
check('OpenAPI: JSON 예시는 문자열이 아니라 JSON 값 + summary', re.search(r'value: \|-\n\s+[\{\[]', y) is None and 'summary: 상품 등록 - 잘못된 입력 (400)' in y)
check('OpenAPI: 민감 헤더 예시 없음 (가짜 JWT)', 'eyJhbGciOiJub25l' not in y and 'example: Bearer' not in y)
check('OpenAPI: 보안 스키마가 있으면 공개 operation 은 security: []', 'security: []' in y)
res = json.loads(read(S + 'product-api-docs-test/get-product/resource.json'))
check('OpenAPI 조각: 타입 있는 파라미터는 epages 에도 INTEGER', res['request']['pathParameters'][0]['type'] == 'INTEGER')
check('AsyncAPI: 정수·제약 정밀도 (integer/int64, minLength·maxLength)', 'format: "int64"' in aa and 'minLength: 1' in aa and 'maxLength: 500' in aa and '제약: NotBlank' not in aa and 'Constraints: NotBlank' not in aa)

# 21. 3차: @Deprecated, 섹션 순서, 진입점, 래퍼 뗀 스키마 이름
check('@Deprecated 파라미터 → 표 설명 앞 Deprecated', '*Deprecated*: minPrice 를 쓴다' in row(qp, 'priceFrom'), row(qp, 'priceFrom'))
check('@Deprecated 파라미터 → OpenAPI deprecated: true', 'deprecated: true' in param('/api/v1/products', 'get', 'priceFrom', 'query'), param('/api/v1/products', 'get', 'priceFrom', 'query'))
order = [pd.find(f'[[{a}]]') for a in ['get-product', 'get-product-not-found', 'search-products', 'create-product', 'create-product-invalid', 'upload-image', 'product-resource', 'delete-product', 'array-data', 'nullable-data', 'download-manual']]
check('@TestMethodOrder + @Order 섹션 순서', -1 not in order and order == sorted(order), str(order))
check('MockMvc Kotlin DSL·MockMvcTester 진입점 스니펫', all('`+data.id+`' in snip('dsl-showcase-docs-test', m, 'response-fields.adoc') for m in ('kotlin-dsl', 'mock-mvc-tester')))
check('OpenAPI: 래퍼 뗀 스키마 이름 (ProductResponse, 래퍼 필드 포함)', '\n    ProductResponse:\n      title: ProductResponse\n      required:\n      - code\n      - data\n      - message\n' in y and '\n    RestResponseProductResponse:' not in y)

WM_DIR = S + 'wiremock/mappings/'
# 21-2. 파일 다운로드: 바이너리 본문은 스니펫에 크기만, OpenAPI 는 format: binary 에 예시 없음, WireMock 은 원래 바이트
dm = snip('product-api-docs-test', 'download-manual', 'http-response.adoc')
check('파일 다운로드: 응답 헤더 (Content-Disposition·Content-Type)', 'Content-Disposition: attachment; filename="product-1-manual-ko.pdf"' in dm and 'Content-Type: application/pdf' in dm and '`+Content-Disposition+`' in snip('product-api-docs-test', 'download-manual', 'response-headers.adoc'), dm)
check('파일 다운로드: 바이너리 본문은 <binary: N bytes> (깨진 글자 없음)', '<binary: 45 bytes>' in dm and '\ufffd' not in dm and '%PDF' not in dm and '<binary: 45 bytes>' in snip('product-api-docs-test', 'download-manual', 'response-body.adoc'), dm)
mn = y[y.index('\n  /api/v1/products/{id}/manual:'):]
mn = mn[:mn.find('\n  /', 5)]
check('파일 다운로드 OpenAPI: application/pdf 는 string/binary, 예시 없음, 헤더·쿼리 기본값', 'application/pdf' in mn and 'format: binary' in mn and '<binary:' not in y and 'Content-Disposition:' in mn and 'default: ko' in mn, mn[:600])
# 21-3. 파일 본문 설명 (responseFile): 스니펫 표, 생성 문서·HTML, OpenAPI 설명과 charset 없는 미디어 타입
rf = snip('product-api-docs-test', 'download-manual', 'response-file.adoc')
check('파일 본문 표: 설명·미디어 타입(charset 없음)·파일 이름·크기 (한국어 머리글)', '|설명\n|상품 설명서 PDF\n' in rf and '|미디어 타입\n|`+application/pdf+`\n' in rf and '|파일 이름 (예시)\n|`+product-1-manual-ko.pdf+`\n' in rf and '|크기 (예시)\n|45바이트\n' in rf, rf)
check('파일 본문: response-body 는 언어 없는 [source] 블록 ([source,pdf] 아님)', snip('product-api-docs-test', 'download-manual', 'response-body.adoc').startswith('[source,options="nowrap"]'))
pdoc = read(A + 'product-api-docs-test.adoc')
check('파일 본문 표: 생성 문서에 .Response File 로 include (response-body 대신)', '.Response File\ninclude::{snippets}/product-api-docs-test/download-manual/response-file.adoc[]' in pdoc and 'download-manual/response-body.adoc' not in pdoc)
ph = read('build/api-docs/product-api-docs-test.html')
check('파일 본문 표: HTML 에 설명과 파일 이름', '상품 설명서 PDF' in ph and 'product-1-manual-ko.pdf</code>' in ph and '45바이트' in ph)
check('파일 본문 OpenAPI: 스키마 설명, charset 없는 application/pdf, Content-Disposition 예시', '\n            application/pdf:\n              schema:\n                type: string\n                format: binary\n                description: 상품 설명서 PDF\n' in mn and 'application/pdf;charset' not in mn and 'example: attachment; filename="product-1-manual-ko.pdf"' in mn, mn[:900])
wd = json.loads(read(WM_DIR + 'product-api-docs-test/download-manual.json')) if os.path.isfile(os.path.join(root, WM_DIR, 'product-api-docs-test/download-manual.json')) else {}
check('파일 다운로드 WireMock: 원래 바이트를 base64Body 로 (자리 표시 아님)', wd.get('response', {}).get('base64Body', '').startswith('JVBERi0xLjQK'), str(wd.get('response', {}))[:200])

# 22. 4차-A: 선택 기능 (두 샘플 모두 켬. Boot 4 는 Redoc + 기준 스펙, Boot 3 은 Scalar)
vh = read('build/api-docs/openapi.html')
# 21. 마스킹되는 필드 표시 (@Masked): AsciiDoc 표·OpenAPI 스키마 (문서 표시. 값은 샘플 앱이 가린다)
MASK_RULE = '아이디의 첫 글자와 끝 글자만 남기고 *'
uf = snip('user-controller-test', 'get-user', 'response-fields.adoc')
check('@Masked → 필드 표 *Masked*: 규칙 (설명 앞)', f'*Masked*: {MASK_RULE} +\n사용자 이메일' in row(uf, 'email') and '*Masked*' not in row(uf, 'nickname'), row(uf, 'email'))
ue = prop(schema('UserResponse'), 'email')
check('@Masked → OpenAPI x-masked·x-masked-rule·설명 끝 Masked', 'x-masked: true' in ue and MASK_RULE in ue and 'Masked: ' in ue and 'x-masked' not in prop(schema('UserResponse'), 'nickname'), ue)
mr = snip('user-controller-test', 'create-user', 'http-response.adoc')
check('@Masked 필드의 예시 값 = 샘플 앱이 규칙대로 가린 값 (hong → h**g)', '"email" : "h**g@example.com"' in mr and 'hong@example.com' not in mr, mr)
check('OpenAPI 뷰어: 고정 버전 CDN + SRI', ('redoc@2.5.4' in vh) if BOOT4 else ('@scalar/api-reference@1.76.4' in vh) and "agent: { disabled: true }" in vh, vh[:300])
check('OpenAPI 뷰어: 스펙을 페이지에 넣음 (제목·경로)', 'const spec = {"openapi":"3.0.1"' in vh and '<title>kardx 예제 API</title>' in vh and '/api/v1/products/{id}' in vh)
pm = json.loads(read('build/api-docs/postman-collection.json'))
def pm_headers(n):
    if isinstance(n, dict):
        for h in n.get('header', []) if isinstance(n.get('header'), list) else []: yield h
        for v in n.values(): yield from pm_headers(v)
    elif isinstance(n, list):
        for v in n: yield from pm_headers(v)
hs = list(pm_headers(pm))
check('Postman: Collection v2.1 + 요청 항목', pm['info']['schema'].endswith('/v2.1.0/collection.json') and len(pm['item']) >= 15, str(len(pm['item'])))
check('Postman: 가짜 토큰 → {{bearerToken}} 변수, 헤더마다 value', any(h.get('value') == 'Bearer {{bearerToken}}' for h in hs) and 'eyJhbGciOiJub25l' not in read('build/api-docs/postman-collection.json') and all('value' in h for h in hs) and {'key': 'bearerToken', 'value': '', 'type': 'string'} in pm.get('variable', []))
WM = S + 'wiremock/mappings/'
wm = json.loads(read(WM + 'product-api-docs-test/get-product.json'))
check('WireMock: 매핑 (요청 경로·응답 상태·본문)', wm['request'] == {'method': 'GET', 'urlPath': '/api/v1/products/1'} and wm['response']['status'] == 200 and '기계식 키보드' in wm['response']['body'] and 'Content-Length' not in wm['response'].get('headers', {}))
wc = json.loads(read(WM + 'product-api-docs-test/create-product-invalid.json'))
check('WireMock: JSON 요청 본문은 equalToJson, 400 응답', 'equalToJson' in wc['request']['bodyPatterns'][0] and wc['response']['status'] == 400)
wfiles = [os.path.join(d, f) for d, _, fs in os.walk(os.path.join(root, WM)) for f in fs]
wids = [json.load(open(f, encoding='utf-8'))['id'] for f in wfiles]
check('WireMock: 문서마다 하나, id 중복 없음', len(wfiles) >= 35 and len(wids) == len(set(wids)), str(len(wfiles)))
if BOOT4:
    # RestTestClient (resttestclient/CouponListDocsTest): 실제 서버에 보낸 요청의 스니펫·문서·OpenAPI
    R = 'coupon-list-docs-test'
    rq = snip(R, 'list-coupons', 'http-request.adoc')
    check('RestTestClient: 스니펫 (실제 요청·쿼리 파라미터·래퍼 응답 필드)', 'GET /api/v1/coupons?usableOnly=true' in rq and '`+usableOnly+`' in snip(R, 'list-coupons', 'query-parameters.adoc') and '`+data[].code+`' in snip(R, 'list-coupons', 'response-fields.adoc'), rq[:200])
    rl = y[y.index('\n  /api/v1/coupons:\n'):] if '\n  /api/v1/coupons:\n' in y else ''
    rl = rl[:rl.find('\n  /', 5)]
    check('RestTestClient: OpenAPI operation (태그·요약·operationId·쿼리 파라미터·예시)', '- 쿠폰 목록 API (RestTestClient)' in rl and 'summary: 쿠폰 목록' in rl and 'operationId: listCoupons\n' in rl and '- name: usableOnly' in rl and 'coupon-list-docs-test/list-coupons:' in rl, rl[:400])
    check('RestTestClient: HTML 문서', 'usableOnly' in read(f'build/api-docs/{R}.html') and f'href="./{R}.html"' in read('build/api-docs/index.html'))
    check('OpenAPI 기준 스펙 (apiDocsCheck)', os.path.isfile(os.path.join(root, 'docs/openapi/openapi3.yaml')) and '/api/v1/products/{id}:' in read('docs/openapi/openapi3.yaml'))

# 23. Kotest 스펙 문서 (samples/*/src/test/.../kotest/UserSearchDocsSpec.kt): JUnit 테스트 문서와 나란히 index·HTML·OpenAPI 에 들어간다
K = 'user-search-docs-spec'
KS = '가입한-사용자가-있을-때-닉네임으로-검색하면-그-사용자-목록을-돌려준다'
KE = '가입한-사용자가-있을-때-다른-사용자의-닉네임으로-검색하면-그-사용자만-돌려준다'
check('Kotest: given/when/then 이름 스니펫 디렉터리 (스타일 접두사 없이)', '`+nickname+`' in snip(K, KS, 'query-parameters.adoc') and os.path.isfile(os.path.join(root, S, K, KE, 'http-response.adoc')))
kd = read(A + K + '.adoc')
check('Kotest: 문서 제목 = 스펙 @DisplayName + @AsciiDescription, 섹션 = given - when - then', kd.startswith('= 사용자 검색 API (Kotest) (Kotest BehaviorSpec 으로 문서화)\n') and f'[[{KS}]]\n== icon:link[] 가입한 사용자가 있을 때 - 닉네임으로 검색하면 - 그 사용자 목록을 돌려준다' in kd and kd.index(KS) < kd.index(KE))
check('Kotest: yml 공통 include (Kotest Spring 확장의 컨텍스트에서 읽음)', 'include::_landing.adoc[]' in kd)
check('Kotest: index 항목과 섹션 링크', f'[[{K}]]\n=== 사용자 검색 API (Kotest)' in idx and f'link:./{K}.html#{KS}[' in idx)
kh = read(f'build/api-docs/{K}.html')
check('Kotest HTML: 문서와 섹션 앵커', '사용자 검색 API (Kotest)' in kh and f'id="{KS}"' in kh and f'href="./{K}.html"' in read('build/api-docs/index.html'))
ks = y[y.index('\n  /api/v1/users/search:\n'):]
ks = ks[:ks.index('\n  /', 2)]
check('Kotest OpenAPI: 태그(스펙 제목)·요약(섹션 제목)·openApi { operationId } 로 정한 id·JUnit 과 같은 스키마', '- 사용자 검색 API (Kotest)' in ks and 'summary: 가입한 사용자가 있을 때 - ' in ks and 'operationId: searchUsersByNickname\n' in ks and '#/components/schemas/ListUserResponse' in ks, ks[:400])
check('Kotest OpenAPI: 태그 설명 (@AsciiDescription)', '- name: 사용자 검색 API (Kotest)\n  description: Kotest BehaviorSpec 으로 문서화' in y)

# 25. 5차: 문서화 커버리지, sealed 타입 → oneOf, Kotlin 기본값 → default, OAuth2, API 버전, IntelliJ HTTP Client
check_coverage()
PAY = 'payment-api-docs-test'
pq = re.search(r'\n    PaymentRequest:\n((?:      .*\n)+)', y)
pq = pq.group(1) if pq else ''
check('oneOf: sealed 결제 수단 → oneOf + discriminator(type) + mapping', re.search(r'        method:\n          oneOf:\n          - \$ref: "#/components/schemas/CardPayment"\n          - \$ref: "#/components/schemas/BankTransfer"\n          discriminator:\n            propertyName: type\n            mapping:\n              CARD: "#/components/schemas/CardPayment"\n              BANK_TRANSFER: "#/components/schemas/BankTransfer"\n', pq) is not None, pq[:600])
cp = re.search(r'\n    CardPayment:\n((?:      .*\n)+)', y)
cp = cp.group(1) if cp else ''
check('oneOf: 하위 타입 스키마의 판별자 프로퍼티 (type enum CARD, required)', re.search(r'        type:\n          type: string\n          enum:\n          - CARD\n', cp) is not None and '      - type\n' in cp, cp[:400])
pr = re.search(r'\n    PaymentResponse:\n((?:      .*\n)+)', y)
check('oneOf: 공통 래퍼 안의 다형 프로퍼티 (data.method → oneOf + discriminator)', pr is not None and re.search(r'\n        data:\n(?:          .*\n)*?            method:\n              oneOf:\n              - \$ref: "#/components/schemas/\w*CardPayment"\n              - \$ref: "#/components/schemas/BankTransfer"\n              discriminator:\n                propertyName: type\n', pr.group(1)) is not None, pr.group(1)[:600] if pr else '')
check('Kotlin 기본값: 요청 본문 필드 default (currency = "KRW", 객체 파라미터가 있는 클래스)', re.search(r'        currency:\n          type: string\n          description: .*\n          default: KRW\n', pq) is not None, pq[-300:])
check('Kotlin 기본값: 요청 필드 표 (Default: KRW)', 'Default: `+KRW+`' in row(snip(PAY, 'pay-by-card', 'request-fields.adoc'), 'currency'))
check('Kotlin 기본값: 쿼리 파라미터 default (상품 검색 size = 20)', re.search(r'- name: size\n        in: query\n(?:        .*\n){0,6}?          default: 20\n', y) is not None)
check('OAuth2: components.securitySchemes.oauth2 (clientCredentials + scope 설명)', re.search(r'\n    oauth2:\n      type: oauth2\n      flows:\n        clientCredentials:\n          tokenUrl: https://auth.example.com/oauth/token\n          scopes:\n            payments:write: 결제 승인\n', y) is not None)
po = y[y.index('\n  /api/v1/payments:\n'):]
po = po[:po.find('\n  /', 5)]
check('openApi { summary }: 합친 operation 의 summary 는 테스트 이름이 아니라 정한 값 (결제 요청)', '      summary: 결제 요청\n' in po and '      summary: 계좌이체 결제\n      description' not in po, po[:300])
check('OAuth2: operation security (oauth2 [payments:write])', '      security:\n      - oauth2:\n        - payments:write' in po, po[-200:])
check('OAuth2: Bearer scheme 과 함께 (bearerAuthJWT 유지)', 'bearerAuthJWT:' in y)
if BOOT4:
    sf = y[y.index('\n  /api/shipping-fees:\n'):]
    sf = sf[:sf.find('\n  /', 5)]
    check('API 버전: x-api-version (가장 높은 버전) + 필수 헤더 X-API-Version enum', 'x-api-version: "1.1"' in sf and re.search(r'- name: X-API-Version\n        in: header\n(?:        .*\n)*?        required: true\n        schema:\n          type: string\n          enum:\n          - "1.1"\n', sf) is not None, sf[:600])
    for v in ['1.0', '1.1']:
        vy = read(f'build/api-docs/openapi3-v{v}.yaml')
        vs = vy[vy.index('\n  /api/shipping-fees:\n'):]
        vs = vs[:vs.find('\n  /', 5)]
        check(f'API 버전: 버전별 스펙 openapi3-v{v}.yaml (그 버전 + 버전 없는 operation)', f'x-api-version: "{v}"' in vs and f'- "{v}"' in vs and '\n  /api/v1/products:\n' in vy)
    check('API 버전: 커버리지 항목에 버전', '| GET | `/api/shipping-fees`' not in read('build/api-docs/coverage/coverage.md') and '"apiVersion": "1.0"' in read('build/api-docs/coverage/coverage.json'))
    http = read('build/api-docs/http-client/api.http')
    env = json.loads(read('build/api-docs/http-client/http-client.env.json'))
    check('HTTP Client: api.http (### 제목, # @name operationId, {{baseUrl}})', '### 상품 조회\n# @name ' in http and http.count('# @name ') == http.count('\n###') + (1 if http.startswith('###') else 0) and 'GET {{baseUrl}}/api/v1/products/1' in http, http[:300])
    check('HTTP Client: Bearer 토큰 → {{bearerToken}}, 환경 파일 bearerToken', 'Authorization: Bearer {{bearerToken}}' in http and 'real-token' not in http and env.get('local', {}).get('bearerToken') == '' and env['local'].get('baseUrl', '').startswith('http://localhost'), str(env))
    check('HTTP Client: 요청 제목은 OpenAPI summary (### 결제 요청)', '### 결제 요청\n' in http and '### 계좌이체 결제\n' not in http)
    check('HTTP Client: 결제 요청 본문 (판별자 type 포함)', '"type" : "CARD"' in http or '"type" : "BANK_TRANSFER"' in http)

# 25. KDoc/Javadoc → 설명 (apiDocsSourceDocs 색인 → 필드 표·OpenAPI 스키마·operation summary/description)
SOURCE_DOCS = 'build/kardx/source-docs/source-docs.json'
check('KDoc: source-docs.json 생성', os.path.isfile(os.path.join(root, SOURCE_DOCS)))
sd = json.loads(read(SOURCE_DOCS)) if os.path.isfile(os.path.join(root, SOURCE_DOCS)) else {'types': {}, 'methods': {}}
sdt = sd.get('types', {})
check('KDoc 색인: 프로퍼티 KDoc·클래스 @property·record @param·enum 상수·메서드',
      sdt.get('com.example.restdocs.dto.UserResponse', {}).get('properties', {}).get('nickname') == '사용자 닉네임'
      and sdt.get('com.example.restdocs.dto.CreateUserRequest', {}).get('properties', {}).get('email') == '사용자 이메일'
      and sdt.get('com.example.restdocs.coupon.CouponResponse', {}).get('properties', {}).get('usable') == '사용 가능 여부'
      and sdt.get('com.example.restdocs.notification.NotificationType', {}).get('enumConstants', {}).get('ORDER') == '주문 상태 변경'
      and sd.get('methods', {}).get('com.example.restdocs.controller.UserController#searchUsers', {}).get('summary') == '닉네임으로 사용자 검색')
MAIN = 'src/main/kotlin/com/example/restdocs/'
check('KDoc 샘플: 바꾼 필드에 @Description 없음 (설명은 KDoc 에서만)',
      '@Description("사용자 닉네임")' not in read(MAIN + 'dto/UserResponse.kt')
      and '@Description' not in read(MAIN + 'dto/CreateUserRequest.kt')
      and '@ApiField("사용 가능 여부")' not in read('src/main/java/com/example/restdocs/coupon/CouponResponse.java'))
gu = snip('user-controller-test', 'get-user', 'response-fields.adoc')
check('KDoc 필드 → 필드 표 (UserResponse.nickname: 프로퍼티 KDoc)', '사용자 닉네임' in row(gu, 'nickname'), row(gu, 'nickname'))
check('KDoc 필드 → HTML 필드 표', re.search(r'<code>nickname</code>.*?사용자 닉네임', read('build/api-docs/user-controller-test.html'), re.S) is not None)
cu = snip('user-controller-test', 'create-user', 'request-fields.adoc')
check('KDoc 필드 → 필드 표 (CreateUserRequest.email: 클래스 @property)', '사용자 이메일' in row(cu, 'email'), row(cu, 'email'))
check('KDoc 필드 → OpenAPI 스키마 (UserResponse.nickname, CreateUserRequest.email, CouponResponse.usable)',
      'description: 사용자 닉네임' in prop(schema('UserResponse'), 'nickname')
      and 'description: 사용자 이메일' in prop(schema('CreateUserRequest'), 'email')
      and 'description: 사용 가능 여부' in prop(schema('CouponResponse'), 'usable'),
      prop(schema('UserResponse'), 'nickname') + prop(schema('CouponResponse'), 'usable'))
check('KDoc enum 상수 → Enum 코드 문서 (description 프로퍼티 없는 NotificationType)', '| ORDER\n| 주문 상태 변경' in ec)
def operation(path, method):
    seg = y[y.index('\n  ' + path + ':\n'):]
    seg = seg[seg.index('\n    ' + method + ':'):]
    nxt = re.search(r'\n    (?:get|post|put|delete|patch):|\n  /', seg[5:])
    return seg[:nxt.start() + 5] if nxt else seg
su = operation('/api/v1/users/search', 'get')
check('KDoc 메서드 → OpenAPI summary·description (searchUsers, 테스트 이름 대신)',
      '      summary: 닉네임으로 사용자 검색\n' in su and 'description: 닉네임으로 사용자 검색. 닉네임이 같은 사용자를 모두 돌려준다.' in su, su[:300])
lu = operation('/api/v1/users', 'get')
check('KDoc 메서드 → OpenAPI description (listUsers, [page] 링크 → page)',
      '      summary: 사용자 목록 조회\n' in lu and 'description: 사용자 목록 조회. 가입한 사용자를 page·size 로 나눠 돌려준다.' in lu, lu[:300])
pp = operation('/api/v1/payments', 'post')
check('openApi { summary } 가 메서드 KDoc 보다 먼저 (summary 결제 요청, description 은 KDoc)',
      '      summary: 결제 요청\n' in pp and 'summary: 주문 결제\n' not in pp and 'description: 주문 결제. 주문 금액을 결제한다.' in pp, pp[:300])
check('일반 주석(//)은 summary 가 되지 않음 (login 은 테스트 이름)', '      summary: 로그인\n' in operation('/api/v1/auth/login', 'post'))

# 26. KDoc 만으로 만든 문서 (memo 패키지 + MemoApiDocsTest): 설명 어노테이션도, 테스트에 적은 설명도 없이 모든 칸이 KDoc 에서 온다
MEMO = 'memo-api-docs-test'
MEMO_MAIN = MAIN + 'memo/'
MEMO_TEST = 'src/test/kotlin/com/example/restdocs/memo/MemoApiDocsTest.kt'
# 주석(//, KDoc 의 * 줄)은 빼고 코드만 본다. 컨트롤러 머리 주석이 '@Description 같은 어노테이션이 없다' 고 설명한다
memo_code = '\n'.join(line for f in sorted(os.listdir(os.path.join(root, MEMO_MAIN))) for line in read(MEMO_MAIN + f).split('\n')
                       if not line.strip().startswith(('//', '*', '/**')))
check('KDoc 전용: memo 패키지에 설명 어노테이션 없음 (@Description·@Schema·@JsonPropertyDescription·@AsciiDescription)',
      re.search(r'@(?:\w+:)?(?:Description|Schema|JsonPropertyDescription|AsciiDescription)\b', memo_code) is None, memo_code[:200])
mt = read(MEMO_TEST)
# 'Means FromParamTag' 는 설명을 적지 않는다 (@param 에서 가져온다). 'Means "..."' 만 테스트에 적은 설명이다
check('KDoc 전용: 테스트에 설명 없음 (description =·summary =·Means "…"·openApi { })',
      not any(a in mt for a in ['description =', 'summary =', 'Means "', 'openApi {', 'fieldWithPath']), mt[:200])
def cell(table_row, index):
    """표 한 행(rows 의 원소)에서 index 번째 칸의 글 (여러 줄 칸 포함)."""
    cells, cur = [], None
    for line in table_row:
        if line.startswith('|'):
            if cur is not None: cells.append(cur)
            cur = line[1:]
        elif cur is not None:
            cur += '\n' + line
    if cur is not None: cells.append(cur)
    return cells[index].strip() if index < len(cells) else ''
MEMO_TABLES = [  # (스니펫, 설명 칸 번호: 필드 표는 Path|Type|Required|Description, 파라미터 표는 Parameter|Required|Description)
    ('create-memo/request-fields.adoc', 3), ('create-memo/response-fields.adoc', 3), ('get-memo/path-parameters.adoc', 2),
    ('get-memo/query-parameters.adoc', 2), ('get-memo/response-fields.adoc', 3), ('list-memos/query-parameters.adoc', 2),
    ('list-memos/response-fields.adoc', 3),
]
for f, i in MEMO_TABLES:
    t = read(f'{S}{MEMO}/{f}')
    empty = [r[0] for r in rows(t) if not cell(r, i)]
    check(f'KDoc 전용: {f} 의 모든 행에 설명 ({len(rows(t))}행)', rows(t) and not empty and 'No description' not in t, ','.join(empty))
cm = snip(MEMO, 'create-memo', 'request-fields.adoc')
check('KDoc 전용 요청 필드: 프로퍼티 KDoc ([MemoColor.YELLOW] 링크 → MemoColor.YELLOW) + enum 상수 KDoc + Kotlin 기본값',
      '메모 제목' in row(cm, 'title') and all(s in row(cm, 'color') for s in ['메모 색. 보내지 않으면 MemoColor.YELLOW 다', 'YELLOW : 노란색. 색을 고르지 않으면 이 색이다.', 'PINK : 분홍색. 급한 일에 쓴다.', 'BLUE : 하늘색. 끝난 일을 모아 둘 때 쓴다.', 'Default: `+YELLOW+`']),
      row(cm, 'color'))
check('KDoc 전용 중첩 객체: reminder.remindAt·reminder.daily (MemoReminder 프로퍼티 KDoc)',
      '알림을 보낼 시각' in row(cm, 'reminder.remindAt') and '매일 같은 시각에 다시 알릴지 여부' in row(cm, 'reminder.daily'))
gm = snip(MEMO, 'get-memo', 'response-fields.adoc')
check('KDoc 전용 응답 필드: 클래스 KDoc @property (MemoResponse.createdAt·reminder)',
      '메모를 작성한 시각' in row(gm, 'createdAt') and '메모 알림. 알림을 정하지 않은 메모는 `null` 이다' in row(gm, 'reminder'))
check('KDoc 전용 경로 파라미터: 핸들러 메서드 KDoc @param (getMemo 의 @param id, 테스트는 이름만 pathParameters("id"))',
      'pathParameters("id")' in mt and '조회할 메모의 ID' in row(snip(MEMO, 'get-memo', 'path-parameters.adoc'), 'id'))
MEMO_INCLUDE_REMINDER = '알림을 함께 돌려줄지 여부. `false` 면 `reminder` 를 `null` 로 돌려준다'
memo_docs = json.loads(read('build/kardx/source-docs/source-docs.json'))['methods']
check('KDoc 전용 source-docs.json: getMemo 메서드 항목의 params (인자 이름 → @param 설명)',
      memo_docs.get('com.example.restdocs.memo.MemoController#getMemo', {}).get('params')
      == {'id': '조회할 메모의 ID', 'includeReminder': MEMO_INCLUDE_REMINDER},
      str(memo_docs.get('com.example.restdocs.memo.MemoController#getMemo')))
gq = next((r for r in rows(snip(MEMO, 'get-memo', 'query-parameters.adoc')) if r[0] == '|`+includeReminder+`'), [])
check('KDoc 전용 선택 쿼리 파라미터: @param 설명 + 수식어 (getMemo 의 includeReminder, 테스트는 queryMeans FromParamTag isOptional true)',
      '"includeReminder" queryMeans FromParamTag isOptional true' in mt and MEMO_INCLUDE_REMINDER in cell(gq, 2)
      and cell(gq, 1) == 'false' and 'Example: `+false+`' in cell(gq, 2), '\n'.join(gq))
lq = snip(MEMO, 'list-memos', 'query-parameters.adoc')
check('KDoc 전용 쿼리 파라미터: 프로퍼티 KDoc ([MemoColor] 링크 → MemoColor) + enum 값 + 기본값',
      '이 색(MemoColor)의 메모만 돌려준다' in row(lq, 'color') and 'PINK : 분홍색' in row(lq, 'color') and 'Default: `+20+`' in row(lq, 'size'), row(lq, 'color'))
check('KDoc 전용 목록 응답: MemoPage @property + 배열 안 MemoResponse',
      '조건에 맞는 메모의 전체 개수' in row(snip(MEMO, 'list-memos', 'response-fields.adoc'), 'total')
      and '메모를 작성한 시각' in row(snip(MEMO, 'list-memos', 'response-fields.adoc'), 'content[].createdAt'))
MEMO_OPERATIONS = [  # (경로, 메서드, summary = KDoc 첫 문장(끝 마침표 없음), description = KDoc 전체)
    ('/api/v1/memos', 'post', '새 메모를 작성한다', '새 메모를 작성한다. 저장한 메모를 ID 와 작성 시각을 채워 돌려준다. 색을 보내지 않으면 노란색(YELLOW) 메모가 된다.'),
    ('/api/v1/memos/{id}', 'get', 'ID 로 메모 하나를 조회한다', 'ID 로 메모 하나를 조회한다. 알림을 정하지 않은 메모는 `reminder` 가 `null` 이다. 없는 ID 면 404 를 돌려준다.'),
    ('/api/v1/memos', 'get', '메모 목록을 페이지로 나눠 조회한다', '메모 목록을 페이지로 나눠 조회한다. 메모를 ID 순으로 MemoSearchCondition.page·MemoSearchCondition.size 로 나눠 돌려준다. color 를 보내면 그 색의 메모만 돌려준다.'),
]
for path, method, summary, description in MEMO_OPERATIONS:
    op = operation(path, method)
    check(f'KDoc 전용 operation: {method.upper()} {path} summary = 첫 문장, description = 전체 ([a][B]·[B] 링크 정리)',
          f'      summary: {summary}\n' in op and f'      description: {description}\n' in op, op[:400])
MEMO_SUMMARIES = [('create-memo', 0), ('get-memo', 1), ('list-memos', 2)]  # (스니펫 디렉터리, MEMO_OPERATIONS 번호)
for name, index in MEMO_SUMMARIES:
    summary_snippet = snip(MEMO, name, 'api-summary.adoc')
    check(f'KDoc 전용 HTML 설명: {name}/api-summary.adoc 에 메서드 KDoc 전체 (테스트에 description = 없음)',
          f'\n\n{MEMO_OPERATIONS[index][3]}\n' in summary_snippet, summary_snippet[:300])
mh_text = re.sub(r'<[^>]+>', '', read(f'build/api-docs/{MEMO}.html'))
check('KDoc 전용 HTML 설명: memo 문서 HTML 에 operation 설명 (메서드 KDoc)',
      all(op[3].replace('`', '') in mh_text for op in MEMO_OPERATIONS), mh_text[:200])
check('KDoc 전용 OpenAPI 파라미터: 경로 id·쿼리 color·page·size 설명',
      'description: 조회할 메모의 ID' in param('/api/v1/memos/{id}', 'get', 'id', 'path')
      and '이 색(MemoColor)의 메모만 돌려준다' in param('/api/v1/memos', 'get', 'color', 'query')
      and 'description: 페이지 크기' in param('/api/v1/memos', 'get', 'size', 'query'))
ir = param('/api/v1/memos/{id}', 'get', 'includeReminder', 'query')
check('KDoc 전용 OpenAPI 선택 파라미터: includeReminder 설명은 @param, required: false, example',
      f'description: {MEMO_INCLUDE_REMINDER}' in ir.replace("'", '') and 'required: false' in ir and 'example:' in ir, ir)
for name, count in [('CreateMemoRequest', 5), ('MemoResponse', 7), ('MemoPage', 4)]:
    block = schema(name)
    # props() 는 빈 줄이 든 여러 줄 설명(enum 값 목록)에서 멈추므로 최상위 프로퍼티 줄(들여쓰기 8칸)을 직접 센다
    names = re.findall(r'^        ([A-Za-z_]+):$', block, re.M)
    missing = [p for p in names if 'description: ' not in prop(block, p)]
    check(f'KDoc 전용 OpenAPI 스키마: {name} 의 모든 프로퍼티({count}개)에 설명', len(names) == count and not missing, f'{names} missing={missing}')
check('KDoc 전용 OpenAPI 스키마: 중첩 reminder 의 프로퍼티 설명', 'description: 알림을 보낼 시각' in prop(schema('MemoResponse'), 'reminder'))
mh = read(f'build/api-docs/{MEMO}.html')
check('KDoc 전용 HTML: 필드·파라미터 설명과 enum 값', all(s in mh for s in ['조회할 메모의 ID', '알림을 보낼 시각', '메모를 작성한 시각', '하늘색. 끝난 일을 모아 둘 때 쓴다.']) and 'No description' not in mh)

# 24. 문서 언어 (language = "ko"): core 문구와 플러그인 문구가 같은 언어인지
LANGUAGE_LABELS = [
    ('build/api-docs/index.html', '문서 열기', 'Open document'),  # core index
    (A + 'product-api-docs-test.adoc', '메인으로 돌아가기', 'Back to index'),  # core 하위 문서 링크
    ('build/api-docs/openapi3.yaml', '이 페이지의 항목', 'Items on this page'),  # core Spring Data 설명
    ('build/api-docs/openapi3.yaml', '제약: ', 'Constraints: '),  # 플러그인 OpenAPI 설명
    ('build/api-docs/openapi3.yaml', '쿠키:', 'Cookies:'),  # 플러그인 Set-Cookie 설명
    (f'build/api-docs/{ASYNC}.yaml', '연결할 때 헤더를 보낸다', 'Headers sent when connecting'),  # core AsyncAPI
    (f'build/api-docs/{ASYNC}.html', '<html lang="ko"', '<html lang="en"'),  # core AsyncAPI 뷰어
    ('build/api-docs/openapi.html', '<html lang="ko"', '<html lang="en"'),  # 플러그인 OpenAPI 뷰어
]
if BOOT4:
    # index 한 파일 안의 core 문구(문서 열기)와 플러그인 문구(공통 오류 응답 절)
    LANGUAGE_LABELS += [
        ('build/api-docs/index.html', '공통 오류 응답', 'Common error responses'),
        ('build/api-docs/index.html', '상태 코드', 'Status code'),
    ]
check_language(LANGUAGE_LABELS)

report()
