const spec = __SPEC_JSON__;
// 화면 문구. 페이지를 만들 때 고른 언어(kardx.language)의 것만 들어 있다. {0}, {1} … 은 fmt 가 채운다.
const T = __VIEWER_TEXTS__;
const fmt = (key, ...args) => T[key].replace(/\{(\d+)}/g, (m, i) => args[i] ?? m);
document.querySelectorAll('[data-text]').forEach(node => { node.textContent = T[node.dataset.text]; });
document.getElementById('log').dataset.empty = T.LOG_EMPTY;

AsyncApiStandalone.render({ schema: spec, config: { show: { sidebar: true }, expand: { messageExamples: true }, sidebar: { showOperations: 'byDefault' } } },
  document.getElementById('doc'));

// ---- 탭 ----
const docEl = document.getElementById('doc'), tryEl = document.getElementById('tryit');
function showTab(tryIt) {
  docEl.style.display = tryIt ? 'none' : '';
  tryEl.classList.toggle('show', tryIt);
  document.getElementById('tab-doc').classList.toggle('active', !tryIt);
  document.getElementById('tab-try').classList.toggle('active', tryIt);
  history.replaceState(null, '', location.pathname + location.search + (tryIt ? '#try-it-out' : ''));
}
document.getElementById('tab-doc').onclick = () => showTab(false);
document.getElementById('tab-try').onclick = () => showTab(true);

// ---- 스펙 도우미 ----
function pointer(ref) {
  return ref.replace(/^#\//, '').split('/')
    .map(k => decodeURIComponent(k).replace(/~1/g, '/').replace(/~0/g, '~'))
    .reduce((node, key) => node == null ? undefined : node[key], spec);
}
function deref(node) { return node && node.$ref ? deref(pointer(node.$ref)) : node; }
// 채널의 x-protocol(라이브러리가 기록) 또는 서버 protocol → Try it out 방식
function protocolKind(protocol) {
  const p = (protocol || '').toLowerCase();
  if (p.startsWith('stomp')) return 'stomp';
  if (p === 'ws' || p === 'wss' || p === 'websocket') return 'ws';
  return 'http';
}
function channelKind(channel, server) {
  return protocolKind(channel['x-protocol'] || (server && server.protocol));
}
const KIND_LABEL = { stomp: 'STOMP', ws: 'WebSocket', http: 'SSE' };
// AsyncAPI 의 action 은 서버 기준이다. receive = 서버가 받는다 = 클라이언트가 보낸다.
function clientSends(op) { return op.action === 'receive'; }
function operationMessages(op) {
  const channel = deref(op.channel) || {};
  const list = op.messages && op.messages.length ? op.messages : Object.values(channel.messages || {});
  return list.map(deref).filter(Boolean);
}
// 설명의 `코드` 를 <code> 로. 그 밖의 텍스트는 그대로 넣는다.
function inlineCode(text) {
  return (text || '').split(/`([^`]+)`/).map((part, i) => i % 2 ? el('code', {}, part) : part);
}
function pretty(value) { return typeof value === 'string' ? value : JSON.stringify(value, null, 2); }
function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  Object.entries(attrs).forEach(([k, v]) => k === 'class' ? node.className = v : k.startsWith('on') ? node[k] = v : node.setAttribute(k, v));
  children.flat(Infinity).forEach(c => node.append(c instanceof Node ? c : document.createTextNode(c ?? '')));
  return node;
}

// ================= 문서 탭: 렌더 결과 다듬기 =================
// React 가 다시 그려도 깨지지 않도록 노드를 지우거나 옮기지 않는다.
// 텍스트 노드의 값만 바꾸고, 표시는 data-rd-* 속성 + CSS 로 한다. 매번 처음부터 다시 적용해도 결과가 같다.

const DIRECTION = {
  out: { short: T.OUT_SHORT, long: T.OUT_LONG, message: T.OUT_MESSAGE, tip: T.OUT_TIP },
  in: { short: T.IN_SHORT, long: T.IN_LONG, message: T.IN_MESSAGE, tip: T.IN_TIP },
};
// 렌더러의 영어 고정 문구 중 바꿔도 의미가 그대로인 것만 옮긴다. 영어 화면에서는 원문과 같다.
const DOC_TEXT = {
  'Introduction': T.DOC_INTRODUCTION, 'Servers': T.DOC_SERVERS, 'Operations': T.DOC_OPERATIONS,
  'Messages': T.DOC_MESSAGES, 'Schemas': T.DOC_SCHEMAS, 'Examples': T.DOC_EXAMPLES, 'Expand all': T.DOC_EXPAND_ALL,
  'Collapse all': T.DOC_COLLAPSE_ALL, 'Available only on servers:': T.DOC_SERVERS_ONLY,
  'Channel specific information': T.DOC_CHANNEL_BINDINGS, 'Channel Extensions': T.DOC_CHANNEL_EXTENSIONS,
  'Parameters': T.DOC_PARAMETERS, 'This example has been generated automatically.': T.DOC_GENERATED_EXAMPLE,
};
const SCHEMA_TOGGLES = ['Payload', 'Headers', 'Parameters', DOC_TEXT.Parameters];

function setText(node, text) { if (node && node.nodeValue !== text) node.nodeValue = text; }
function setAttr(node, name, value) { if (node && node.getAttribute(name) !== value) node.setAttribute(name, value); }
function ownText(node) { return [...node.childNodes].find(n => n.nodeType === Node.TEXT_NODE); }
const plain = text => (text || '').replace(/`/g, '').replace(/\s+/g, ' ').trim();

