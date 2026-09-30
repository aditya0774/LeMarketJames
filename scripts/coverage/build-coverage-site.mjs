#!/usr/bin/env node
// Builds the combined coverage site, so coverage can be read without running anything
// (LMKT-135). The GitHub coverage workflow publishes it to docs/coverage/ (GitHub Pages) and
// Jenkins attaches it to each build. Run from the repo root after `mvn -B test` and
// `cd apps/frontend && npx ng test --watch=false --coverage`:
//
//   node scripts/coverage/build-coverage-site.mjs [outDir] [--sides backend,frontend] [--no-history]
//
//   outDir        default docs/coverage
//   --sides       which reports to read (default both). Jenkins passes only the sides that ran
//                 this build, so stale reports left in its workspace are never read.
//   --no-history  skip the history (Jenkins builds are one-offs; the history lives on Pages)
//
// It copies every module's JaCoCo HTML report and the front-end report, then writes:
//   index.html    landing page: summary, lowest-covered classes, history
//   coverage.css  its styles, kept out of the HTML because Jenkins' Content-Security-Policy
//                 blocks inline styles on published reports
//   README.md     the same summary, rendered by github.com when browsing docs/coverage/
//   summary.json  the totals, read by check-coverage-gate.mjs
//   history.csv   one row per run; kept between runs so coverage can be shown over time
// Inputs are the plain-text reports (jacoco.csv, lcov.info), so no XML parsing is needed.

import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { execSync } from 'node:child_process';
import { join } from 'node:path';

const args = process.argv.slice(2);
const option = (name) => (args.includes(name) ? args[args.indexOf(name) + 1] : undefined);
const outDir = args[0] && !args[0].startsWith('--') ? args[0] : 'docs/coverage';
const sides = (option('--sides') ?? 'backend,frontend').split(',').filter(Boolean);
const withHistory = !args.includes('--no-history');
const frontendReportDir = 'apps/frontend/coverage/lemarket-ui';
const lowestCount = 10;

// Full HTML reports are linked by their GitHub Pages URL in README.md, because github.com
// shows an .html file's source instead of rendering it. Locally, relative links are used.
const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '').split('/');
const siteUrl = owner && repo ? `https://${owner}.github.io/${repo}/coverage/` : '';

const counts = () => ({ linesMissed: 0, linesCovered: 0, branchesMissed: 0, branchesCovered: 0 });
const add = (total, part) => Object.keys(total).forEach((key) => (total[key] += part[key]));
const pct = (covered, missed) => (covered + missed === 0 ? 100 : (100 * covered) / (covered + missed));
const linePct = (c) => pct(c.linesCovered, c.linesMissed);
const branchPct = (c) => pct(c.branchesCovered, c.branchesMissed);
const fmt = (value) => value.toFixed(2);

// --- Back end: one JaCoCo report per module under libs/ and services/ ---------------------
// Columns: GROUP,PACKAGE,CLASS,INSTRUCTION_MISSED,INSTRUCTION_COVERED,BRANCH_MISSED,
// BRANCH_COVERED,LINE_MISSED,LINE_COVERED,... (class and package names never contain commas).
function readBackendModules() {
  const modules = [];
  for (const parent of ['libs', 'services']) {
    if (!existsSync(parent)) continue;
    for (const name of readdirSync(parent).sort()) {
      const reportDir = join(parent, name, 'target', 'site', 'jacoco');
      const csvPath = join(reportDir, 'jacoco.csv');
      if (!existsSync(csvPath)) continue;
      const total = counts();
      const classes = [];
      for (const row of readFileSync(csvPath, 'utf8').trim().split(/\r?\n/).slice(1)) {
        const col = row.split(',');
        const c = {
          branchesMissed: +col[5], branchesCovered: +col[6],
          linesMissed: +col[7], linesCovered: +col[8],
        };
        add(total, c);
        classes.push({ module: name, name: `${col[1]}.${col[2]}`, ...c });
      }
      modules.push({ name, reportDir, total, classes });
    }
  }
  return modules;
}

