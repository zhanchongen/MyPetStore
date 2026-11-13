#!/usr/bin/env node
/**
 * MyPetStore 视觉验收脚本（零依赖）。
 *
 * 用系统自带的 Edge/Chrome 以无头模式渲染页面并整页截图，覆盖未登录、已登录、
 * 购物车/结算/订单/个人资料，以及管理端主要页面。目的是把「页面长什么样」变成
 * 可复查的产物，而不是靠肉眼记忆。
 *
 * 前置条件：
 *   1. MySQL、MyPetStore-SSM（8080）、MyPetStore-admin（8081）都已启动；
 *   2. 桌面已安装 Edge（默认路径）或 Chrome；
 *   3. Node.js >= 22（脚本用到内建 WebSocket / fetch）。
 *
 * 用法：
 *   node scripts/capture-pages.mjs [输出目录]
 *
 * 说明：
 *   - 脚本会自己拉起一个带 --remote-debugging-port 的无头浏览器，结束后关闭它；
 *   - 默认演示账号 alice / admin，密码 PetStore123!（可用环境变量覆盖）；
 *   - 截图是逐页整页高度，文件名带序号，方便按顺序贴进实验报告。
 */
import { spawn } from 'node:child_process';
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const OUT = process.argv[2] || 'rendered';
const USER_BASE = process.env.SHOP_BASE || 'http://127.0.0.1:8080';
const ADMIN_BASE = process.env.ADMIN_BASE || 'http://127.0.0.1:8081';
const SHOP_PASSWORD = process.env.SHOP_PASSWORD || 'PetStore123!';
const PORT = Number(process.env.CDP_PORT || 9333);
const WIDTH = 1440;

const BROWSERS = [
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Microsoft\\Edge\\Application\\msedge.exe',
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium'
];

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function findBrowser() {
  const hit = BROWSERS.find((p) => existsSync(p));
  if (!hit) throw new Error('未找到 Edge/Chrome，请改 BROWSERS 列表指向可执行文件');
  return hit;
}

mkdirSync(OUT, { recursive: true });
const profile = mkdtempSync(join(tmpdir(), 'mps-shots-'));
const browser = spawn(
  findBrowser(),
  [
    '--headless=new', '--disable-gpu', '--no-first-run', '--disable-breakpad',
    '--disable-crash-reporter', '--hide-scrollbars', '--no-default-browser-check',
    `--remote-debugging-port=${PORT}`, `--user-data-dir=${profile}`,
    `--window-size=${WIDTH},900`, 'about:blank'
  ],
  { stdio: 'ignore', detached: false }
);

let ws = null;
let seq = 0;
let sid = null;
const pending = new Map();

function send(method, params = {}, sessionId = null) {
  const id = ++seq;
  const msg = { id, method, params };
  if (sessionId) msg.sessionId = sessionId;
  ws.send(JSON.stringify(msg));
  return new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
}

async function waitForCdp() {
  for (let i = 0; i < 100; i++) {
    try {
      const r = await fetch(`http://127.0.0.1:${PORT}/json/version`);
      if (r.ok) return (await r.json()).webSocketDebuggerUrl;
    } catch { /* 浏览器还没起来 */ }
    await sleep(150);
  }
  throw new Error('CDP 端口未就绪');
}

async function connect() {
  ws = new WebSocket(await waitForCdp());
  await new Promise((res, rej) => {
    ws.addEventListener('open', res, { once: true });
    ws.addEventListener('error', rej, { once: true });
  });
  ws.addEventListener('message', (ev) => {
    const m = JSON.parse(ev.data);
    if (m.id && pending.has(m.id)) {
      const { resolve, reject } = pending.get(m.id);
      pending.delete(m.id);
      if (m.error) reject(new Error(JSON.stringify(m.error)));
      else resolve(m.result);
    }
  });
  const { targetId } = await send('Target.createTarget', { url: 'about:blank' });
  sid = (await send('Target.attachToTarget', { targetId, flatten: true })).sessionId;
  await send('Page.enable', {}, sid);
  await send('Runtime.enable', {}, sid);
  await send('Network.enable', {}, sid);
  await send('Network.clearBrowserCookies', {}, sid);
}