// "#operation-receive-receive-chatSend" 같은 앵커·id 에서 operation 을 찾는다.
function operationFromAnchor(anchor) {
  const match = /^#?operation-(send|receive)-(.+)$/.exec(anchor || '');
  return match && spec.operations && spec.operations[match[2]] ? [match[2], spec.operations[match[2]]] : null;
}
function directionOf(op) { return clientSends(op) ? 'out' : 'in'; }
function markDirection(badge, op, long) {
  const dir = directionOf(op);
  setAttr(badge, 'data-rd-dir', dir);
  setAttr(badge, 'title', DIRECTION[dir].tip);
  setText(ownText(badge), long ? DIRECTION[dir].long : DIRECTION[dir].short);
}

// 사이드바: 내부 operation ID 대신 채널 주소 + 메시지 이름, 배지는 클라이언트 기준
function relabelSidebar(root) {
  root.querySelectorAll('.sidebar a[href^="#operation-"]').forEach(link => {
    const found = operationFromAnchor(link.getAttribute('href'));
    if (!found) return;
    const [id, op] = found;
    const badge = link.querySelector('strong'), label = link.querySelector('span');
    if (badge) markDirection(badge, op, false);
    if (!label) return;
    const channel = deref(op.channel) || {};
    // 긴 주소는 단어 중간이 아니라 구분자(/ .) 뒤에서 줄이 바뀌게 폭 없는 공백을 넣는다
    setText(ownText(label), (channel.address || id).replace(/([/.])/g, '$1\u200B'));
    setAttr(label, 'data-rd-sub', operationMessages(op).map(m => m.name || m.title).filter(Boolean).join(' · ') || op.summary || '');
    setAttr(link, 'title', [op.summary, 'operation: ' + id].filter(Boolean).join(' — '));
  });
}

// 본문 operation: 방향 배지, "Accepts the following message" 문구, 반복되는 채널 설명
function relabelOperations(root) {
  root.querySelectorAll('li[id^="operation-"]').forEach(item => {
    const found = operationFromAnchor(item.id);
    if (!found) return;
    const op = found[1], dir = directionOf(op);
    const badge = item.querySelector('h3 > span[title]');
    if (badge) markDirection(badge, op, true);
    relabelAccepts(item, dir);
    markChannelNote(item, deref(op.channel) || {});
  });
}
// 렌더러 원문과, 이미 바꾼 문구(다시 그려도 같은 결과가 나오게)
const ACCEPTS = ['Accepts', T.OUT_MESSAGE, T.IN_MESSAGE];
function relabelAccepts(item, dir) {
  item.querySelectorAll('p').forEach(p => {
    const texts = [...p.childNodes];
    const first = texts[0];
    if (!first || first.nodeType !== Node.TEXT_NODE || !ACCEPTS.some(prefix => first.nodeValue.startsWith(prefix))) return;
    if (texts.length === 1) { setText(first, DIRECTION[dir].message); return; }
    // "Accepts <strong>one of</strong> the following messages:"
    setText(first, DIRECTION[dir].message + ' (');
    setText(ownText(texts[1]) || texts[1], T.ONE_OF);
    if (texts[2]) setText(texts[2], ')');
  });
}
function markChannelNote(item, channel) {
  if (!channel.description) return;
  const target = plain(channel.description);
  const center = item.querySelector('.panel-item--center');
  const note = center && [...center.children].find(child => child.querySelector(':scope > .prose') && plain(child.textContent) === target);
  setAttr(note, 'data-rd', 'channel-note');
}

// 영어 고정 문구 옮기기, 내부 ID 상자 숨기기, 서버 줄 줄이기
function translateText(root) {
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const nodes = [];
  for (let node = walker.nextNode(); node; node = walker.nextNode()) nodes.push(node);
  nodes.forEach(node => {
    const parent = node.parentElement;
    if (!parent || parent.closest('.prose, pre, code')) return;
    const key = node.nodeValue.trim();
    if (key === 'Operation ID' || key === 'Message ID') { setAttr(parent.closest('.border'), 'data-rd-hide', ''); return; }
    if (key === 'Available only on servers:') setAttr(parent.parentElement, 'data-rd', 'servers');
    const example = /^#(\d+) Example$/.exec(key);
    if (example) setText(node, fmt('DOC_EXAMPLE_NUMBER', example[1]));
    else if (DOC_TEXT[key]) setText(node, DOC_TEXT[key]);
  });
}