// --- Front end: lcov.info from the Angular (Vitest) unit tests ---------------------------
function readFrontend() {
  const lcovPath = join(frontendReportDir, 'lcov.info');
  if (!existsSync(lcovPath)) return null;
  const total = counts();
  const files = [];
  let file = null;
  for (const line of readFileSync(lcovPath, 'utf8').split(/\r?\n/)) {
    const [key, value] = [line.slice(0, line.indexOf(':')), line.slice(line.indexOf(':') + 1)];
    if (key === 'SF') file = { module: 'frontend', name: value.replace(/\\/g, '/'), ...counts() };
    else if (!file) continue;
    else if (key === 'LF') file.linesMissed += +value;
    else if (key === 'LH') { file.linesCovered += +value; file.linesMissed -= +value; }
    else if (key === 'BRF') file.branchesMissed += +value;
    else if (key === 'BRH') { file.branchesCovered += +value; file.branchesMissed -= +value; }
    else if (line === 'end_of_record') { add(total, file); files.push(file); file = null; }
  }
  return { total, files };
}

// --- History: kept between runs; a rerun for the same commit replaces its row -------------
const historyHeader = 'date,commit,backend_line,backend_branch,frontend_line,frontend_branch';

function readHistory() {
  const path = join(outDir, 'history.csv');
  if (!existsSync(path)) return [];
  return readFileSync(path, 'utf8').trim().split(/\r?\n/).slice(1).filter(Boolean).map((row) => {
    const [date, commit, backendLine, backendBranch, frontendLine, frontendBranch] = row.split(',');
    return { date, commit, backendLine, backendBranch, frontendLine, frontendBranch };
  });
}

// The checked-out commit, not GITHUB_SHA: the workflow checks out the latest main, which can be
// newer than the push that triggered it.
function currentCommit() {
  try { return execSync('git rev-parse --short=8 HEAD').toString().trim(); } catch { return 'local'; }
}

// --- Build ---------------------------------------------------------------------------------
// A side left out of --sides is null and simply doesn't appear in the site.
const backend = sides.includes('backend') ? readBackendModules() : null;
const frontend = sides.includes('frontend') ? readFrontend() : null;
if ((backend && backend.length === 0) || (sides.includes('frontend') && !frontend) || (!backend && !frontend)) {
  console.error(`Missing coverage input for --sides ${sides.join(',')}: run \`mvn -B test\` and/or ` +
    'the front-end tests with --coverage first.');
  process.exit(1);
}

let backendTotal = null;
if (backend) {
  backendTotal = counts();
  backend.forEach((m) => add(backendTotal, m.total));
}
const allTotal = counts();
if (backendTotal) add(allTotal, backendTotal);
if (frontend) add(allTotal, frontend.total);

const run = {
  date: new Date().toISOString().slice(0, 10),
  commit: currentCommit(),
  backendLine: backendTotal ? fmt(linePct(backendTotal)) : '',
  backendBranch: backendTotal ? fmt(branchPct(backendTotal)) : '',
  frontendLine: frontend ? fmt(linePct(frontend.total)) : '',
  frontendBranch: frontend ? fmt(branchPct(frontend.total)) : '',
};
const history = withHistory ? readHistory() : [];
if (withHistory) {
  if (history.at(-1)?.commit === run.commit) history.pop();
  history.push(run);
}

const lowest = (items) => items.filter((i) => i.linesMissed > 0)
  .sort((a, b) => b.linesMissed - a.linesMissed).slice(0, lowestCount);
const lowestBackend = backend ? lowest(backend.flatMap((m) => m.classes)) : [];
const lowestFrontend = frontend ? lowest(frontend.files) : [];

// Copy the full reports fresh, so classes that no longer exist never linger in the site.
mkdirSync(outDir, { recursive: true });
rmSync(join(outDir, 'backend'), { recursive: true, force: true });
rmSync(join(outDir, 'frontend'), { recursive: true, force: true });
for (const m of backend ?? []) cpSync(m.reportDir, join(outDir, 'backend', m.name), { recursive: true });
if (frontend) cpSync(frontendReportDir, join(outDir, 'frontend'), { recursive: true });

const summaryRows = [
  ...(backend ?? []).map((m) => ({ label: m.name, href: `backend/${m.name}/index.html`, c: m.total })),
  ...(backendTotal ? [{ label: 'Back end (all modules)', c: backendTotal, strong: true }] : []),
  ...(frontend ? [{ label: 'Front end (Angular)', href: 'frontend/index.html', c: frontend.total, strong: true }] : []),
  ...(backend && frontend ? [{ label: 'Everything', c: allTotal, strong: true }] : []),
];
const newestFirst = [...history].reverse();
const commonNote = 'JaCoCo credits a module only for its own tests, so shared code in libs/ that the ' +
  'services’ tests exercise is under-reported.';

