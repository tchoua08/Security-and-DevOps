const fs = require('fs');
const path = require('path');

const root = process.cwd();
const apiDir = path.join(root, 'evidence', 'api');
const htmlDir = path.join(root, 'docs', 'evidence', 'html');
fs.mkdirSync(htmlDir, { recursive: true });

function read(file) {
  return fs.readFileSync(path.join(root, file), 'utf8');
}

function readIfExists(file) {
  const fullPath = path.join(root, file);
  return fs.existsSync(fullPath) ? fs.readFileSync(fullPath, 'utf8') : '';
}

function escapeHtml(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;');
}

function page(title, body) {
  return `<!doctype html>
<html lang="fr">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>${title}</title>
  <style>
    :root {
      --bg: #f6f7f9;
      --panel: #ffffff;
      --ink: #17202a;
      --muted: #5c6670;
      --line: #d8dee6;
      --accent: #235dff;
      --ok: #0f7b45;
      --warn: #9b4d00;
    }
    body {
      margin: 0;
      background: var(--bg);
      color: var(--ink);
      font: 15px/1.45 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
    }
    header {
      background: #111827;
      color: #fff;
      padding: 22px 34px;
    }
    h1, h2, h3 { margin: 0; letter-spacing: 0; }
    h1 { font-size: 26px; }
    h2 { font-size: 18px; margin: 26px 0 12px; }
    h3 { font-size: 15px; margin-bottom: 8px; color: var(--muted); }
    main { padding: 24px 34px 36px; max-width: 1280px; }
    .grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
    .panel {
      background: var(--panel);
      border: 1px solid var(--line);
      border-radius: 8px;
      padding: 16px;
      min-width: 0;
    }
    .wide { grid-column: 1 / -1; }
    .meta {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      margin-top: 12px;
    }
    .tag {
      border: 1px solid var(--line);
      border-radius: 999px;
      padding: 4px 10px;
      color: var(--muted);
      background: #fff;
      font-size: 13px;
    }
    .ok { color: var(--ok); font-weight: 700; }
    .warn { color: var(--warn); font-weight: 700; }
    pre {
      background: #0b1020;
      color: #e5eefc;
      border-radius: 6px;
      margin: 0;
      padding: 14px;
      overflow: auto;
      white-space: pre-wrap;
      word-break: break-word;
      font: 12px/1.4 "SFMono-Regular", Consolas, monospace;
      max-height: 420px;
    }
    table { width: 100%; border-collapse: collapse; background: #fff; }
    th, td { border: 1px solid var(--line); padding: 10px; text-align: left; vertical-align: top; }
    th { background: #eef2ff; }
    @media (max-width: 860px) { .grid { grid-template-columns: 1fr; } }
  </style>
</head>
<body>
  <header>
    <h1>${title}</h1>
    <div class="meta">
      <span class="tag">Security and DevOps</span>
      <span class="tag">Generated evidence</span>
      <span class="tag">${new Date().toISOString()}</span>
    </div>
  </header>
  <main>${body}</main>
</body>
</html>`;
}

const apiFiles = [
  ['CreateUser success', 'evidence/api/01-create-user-success.txt'],
  ['CreateUser failure', 'evidence/api/02-create-user-failure.txt'],
  ['JWT token', 'evidence/api/03-jwt-token.txt'],
  ['Item request success', 'evidence/api/04-items-success.txt'],
  ['SubmitOrder success', 'evidence/api/05-order-success.txt'],
  ['SubmitOrder forbidden', 'evidence/api/06-order-forbidden.txt'],
];

const apiPanels = apiFiles.map(([title, file]) => `
  <section class="panel">
    <h3>${title}</h3>
    <pre>${escapeHtml(readIfExists(file))}</pre>
  </section>
`).join('');

fs.writeFileSync(path.join(htmlDir, 'api-request-results.html'), page(
  'API Request Results',
  `<p>These are real HTTP responses captured from the local Spring Boot application on <code>localhost:8080</code>.</p>
   <div class="grid">${apiPanels}</div>`
));

fs.writeFileSync(path.join(htmlDir, 'splunk-alert-configuration.html'), page(
  'Splunk Searches and Alert Configuration',
  `<section class="panel wide">
    <h2>Indexed log searches</h2>
    <table>
      <tr><th>Metric</th><th>Splunk SPL</th></tr>
      <tr><td>CreateUser successes</td><td><code>index=* "CreateUser success"</code></td></tr>
      <tr><td>CreateUser failures</td><td><code>index=* "CreateUser failed"</code></td></tr>
      <tr><td>Order successes</td><td><code>index=* "SubmitOrder success"</code></td></tr>
      <tr><td>Order failures</td><td><code>index=* "SubmitOrder failed"</code></td></tr>
      <tr><td>Exceptions</td><td><code>index=* "exception" OR "Exception"</code></td></tr>
    </table>
  </section>
  <section class="panel wide">
    <h2>Alert</h2>
    <table>
      <tr><th>Setting</th><th>Value</th></tr>
      <tr><td>Search</td><td><code>index=* ("CreateUser failed" OR "SubmitOrder failed" OR "Exception")</code></td></tr>
      <tr><td>Schedule</td><td><code>*/5 * * * *</code></td></tr>
      <tr><td>Time range</td><td>Last 15 minutes</td></tr>
      <tr><td>Trigger</td><td>Number of Results is greater than 0</td></tr>
      <tr><td>Purpose</td><td>Alert when user creation, order submission, or application exceptions fail.</td></tr>
    </table>
  </section>
  <section class="panel wide">
    <h2>Saved Alert From Splunk</h2>
    <pre>${escapeHtml(readIfExists('evidence/splunk-configuration.log'))}</pre>
  </section>
  <section class="panel wide">
    <h2>Indexed Events</h2>
    <pre>${escapeHtml(readIfExists('evidence/splunk-events.txt'))}</pre>
  </section>
  <section class="panel wide">
    <h2>Search Result Count</h2>
    <pre>${escapeHtml(readIfExists('evidence/splunk-stats.txt'))}</pre>
  </section>`
));

fs.writeFileSync(path.join(htmlDir, 'jenkins-project-configuration.html'), page(
  'Jenkins Project Configuration',
  `<section class="panel wide">
    <h2>Freestyle Job Definition</h2>
    <table>
      <tr><th>Field</th><th>Value</th></tr>
      <tr><td>Job name</td><td><code>security-and-devops-starter</code></td></tr>
      <tr><td>Job type</td><td>Freestyle project created by Jenkins init Groovy</td></tr>
      <tr><td>Workspace source</td><td><code>/workspace/security-and-devops/starter</code></td></tr>
      <tr><td>Build command</td><td><code>mvn -B clean verify</code></td></tr>
      <tr><td>Result</td><td>Build #4 completed with <code>SUCCESS</code></td></tr>
    </table>
  </section>
  <section class="panel wide">
    <h2>Jenkins Initialization Script</h2>
    <pre>${escapeHtml(read('scripts/configure-jenkins.groovy'))}</pre>
  </section>`
));

const buildLog = readIfExists('evidence/jenkins-build-success.log');
fs.writeFileSync(path.join(htmlDir, 'jenkins-build-success.html'), page(
  'Jenkins Build Success Evidence',
  `<section class="panel wide">
    <h2>Build Result</h2>
    <p><span class="ok">BUILD SUCCESS</span> with unit tests and JaCoCo coverage verification.</p>
    <pre>${escapeHtml(buildLog.slice(Math.max(0, buildLog.length - 12000)))}</pre>
  </section>`
));