// Payload·Headers 스키마는 첫 단계를 펼쳐 둔다. 한 번 펼친 버튼은 표시해 두어 사용자가 접으면 그대로 둔다.
function expandSchemas(root) {
  let clicked = false;
  root.querySelectorAll('.panel-item--center button[type="button"]').forEach(button => {
    if (button.dataset.rdAuto || button.closest('.examples')) return;
    const label = button.querySelector('span');
    if (!label || !SCHEMA_TOGGLES.includes(label.textContent.trim())) return;
    button.dataset.rdAuto = '1';
    const arrow = button.querySelector('svg');
    if (arrow && !/rotate-90/.test(arrow.getAttribute('class') || '')) { button.click(); clicked = true; }
  });
  return clicked;
}

function relabelDoc(root) {
  relabelSidebar(root);
  relabelOperations(root);
  translateText(root);
}

// 렌더러가 다시 그리면 다시 적용한다. 적용하는 동안은 관찰을 멈춰 자기 변경으로 다시 불리지 않게 한다.
// 문구는 다음 프레임에 바로 바꾸고, 펼치기는 DOM 이 잠잠해진 뒤(렌더러의 초기 effect 가 접힘 상태로 되돌리지 않게) 한다.
function watchDoc(root) {
  const options = { childList: true, subtree: true, characterData: true };
  let queued = false, expandTimer = 0;
  const observer = new MutationObserver(() => schedule());
  function schedule() {
    if (!queued) { queued = true; requestAnimationFrame(relabel); }
    clearTimeout(expandTimer);
    expandTimer = setTimeout(expand, 200);
  }
  function quietly(fn) {
    observer.disconnect();
    try { return fn(); } finally { observer.observe(root, options); }
  }
  function relabel() { queued = false; quietly(() => relabelDoc(root)); }
  // 클릭으로 새로 그려진 부분은 관찰을 멈춘 사이에 생겼으므로 다시 훑는다
  function expand() { if (quietly(() => expandSchemas(root))) schedule(); }
  observer.observe(root, options);
  schedule();
}
watchDoc(docEl);

// ================= Try it out =================

// ---- 연결 상태 (점 + 글자) ----
const statusViews = {};   // key → 상태를 보여 주는 요소들
const statusState = {};   // key → { tone, text }
function statusView(key) {
  const view = el('span', { class: 'status' }, '');
  (statusViews[key] = statusViews[key] || []).push(view);
  paintStatus(view, statusState[key] || { tone: '', text: T.NOT_CONNECTED });
  return view;
}
function paintStatus(view, { tone, text }) { view.className = 'status' + (tone ? ' ' + tone : ''); view.textContent = text; }
function setStatus(key, tone, text) {
  statusState[key] = { tone, text };
  (statusViews[key] || []).forEach(view => paintStatus(view, statusState[key]));
  refreshSummary();
}

// ---- 연결 설정 ----
const serverInput = document.getElementById('server-url');
const firstHttp = Object.values(spec.servers || {}).find(s => protocolKind(s.protocol) === 'http');
serverInput.value = location.protocol.startsWith('http') ? location.origin : 'http://' + (firstHttp ? firstHttp.host : 'localhost:8080');
const httpBase = () => serverInput.value.trim().replace(/\/$/, '');
const wsBase = () => httpBase().replace(/^http/, 'ws');
const connectInput = document.getElementById('connect-headers'), connectHint = document.getElementById('connect-hint');

// 문서화한 연결 헤더(bindings.ws.headers)의 예시로 STOMP CONNECT 헤더 칸을 채운다.
function fillConnectHeaders() {
  const headers = Object.values(spec.channels || {})
    .map(c => c.bindings && c.bindings.ws && c.bindings.ws.headers && c.bindings.ws.headers.properties)
    .find(Boolean);
  if (!headers) return;
  const filled = {};
  Object.entries(headers).forEach(([name, s]) => { filled[name] = (s.examples && s.examples[0]) || ''; });
  connectInput.value = JSON.stringify(filled, null, 2);
  connectInput.rows = Math.min(10, connectInput.value.split('\n').length + 1);
}
const checkMasked = () => { connectHint.textContent = connectInput.value.includes('****') ? T.MASKED_HINT : ''; };
fillConnectHeaders();
connectInput.oninput = checkMasked; checkMasked();