const totalsJson = (c) => (c ? { line: +fmt(linePct(c)), branch: +fmt(branchPct(c)) } : null);
writeFileSync(join(outDir, 'summary.json'), JSON.stringify({
  commit: run.commit, date: run.date,
  backend: totalsJson(backendTotal), frontend: totalsJson(frontend?.total),
}, null, 2) + '\n');
if (withHistory) {
  writeFileSync(join(outDir, 'history.csv'), [historyHeader,
    ...history.map((h) => [h.date, h.commit, h.backendLine, h.backendBranch, h.frontendLine, h.frontendBranch].join(','))]
    .join('\n') + '\n');
}
writeFileSync(join(outDir, 'README.md'), renderMarkdown());
writeFileSync(join(outDir, 'index.html'), renderHtml());
writeFileSync(join(outDir, 'coverage.css'), css());
console.log(`Coverage site written to ${outDir}:` +
  (backendTotal ? ` back end ${run.backendLine}% lines, ${run.backendBranch}% branches;` : '') +
  (frontend ? ` front end ${run.frontendLine}% lines, ${run.frontendBranch}% branches` : ''));

// --- Rendering -------------------------------------------------------------------------------
function renderMarkdown() {
  const link = (label, href) => (href ? `[${label}](${siteUrl ? siteUrl + href : href})` : label);
  const bold = (text, strong) => (strong ? `**${text}**` : text);
  const lines = [
    '# Code coverage',
    '',
    `Generated at commit \`${run.commit}\` on ${run.date} by [build-coverage-site.mjs](../../scripts/coverage/build-coverage-site.mjs). Do not edit by hand.`,
    '',
    siteUrl ? `Full clickable reports: ${siteUrl}` : 'Full reports: open `index.html` in this folder.',
    '',
    '## Summary',
    '',
    '| Area | Line % | Branch % | Lines missed / total |',
    '|---|---:|---:|---:|',
    ...summaryRows.map((r) => `| ${bold(link(r.label, r.href), r.strong)} | ${fmt(linePct(r.c))} | ${fmt(branchPct(r.c))} | ` +
      `${r.c.linesMissed} / ${r.c.linesMissed + r.c.linesCovered} |`),
    '',
  ];
  if (backend) {
    lines.push(`_${commonNote}_`, '',
      '## Lowest-covered back-end classes (most missed lines)', '',
      '| Module | Class | Lines missed | Line % |', '|---|---|---:|---:|',
      ...lowestBackend.map((c) => `| ${c.module} | \`${c.name}\` | ${c.linesMissed} | ${fmt(linePct(c))} |`), '');
  }
  if (frontend) {
    lines.push('## Lowest-covered front-end files (most missed lines)', '',
      '| File | Lines missed | Line % |', '|---|---:|---:|',
      ...lowestFrontend.map((f) => `| \`${f.name}\` | ${f.linesMissed} | ${fmt(linePct(f))} |`), '');
  }
  if (withHistory) {
    lines.push('## History (newest first)', '',
      '| Date | Commit | Back-end line % | Back-end branch % | Front-end line % | Front-end branch % |',
      '|---|---|---:|---:|---:|---:|',
      ...newestFirst.map((h) => `| ${h.date} | \`${h.commit}\` | ${h.backendLine} | ${h.backendBranch} | ${h.frontendLine} | ${h.frontendBranch} |`), '');
  }
  return lines.join('\n');
}

