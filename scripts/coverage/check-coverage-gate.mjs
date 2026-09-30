#!/usr/bin/env node
// Coverage quality gate (LMKT-135): exits non-zero when line or branch coverage of any side in
// summary.json (written by build-coverage-site.mjs) is below its floor in coverage-baseline.json,
// so coverage can only hold or improve. A side missing from summary.json (a PR that only ran
// the other side's tests) is skipped. Kept separate from the site builder so each does one job.
//
//   node scripts/coverage/check-coverage-gate.mjs <summary.json> [baseline.json]

import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const summaryPath = process.argv[2];
const baselinePath = process.argv[3] ?? join(dirname(fileURLToPath(import.meta.url)), 'coverage-baseline.json');
if (!summaryPath) {
  console.error('Usage: check-coverage-gate.mjs <summary.json> [baseline.json]');
  process.exit(2);
}

const summary = JSON.parse(readFileSync(summaryPath, 'utf8'));
const baseline = JSON.parse(readFileSync(baselinePath, 'utf8'));

let failed = false;
let checked = 0;
for (const side of ['backend', 'frontend']) {
  if (!summary[side]) {
    console.log(`${side}: not run in this build, skipped`);
    continue;
  }
  for (const metric of ['line', 'branch']) {
    const actual = summary[side][metric];
    const floor = baseline[side][metric];
    const ok = actual >= floor;
    failed ||= !ok;
    checked++;
    console.log(`${ok ? 'PASS' : 'FAIL'}  ${side} ${metric} coverage ${actual.toFixed(2)}% (floor ${floor}%)`);
  }
}

if (checked === 0) {
  console.error('No coverage in summary.json to check.');
  process.exit(1);
}
if (failed) {
  console.error(`Coverage dropped below the baseline in ${baselinePath}. Add tests; do not lower the floor.`);
  process.exit(1);
}