// 채널마다 어떤 방식으로 연결하는지
function channelServer(channel) {
  const servers = (channel.servers || []).map(deref);
  return servers[0] || Object.values(spec.servers || {})[0] || {};
}
const usedKinds = new Set(Object.values(spec.channels || {}).map(c => channelKind(c, channelServer(c))));
const hasStomp = usedKinds.has('stomp') || Object.values(spec.servers || {}).some(s => protocolKind(s.protocol) === 'stomp');
// STOMP 채널이 없으면 CONNECT 헤더 칸은 숨긴다.
document.querySelectorAll('.stomp-only').forEach(node => { node.style.display = hasStomp ? '' : 'none'; });
// WebSocket·STOMP 가 없으면 ws:// 안내도 숨긴다.
document.querySelectorAll('.ws-only').forEach(node => { node.style.display = hasStomp || usedKinds.has('ws') ? '' : 'none'; });
const stompServer = Object.values(spec.servers || {}).find(s => protocolKind(s.protocol) === 'stomp') || {};

// ---- 인증 (components.securitySchemes) ----
// 자격 증명은 입력 칸(이 페이지의 메모리)에만 있다. 저장하지 않으며 새로 고치면 사라진다.
// STOMP 는 CONNECT 프레임 헤더로 보낸다. 브라우저는 WebSocket 핸드셰이크와 EventSource 에 헤더를 붙일 수 없어서,
// WebSocket·SSE 는 사용자가 고르면 쿼리 파라미터로 보낸다 (in: query 인 API 키는 항상 쿼리로 보낸다).
const securitySchemes = Object.entries((spec.components && spec.components.securitySchemes) || {})
  .map(([name, scheme]) => ({ key: name, ...deref(scheme) }));
const authEl = document.getElementById('auth');
const authInputs = {};
let authScheme = null;
function schemeKind(scheme) {
  if (scheme.type === 'httpApiKey') return 'apiKey';
  return (scheme.scheme || '').toLowerCase() === 'basic' ? 'basic' : 'bearer';
}
function schemeType(scheme) {
  return { bearer: T.AUTH_BEARER, basic: T.AUTH_BASIC, apiKey: T.AUTH_API_KEY }[schemeKind(scheme)];
}
function schemePlace(scheme) {
  if (schemeKind(scheme) !== 'apiKey') return fmt('AUTH_IN_HEADER', 'Authorization');
  return fmt({ query: 'AUTH_IN_QUERY', cookie: 'AUTH_IN_COOKIE' }[scheme.in] || 'AUTH_IN_HEADER', scheme.name);
}
function base64(text) { return btoa(String.fromCharCode(...new TextEncoder().encode(text))); }
// 입력한 자격 증명. place 는 header·query·cookie, value 는 헤더 값, raw 는 쿼리로 보낼 값
function authCredential() {
  if (!authScheme) return null;
  const kind = schemeKind(authScheme);
  if (kind === 'basic') {
    const user = authInputs.username.value, password = authInputs.password.value;
    if (!user && !password) return null;
    const raw = base64(user + ':' + password);
    return { place: 'header', name: 'Authorization', value: 'Basic ' + raw, raw };
  }
  const raw = authInputs.secret.value.trim();
  if (!raw) return null;
  if (kind === 'bearer') return { place: 'header', name: 'Authorization', value: 'Bearer ' + raw, raw };
  return { place: authScheme.in || 'header', name: authScheme.name, value: raw, raw };
}
// transport 'stomp' 는 CONNECT 헤더를 보낼 수 있고, 'browser'(WebSocket 핸드셰이크·EventSource) 는 못 보낸다.
function authFor(transport) {
  const credential = authCredential();
  if (!credential || credential.place === 'cookie') return {};
  if (credential.place === 'query') return { query: [credential.name, credential.raw] };
  if (transport === 'stomp') return { header: [credential.name, credential.value] };
  if (!authInputs.asQuery || !authInputs.asQuery.checked) return {};
  return { query: [authInputs.queryName.value.trim() || 'access_token', credential.raw] };
}
// 실제로 연결할 주소와, 로그에 남길 (자격 증명을 가린) 주소
function authUrl(url, query) {
  if (!query) return { url, shown: url };
  const join = (value) => url + (url.includes('?') ? '&' : '?') + encodeURIComponent(query[0]) + '=' + value;
  return { url: join(encodeURIComponent(query[1])), shown: join('****') };
}
function credentialFields(kind) {
  const inputs = kind === 'basic'
    ? [['username', T.AUTH_USERNAME, 'text'], ['password', T.AUTH_PASSWORD, 'password']]
    : [['secret', kind === 'apiKey' ? T.AUTH_API_KEY_VALUE : T.AUTH_TOKEN, 'password']];
  return inputs.map(([key, label, type]) => {
    authInputs[key] = el('input', { type, autocomplete: 'off', spellcheck: 'false' });
    return el('div', {}, el('label', {}, label), authInputs[key]);
  });
}
function authNotes() {
  if (authScheme.in === 'cookie') return [el('div', { class: 'hint' }, T.AUTH_COOKIE_NOTE)];
  if (authScheme.in === 'query') return [el('div', { class: 'help' }, T.AUTH_QUERY_ALWAYS)];
  const notes = [];
  if (hasStomp) notes.push(el('div', { class: 'help' }, T.AUTH_STOMP_HEADER));
  if (!usedKinds.has('ws') && !usedKinds.has('http')) return notes;
  authInputs.asQuery = el('input', { type: 'checkbox' });
  authInputs.queryName = el('input', { value: 'access_token', spellcheck: 'false' });
  notes.push(el('div', { class: 'hint' }, T.AUTH_BROWSER_LIMIT),
    el('label', { class: 'check' }, authInputs.asQuery, T.AUTH_AS_QUERY),
    el('div', {}, el('label', {}, T.AUTH_QUERY_NAME), authInputs.queryName));
  return notes;
}
function renderCredentials(fields) {
  Object.keys(authInputs).forEach(key => delete authInputs[key]);
  fields.replaceChildren();
  if (authScheme) fields.append(...credentialFields(schemeKind(authScheme)), ...authNotes());
}
function buildAuth() {
  if (!securitySchemes.length) { authEl.remove(); return; }
  const select = el('select', { id: 'auth-scheme' }, el('option', { value: '' }, T.AUTH_NONE),
    securitySchemes.map((scheme, i) => el('option', { value: String(i) }, scheme.key)));
  const fields = el('div', { class: 'auth-fields' });
  select.value = '0';
  select.onchange = () => { authScheme = securitySchemes[select.value] || null; renderCredentials(fields); };
  authEl.append(el('h4', {}, T.AUTHENTICATION),
    el('ul', { class: 'auth-list' }, securitySchemes.map(scheme => el('li', {},
      el('code', {}, scheme.key), el('span', { class: 'proto' }, schemeType(scheme)),
      el('span', { class: 'auth-place' }, schemePlace(scheme))))),
    el('div', { class: 'auth-grid' },
      el('div', {}, el('label', { for: 'auth-scheme' }, T.AUTH_USE), select, el('div', { class: 'help' }, T.AUTH_MEMORY_ONLY)),
      fields));
  select.onchange();
}