function renderHtml() {
  const esc = (s) => String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
  // <progress> draws the bar without inline style attributes, which Jenkins' CSP would block.
  const cell = (value) => `<td class="num"><progress max="100" value="${value.toFixed(1)}"></progress>${fmt(value)}</td>`;
  const table = (head, rows) => `<div class="scroll"><table>\n<thead><tr>${head}</tr></thead>\n<tbody>\n${rows}\n</tbody>\n</table></div>`;
  const th = (label, num) => `<th${num ? ' class="num"' : ''}>${label}</th>`;
  const td = (value, num) => `<td${num ? ' class="num"' : ''}>${value}</td>`;

  const summary = summaryRows.map((r) => {
    const label = r.href ? `<a href="${r.href}">${esc(r.label)}</a>` : esc(r.label);
    return `<tr${r.strong ? ' class="total"' : ''}>${td(label)}${cell(linePct(r.c))}${cell(branchPct(r.c))}` +
      `${td(`${r.c.linesMissed} / ${r.c.linesMissed + r.c.linesCovered}`, true)}</tr>`;
  }).join('\n');

  const sections = [
    '<h2>Summary</h2>',
    table(th('Area') + th('Line %', true) + th('Branch %', true) + th('Lines missed / total', true), summary),
  ];
  if (backend) {
    sections.push(`<p class="note">${esc(commonNote)}</p>`, '<h2>Lowest-covered back-end classes</h2>',
      table(th('Module') + th('Class') + th('Lines missed', true) + th('Line %', true), lowestBackend.map((c) =>
        `<tr>${td(esc(c.module))}${td(`<code>${esc(c.name)}</code>`)}${td(c.linesMissed, true)}${td(fmt(linePct(c)), true)}</tr>`).join('\n')));
  }
  if (frontend) {
    sections.push('<h2>Lowest-covered front-end files</h2>',
      table(th('File') + th('Lines missed', true) + th('Line %', true), lowestFrontend.map((f) =>
        `<tr>${td(`<code>${esc(f.name)}</code>`)}${td(f.linesMissed, true)}${td(fmt(linePct(f)), true)}</tr>`).join('\n')));
  }
  if (withHistory) {
    sections.push('<h2>History</h2>',
      table(th('Date') + th('Commit') + th('Back-end line %', true) + th('Back-end branch %', true) +
        th('Front-end line %', true) + th('Front-end branch %', true), newestFirst.map((h) =>
        `<tr>${td(esc(h.date))}${td(`<code>${esc(h.commit)}</code>`)}${td(h.backendLine, true)}${td(h.backendBranch, true)}` +
        `${td(h.frontendLine, true)}${td(h.frontendBranch, true)}</tr>`).join('\n')));
  }

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>LeMarketJames Coverage</title>
<link rel="stylesheet" href="coverage.css">
</head>
<body>
<main>
<h1>Code coverage</h1>
<p class="meta">Generated at commit <code>${esc(run.commit)}</code> on ${esc(run.date)}. Click an area to open its full report.</p>
${sections.join('\n')}
</main>
</body>
</html>
`;
}

function css() {
  return `:root { --bg: #ffffff; --fg: #1d2330; --muted: #5d6675; --line: #dde1e7; --accent: #2f6fdb; --track: #e8ebf0; --row: #f6f7f9; }
@media (prefers-color-scheme: dark) {
  :root { --bg: #14171c; --fg: #e4e7ec; --muted: #9aa3b2; --line: #2c323c; --accent: #6ea0f5; --track: #2a3039; --row: #1b1f26; }
}
body { margin: 0; background: var(--bg); color: var(--fg); font: 15px/1.5 system-ui, -apple-system, "Segoe UI", sans-serif; }
main { max-width: 960px; margin: 0 auto; padding: 32px 16px 48px; }
h1 { margin: 0 0 4px; font-size: 26px; }
h2 { margin: 36px 0 8px; font-size: 18px; }
p.meta, p.note { color: var(--muted); margin: 0 0 8px; }
a { color: var(--accent); }
.scroll { overflow-x: auto; }
table { border-collapse: collapse; width: 100%; }
th, td { padding: 6px 10px; border-bottom: 1px solid var(--line); text-align: left; vertical-align: middle; }
th { font-size: 13px; color: var(--muted); font-weight: 600; }
td.num, th.num { text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
tr.total td { font-weight: 600; background: var(--row); }
code { font-size: 13px; word-break: break-all; }
progress { width: 64px; height: 6px; margin-right: 8px; vertical-align: middle; border: 0; border-radius: 3px;
  background: var(--track); appearance: none; -webkit-appearance: none; overflow: hidden; }
progress::-webkit-progress-bar { background: var(--track); }
progress::-webkit-progress-value { background: var(--accent); }
progress::-moz-progress-bar { background: var(--accent); }
`;
}