async function evaluate(expression, awaitPromise = false) {
  const r = await send('Runtime.evaluate', { expression, returnByValue: true, awaitPromise }, sid);
  if (r.exceptionDetails) return `ERR ${JSON.stringify(r.exceptionDetails).slice(0, 200)}`;
  return r.result.value;
}

async function navigate(url) {
  await send('Emulation.setDeviceMetricsOverride', { width: WIDTH, height: 900, deviceScaleFactor: 1, mobile: false }, sid);
  await send('Page.navigate', { url }, sid);
  for (let i = 0; i < 100; i++) {
    await sleep(120);
    if ((await evaluate('document.readyState')) === 'complete') break;
  }
  await evaluate('document.fonts && document.fonts.ready ? document.fonts.ready.then(() => 1) : 1', true);
  await sleep(350);
}

async function capture(name) {
  const metrics = await send('Page.getLayoutMetrics', {}, sid);
  const height = Math.min(Math.ceil(metrics.cssContentSize.height), 7000);
  await send('Emulation.setDeviceMetricsOverride', { width: WIDTH, height, deviceScaleFactor: 1, mobile: false }, sid);
  await sleep(280);
  const { data } = await send('Page.captureScreenshot', { format: 'png', captureBeyondViewport: false }, sid);
  writeFileSync(join(OUT, `${name}.png`), Buffer.from(data, 'base64'));
  const title = await evaluate('document.title');
  console.log(`  ${name.padEnd(26)} ${String(height).padStart(5)}px  ${title}`);
}

async function shot(name, url) {
  await navigate(url);
  await capture(name);
}

async function login(base, username) {
  await navigate(`${base}/login`);
  await evaluate(`(() => {
    const f = document.querySelector('form');
    if (!f) return 'noform';
    f.querySelector('[name=username]').value = ${JSON.stringify(username)};
    f.querySelector('[name=password]').value = ${JSON.stringify(SHOP_PASSWORD)};
    f.requestSubmit();
    return 'ok';
  })()`);
  await sleep(1600);
}


/** 清掉会话 Cookie，换下一个演示账号 */
async function switchAccount() {
  await send('Network.clearBrowserCookies', {}, sid);
  await sleep(400);
}

try {
  await connect();
  console.log(`输出目录：${OUT}`);

  console.log('— 未登录 —');
  await shot('01-入口页', `${USER_BASE}/`);
  await shot('02-首页', `${USER_BASE}/main`);
  await shot('03-全部商品', `${USER_BASE}/category`);
  await shot('04-分类筛选', `${USER_BASE}/category?categoryId=2`);
  await shot('05-商品详情', `${USER_BASE}/product/1`);
  await shot('06-商品详情-用品', `${USER_BASE}/product/56`);
  await shot('07-登录页', `${USER_BASE}/login`);

  console.log('— 已登录 alice —');
  await login(USER_BASE, 'alice');
  await shot('08-首页-已登录', `${USER_BASE}/main`);
  await shot('09-购物车', `${USER_BASE}/cart`);
  await shot('11-订单列表', `${USER_BASE}/orders`);
  await shot('12-订单详情', `${USER_BASE}/orders/1`);
  await shot('13-个人资料', `${USER_BASE}/profile`);
  await shot('14-修改密码', `${USER_BASE}/profile/password`);

  // alice 的购物车故意混入了不可结算商品，用于演示拦截；
  // 结算页换 bob（购物车全部可结算）才能看到真实页面。
  console.log('— 已登录 bob —');
  await switchAccount();
  await login(USER_BASE, 'bob');
  await shot('10-结算页', `${USER_BASE}/checkout`);

  console.log('— 管理端 admin —');
  await login(ADMIN_BASE, 'admin');
  await shot('15-控制台', `${ADMIN_BASE}/dashboard`);
  await shot('16-商品管理', `${ADMIN_BASE}/products`);
  await shot('17-订单管理', `${ADMIN_BASE}/orders`);
  await shot('18-用户管理', `${ADMIN_BASE}/users`);

  console.log('完成。');
} finally {
  try { browser.kill(); } catch { /* 已退出 */ }
  await sleep(300);
  try { rmSync(profile, { recursive: true, force: true }); } catch { /* 临时目录可能被占用 */ }
}