// 상단 요약: STOMP 는 공유 연결 하나, WebSocket·SSE 는 카드마다 연결이라 개수로 보여 준다.
const summaryEl = document.getElementById('conn-summary');
const summaryCounts = {};
function refreshSummary() {
  ['ws', 'http'].forEach(kind => {
    const view = summaryCounts[kind];
    if (!view) return;
    const open = Object.entries(statusState).filter(([key, s]) => key.startsWith(kind + ':') && s.tone === 'on').length;
    paintStatus(view, open ? { tone: 'on', text: fmt(kind === 'ws' ? 'WS_OPEN_COUNT' : 'SSE_OPEN_COUNT', open) }
      : { tone: '', text: kind === 'ws' ? T.WS_NONE : T.SSE_NONE });
  });
}
function buildSummary() {
  if (hasStomp) {
    const button = el('button', { class: 'btn secondary small' }, T.CONNECT);
    button.onclick = () => (stomp && stomp.active ? stomp.deactivate() : stompConnect(stompServer.pathname).catch(() => {}));
    stompButtons.push(button);
    summaryEl.append(el('span', { class: 'conn-item' }, el('b', {}, 'STOMP'), statusView('stomp'), button));
  }
  ['ws', 'http'].filter(kind => usedKinds.has(kind)).forEach(kind => {
    summaryCounts[kind] = el('span', { class: 'status' }, '');
    summaryEl.append(el('span', { class: 'conn-item' }, el('b', {}, KIND_LABEL[kind]), summaryCounts[kind]));
  });
  refreshSummary();
}

// ---- 메시지 로그 ----
const logEl = document.getElementById('log');
function log(kind, label, body) {
  const time = new Date().toLocaleTimeString('en-GB', { hour12: false });
  const tag = { out: T.LOG_OUT, in: T.LOG_IN, info: T.LOG_INFO, error: T.LOG_ERROR }[kind];
  const entry = el('div', { class: 'entry ' + kind },
    el('div', {}, el('span', { class: 'tag' }, tag), ' ', el('span', { class: 'meta' }, time + ' ' + label)));
  if (body !== undefined && body !== '') {
    let text = body;
    try { text = JSON.stringify(JSON.parse(body), null, 2); } catch (_) { /* JSON 이 아니면 그대로 */ }
    entry.append(el('pre', {}, text));
  }
  logEl.prepend(entry);
}
document.getElementById('clear-log').onclick = () => logEl.replaceChildren();

