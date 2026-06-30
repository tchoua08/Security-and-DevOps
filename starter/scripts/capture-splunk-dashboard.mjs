import { spawn } from 'node:child_process';
import { mkdir, rm, writeFile } from 'node:fs/promises';

const chromePath = '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome';
const port = 9223;
const profileDir = '/tmp/security-devops-splunk-chrome';
const outputPath = 'evidence/screenshots/splunk-dashboard.png';
const dashboardUrl = 'http://localhost:8000/en-US/app/search/security_devops_application_logs';
const loginUrl = `http://localhost:8000/en-US/account/login?return_to=${encodeURIComponent(dashboardUrl)}`;

await mkdir('evidence/screenshots', { recursive: true });
await rm(profileDir, { recursive: true, force: true });

const chrome = spawn(chromePath, [
  '--headless=new',
  '--disable-gpu',
  '--no-first-run',
  '--no-default-browser-check',
  '--window-size=1600,1200',
  `--user-data-dir=${profileDir}`,
  `--remote-debugging-port=${port}`,
  'about:blank',
], { stdio: ['ignore', 'ignore', 'pipe'] });

chrome.stderr.on('data', () => {});

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function getWebSocketUrl() {
  for (let i = 0; i < 50; i += 1) {
    try {
      const response = await fetch(`http://localhost:${port}/json/list`);
      const json = await response.json();
      const page = json.find((target) => target.type === 'page');
      if (page?.webSocketDebuggerUrl) {
        return page.webSocketDebuggerUrl;
      }
    } catch {
      await sleep(200);
    }
  }
  throw new Error('Chrome DevTools endpoint did not start');
}

const ws = new WebSocket(await getWebSocketUrl());
await new Promise((resolve, reject) => {
  ws.addEventListener('open', resolve, { once: true });
  ws.addEventListener('error', reject, { once: true });
});

let id = 0;
const pending = new Map();
ws.addEventListener('message', (event) => {
  const message = JSON.parse(event.data);
  if (message.id && pending.has(message.id)) {
    const { resolve, reject } = pending.get(message.id);
    pending.delete(message.id);
    if (message.error) {
      reject(new Error(message.error.message));
    } else {
      resolve(message.result);
    }
  }
});

function send(method, params = {}) {
  const messageId = ++id;
  ws.send(JSON.stringify({ id: messageId, method, params }));
  return new Promise((resolve, reject) => {
    pending.set(messageId, { resolve, reject });
  });
}

async function waitForLoad() {
  await sleep(3500);
}

await send('Page.enable');
await send('Runtime.enable');
await send('Emulation.setDeviceMetricsOverride', {
  width: 1600,
  height: 1200,
  deviceScaleFactor: 1,
  mobile: false,
});

await send('Page.navigate', { url: loginUrl });
await waitForLoad();

await send('Runtime.evaluate', {
  expression: `
    (() => {
      const inputs = [...document.querySelectorAll('input')];
      const username = inputs.find((input) => /user/i.test(input.name || input.placeholder || input.id)) || inputs[0];
      const password = inputs.find((input) => input.type === 'password') || inputs[1];
      username.focus();
      username.value = 'admin';
      username.dispatchEvent(new Event('input', { bubbles: true }));
      username.dispatchEvent(new Event('change', { bubbles: true }));
      password.focus();
      password.value = 'Password123!';
      password.dispatchEvent(new Event('input', { bubbles: true }));
      password.dispatchEvent(new Event('change', { bubbles: true }));
      const button = [...document.querySelectorAll('button,input[type=submit]')].find((item) => /sign in|login/i.test(item.textContent || item.value || '')) || document.querySelector('button,input[type=submit]');
      button.click();
      return true;
    })();
  `,
});

await sleep(6000);
await send('Page.navigate', { url: dashboardUrl });
await sleep(30000);

const screenshot = await send('Page.captureScreenshot', {
  format: 'png',
  captureBeyondViewport: false,
});

await writeFile(outputPath, Buffer.from(screenshot.data, 'base64'));
ws.close();
chrome.kill();