// ---- STOMP: 연결 하나를 모든 STOMP 카드가 같이 쓴다 ----
let stomp = null, stompReady = null;
const stompSubs = {};
const stompResets = [];   // 연결이 끊기면 구독 카드의 버튼·상태를 되돌린다
const stompButtons = [];  // 연결 설정의 연결/끊기 버튼
function stompState(tone, text) {
  setStatus('stomp', tone, text);
  stompButtons.forEach(b => { b.textContent = tone === 'on' || tone === 'busy' ? T.DISCONNECT : T.CONNECT; });
}
function stompDisconnected(tone, text) {
  Object.keys(stompSubs).forEach(key => delete stompSubs[key]);
  stompResets.forEach(reset => reset());
  stompReady = null;
  stompState(tone || '', text || T.NOT_CONNECTED);
}
// STOMP 1.1/1.2 헤더 값의 이스케이프(\c \n \r \\)를 푼다. stompjs 는 1.2 로 CONNECTED 를 받은 뒤에만
// 풀어 주므로, CONNECT 를 거절한 ERROR 프레임(버전 협상 전)과 1.1 연결의 헤더는 이스케이프된 채로 온다.
const STOMP_ESCAPES = { c: ':', n: '\n', r: '\r', '\\': '\\' };
function unescapeStompHeader(value) {
  return value.replace(/\\([cnr\\])/g, (_, ch) => STOMP_ESCAPES[ch]);
}
function stompHeader(frame, name) {
  const value = frame.headers[name];
  const version = stomp && stomp.connectedVersion;
  if (value == null || version === '1.2' || version === '1.0') return value;
  return unescapeStompHeader(value);
}
function stompConnect(path) {
  if (stomp && stomp.connected) return Promise.resolve(stomp);
  if (stompReady) return stompReady;
  let headers = {};
  try {
    headers = JSON.parse(connectInput.value || '{}');
  } catch (_) {
    log('error', T.CONNECT_HEADERS_INVALID);
    return Promise.reject();
  }
  const auth = authFor('stomp');
  if (auth.header) headers[auth.header[0]] = auth.header[1];
  const target = authUrl(wsBase() + (path || '/'), auth.query), url = target.shown;
  stompState('busy', T.CONNECTING);
  stompReady = new Promise((resolve, reject) => {
    stomp = new StompJs.Client({
      brokerURL: target.url, connectHeaders: headers, reconnectDelay: 0,
      onConnect: () => { log('info', fmt('STOMP_CONNECTED', url)); stompState('on', T.CONNECTED); resolve(stomp); },
      onStompError: frame => { log('error', 'STOMP ' + (stompHeader(frame, 'message') || T.ERROR), frame.body); stompDisconnected('error', T.REJECTED); reject(); },
      onWebSocketError: () => { log('error', fmt('STOMP_CONNECT_FAILED', url)); stompDisconnected('error', T.CONNECT_FAILED); reject(); },
      onWebSocketClose: () => {
        if (statusState.stomp && statusState.stomp.tone === 'error') return;   // 실패 표시는 남겨 둔다
        log('info', T.STOMP_CLOSED); stompDisconnected();
      },
    });
    stomp.activate();
  });
  return stompReady;
}

// ---- WebSocket: 카드마다 연결 하나 ----
function openSocket(key, address) {
  const target = authUrl(wsBase() + address, authFor('browser').query), url = target.shown;
  const socket = new WebSocket(target.url);
  setStatus(key, 'busy', T.CONNECTING);
  socket.ready = new Promise((resolve, reject) => {
    socket.onopen = () => { log('info', fmt('WS_CONNECTED', url)); setStatus(key, 'on', T.CONNECTED); resolve(socket); };
    socket.onerror = () => { log('error', fmt('WS_CONNECT_FAILED', url)); setStatus(key, 'error', T.CONNECT_FAILED); reject(); };
  });
  socket.onmessage = e => log('in', address, typeof e.data === 'string' ? e.data : '(binary)');
  return socket;
}

// ================= 채널 카드 =================
// 같은 채널(주소)의 operation 을 카드 하나로 묶는다: 머리 → 연결/구독 → 보낼 메시지 → 받을 메시지
function channelGroups() {
  const groups = new Map();
  Object.entries(spec.operations || {}).forEach(([id, op]) => {
    const key = (op.channel && op.channel.$ref) || id;
    if (!groups.has(key)) groups.set(key, { id: 'ch' + groups.size, channel: deref(op.channel) || {}, sends: [], receives: [] });
    groups.get(key)[clientSends(op) ? 'sends' : 'receives'].push({ id, op, messages: operationMessages(op) });
  });
  return [...groups.values()];
}

function destinationInput(address) {
  const input = el('input', { value: address || '', spellcheck: 'false' });
  const hint = el('div', { class: 'hint' });
  const check = () => { hint.textContent = /\{[^}]+\}/.test(input.value) ? T.PATH_VARIABLE_HINT : ''; };
  input.addEventListener('input', check); check();
  return [input, hint];
}

function channelCard(group) {
  const server = channelServer(group.channel);
  const kind = channelKind(group.channel, server);
  const [destination, hint] = destinationInput(group.channel.address);
  const ctx = { ...group, server, kind, destination, statusKey: kind + ':' + group.id };
  const box = el('section', { class: 'panel channel' });
  const control = kind === 'http' ? sseControl(ctx) : kind === 'stomp' ? stompControl(ctx) : webSocketControl(ctx);
  box.append(cardHead(ctx, control.status),
    el('div', { class: 'controls' }, el('span', { class: 'field-label' }, kind === 'stomp' ? T.DESTINATION : T.PATH), destination, control.button || ''),
    hint, ...(control.notes || []));
  if (group.sends.length) box.append(sendSection(ctx, control.deliver));
  if (group.receives.length) box.append(receiveSection(group.receives));
  return box;
}

function cardHead({ channel, sends, receives, kind }, status) {
  const summaries = [...new Set([...sends, ...receives].map(({ op }) => op.summary).filter(Boolean))];
  return el('div', {},
    el('div', { class: 'card-head' },
      el('span', { class: 'proto' }, KIND_LABEL[kind]),
      el('span', { class: 'addr' }, channel.address || ''),
      status),
    summaries.length ? el('p', { class: 'summary' }, inlineCode(summaries.join(' · '))) : '',
    // STOMP 채널 설명은 연결 헤더 안내라 연결 설정 칸과 겹친다
    channel.description && kind !== 'stomp' ? el('p', { class: 'summary small' }, inlineCode(channel.description)) : '');
}

// 보낼 메시지: operation(메시지)마다 폼 하나
function sendSection(ctx, deliver) {
  const section = el('div', { class: 'section' }, el('div', { class: 'section-title' }, el('span', { class: 'dir out' }, T.SEND_MESSAGES)));
  ctx.sends.forEach(({ op, messages }) => section.append(sendForm(ctx, op, messages[0] || {}, deliver)));
  return section;
}
function sendForm(ctx, op, message, deliver) {
  const example = (message.examples && message.examples[0]) || {};
  const payload = textArea(example.payload === undefined ? '' : pretty(example.payload));
  const headers = ctx.kind === 'stomp' ? textArea(pretty(example.headers || {})) : null;
  const send = el('button', { class: 'btn' }, T.SEND);
  send.onclick = async () => {
    let extra = {};
    if (headers) {
      try { extra = JSON.parse(headers.value || '{}'); } catch (_) { log('error', T.HEADERS_INVALID); return; }
    }
    try {
      await deliver(payload.value, extra);
      log('out', ctx.destination.value, payload.value);
    } catch (_) { /* 실패는 연결 쪽에서 로그를 남겼다 */ }
  };
  const fields = [];
  if (headers) fields.push(el('div', {}, el('label', {}, T.HEADERS_JSON), headers));
  fields.push(el('div', {}, el('label', {}, T.PAYLOAD), payload));
  return el('div', { class: 'msg' },
    el('div', { class: 'msg-name' }, message.name || message.title || ''),
    op.description ? el('div', { class: 'msg-desc' }, inlineCode(op.description)) : '',
    el('div', { class: 'form-grid' + (headers ? ' two' : '') }, fields),
    el('div', { class: 'form-actions' }, send));
}
// 내용 줄 수에 맞춘 textarea (3~12줄)
function textArea(value) {
  const lines = value.split('\n').length;
  return el('textarea', { rows: String(Math.min(12, Math.max(3, lines))), spellcheck: 'false' }, value);
}

// 받을 메시지: 이름·설명, 예시는 접어 둔다
function receiveSection(receives) {
  const section = el('div', { class: 'section' }, el('div', { class: 'section-title' }, el('span', { class: 'dir in' }, T.RECEIVE_MESSAGES)));
  receives.forEach(({ op, messages }) => messages.forEach(message => {
    const example = message.examples && message.examples[0];
    section.append(el('div', { class: 'msg' },
      el('div', { class: 'msg-name' }, message.name || message.title || ''),
      el('div', { class: 'msg-desc' }, inlineCode(message.description || op.description || '')),
      example && example.payload !== undefined ? el('details', {}, el('summary', {}, T.EXAMPLE), el('pre', {}, pretty(example.payload))) : ''));
  }));
  return section;
}

// ---- 프로토콜별 연결 제어. { status, button, notes, deliver(body, headers) } 를 돌려준다 ----

// SSE: EventSource 로 구독하고, 받은 이벤트를 로그에 남긴다.
function sseControl({ channel, receives, destination, statusKey }) {
  const names = [...new Set(receives.flatMap(r => r.messages.map(m => m.name)).filter(Boolean))];
  const button = el('button', { class: 'btn' }, T.SUBSCRIBE);
  let source = null;
  const stop = (tone, text) => { source.close(); source = null; button.textContent = T.SUBSCRIBE; button.className = 'btn'; setStatus(statusKey, tone || '', text || T.NOT_SUBSCRIBED); };
  button.onclick = () => {
    const path = destination.value;
    if (source) { stop(); log('info', fmt('SSE_STOPPED', path)); return; }
    const target = authUrl(httpBase() + path, authFor('browser').query), url = target.shown;
    source = new EventSource(target.url);
    let opened = false;
    button.textContent = T.STOP; button.className = 'btn stop';
    setStatus(statusKey, 'busy', T.CONNECTING);
    log('info', fmt('SSE_SUBSCRIBING', url));
    source.onopen = () => { opened = true; setStatus(statusKey, 'on', T.SUBSCRIBED); };
    [...new Set(['message', ...names])].forEach(name =>
      source.addEventListener(name, e => log('in', `${path} [${name}]`, e.data)));
    // 서버가 스트림을 닫으면 EventSource 는 자동 재연결한다. Try it out 에서는 한 번 받고 멈춘다.
    source.onerror = () => {
      if (opened) { stop(); log('info', fmt('SSE_CLOSED', path)); return; }
      stop('error', T.CONNECT_FAILED);
      log('error', fmt('SSE_CONNECT_FAILED', url));
    };
  };
  const notes = [];
  if (names.length) notes.push(el('div', { class: 'help' }, T.SSE_EVENTS + ' ', names.map((n, i) => [i ? ', ' : '', el('code', {}, n)])));
  const required = (channel['x-request-headers'] && channel['x-request-headers'].required) || [];
  if (required.length) {
    notes.push(el('div', { class: 'hint' },
      fmt('SSE_HEADERS_REQUIRED', required.join(', '))));
  }
  statusState[statusKey] = { tone: '', text: T.NOT_SUBSCRIBED };
  return { status: statusView(statusKey), button, notes, deliver: () => Promise.reject() };
}

// STOMP: 서버가 보내는 메시지가 있으면 구독 버튼. 보내기는 공유 연결로 바로 보낸다.
function stompControl({ id, server, receives, destination, statusKey }) {
  const deliver = async (body, headers) => {
    const client = await stompConnect(server.pathname);
    client.publish({ destination: destination.value, body, headers });
  };
  if (!receives.length) return { status: statusView('stomp'), deliver };
  const button = el('button', { class: 'btn' }, T.SUBSCRIBE);
  const idle = () => { button.textContent = T.SUBSCRIBE; button.className = 'btn'; setStatus(statusKey, '', T.NOT_SUBSCRIBED); };
  stompResets.push(idle);
  button.onclick = async () => {
    const dest = destination.value;
    if (stompSubs[id]) {
      try { stompSubs[id].unsubscribe(); } catch (_) { /* 연결이 이미 끊겼다 */ }
      delete stompSubs[id]; idle(); log('info', fmt('UNSUBSCRIBED_FROM', dest));
      return;
    }
    try {
      const client = await stompConnect(server.pathname);
      stompSubs[id] = client.subscribe(dest, message => log('in', stompHeader(message, 'destination') || dest, message.body));
      button.textContent = T.UNSUBSCRIBE; button.className = 'btn stop'; setStatus(statusKey, 'on', T.SUBSCRIBED); log('info', fmt('SUBSCRIBED_TO', dest));
    } catch (_) { /* 로그를 남겼다 */ }
  };
  statusState[statusKey] = { tone: '', text: T.NOT_SUBSCRIBED };
  return { status: statusView(statusKey), button, deliver };
}

// WebSocket: 연결 버튼 하나. 보내기를 누르면 연결이 없을 때 먼저 연결한다.
function webSocketControl({ destination, statusKey }) {
  const button = el('button', { class: 'btn' }, T.CONNECT);
  let socket = null;
  const connect = () => {
    if (socket && socket.readyState <= 1) return socket.ready;
    const address = destination.value;
    socket = openSocket(statusKey, address);
    button.textContent = T.CLOSE_SOCKET; button.className = 'btn stop';
    const current = socket;
    current.onclose = e => {
      log('info', fmt('WS_CLOSED', address, e.code));
      if (socket === current) socket = null;
      button.textContent = T.CONNECT; button.className = 'btn';
      if (statusState[statusKey].tone !== 'error') setStatus(statusKey, '', T.NOT_CONNECTED);
    };
    return current.ready;
  };
  button.onclick = () => { if (socket && socket.readyState <= 1) socket.close(); else connect().catch(() => {}); };
  const deliver = async body => { (await connect()).send(body); };
  return { status: statusView(statusKey), button, deliver };
}

buildSummary();
buildAuth();
const operationsEl = document.getElementById('operations');
channelGroups().forEach(group => operationsEl.append(channelCard(group)));
if (location.hash === '#try-it-out') showTab(true);
