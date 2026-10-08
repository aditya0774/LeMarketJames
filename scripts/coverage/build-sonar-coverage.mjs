#!/usr/bin/env node
// Writes the one coverage report SonarQube reads (sonar.coverageReportPaths in
// sonar-project.properties), in SonarQube's generic coverage format. Run from the repo root
// after the unit tests of both sides, with coverage:
//
//   node scripts/coverage/build-sonar-coverage.mjs [outFile]     default target/sonar/coverage.xml
//
// Why SonarQube is not pointed at the JaCoCo and lcov reports themselves: neither names a file
// by its path from the repo root (JaCoCo gives package/Class.java, lcov a path under
// apps/frontend), and SonarQube then takes the first file whose path ends that way. Five
// services have a com/lemarketjames/config/SecurityConfig.java, and the two Angular apps share
// paths such as src/app/app.ts, so coverage would be credited to the wrong file. Here every
// path starts at the repo root, so nothing is guessed.

import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';

const outFile = process.argv[2] ?? 'target/sonar/coverage.xml';
const frontendDir = 'apps/frontend';
// Both lcov reports name their files relative to apps/frontend.
const lcovReports = ['coverage/lemarket-ui/lcov.info', 'coverage/staff/lcov.info'];

// file path from the repo root -> line number -> { covered, branches, coveredBranches }
const files = new Map();
function lineOf(path, number) {
  if (!files.has(path)) files.set(path, new Map());
  const lines = files.get(path);
  if (!lines.has(number)) lines.set(number, { covered: false, branches: 0, coveredBranches: 0 });
  return lines.get(number);
}

// --- Back end: one JaCoCo XML report per module under libs/ and services/ ------------------
// <package name="com/x"> ... <sourcefile name="Y.java"> <line nr mi ci mb cb/> ...
// mi/ci are missed/covered instructions, mb/cb missed/covered branches.
function readJacoco() {
  let reports = 0;
  for (const parent of ['libs', 'services']) {
    if (!existsSync(parent)) continue;
    for (const name of readdirSync(parent).sort()) {
      const xmlPath = join(parent, name, 'target', 'site', 'jacoco', 'jacoco.xml');
      if (!existsSync(xmlPath)) continue;
      reports++;
      const xml = readFileSync(xmlPath, 'utf8');
      for (const [, pkg, body] of xml.matchAll(/<package name="([^"]*)">(.*?)<\/package>/gs)) {
        for (const [, source, rows] of body.matchAll(/<sourcefile name="([^"]*)">(.*?)<\/sourcefile>/gs)) {
          const path = `${parent}/${name}/src/main/java/${pkg}/${source}`;
          for (const [, nr, , ci, mb, cb] of rows.matchAll(/<line nr="(\d+)" mi="(\d+)" ci="(\d+)" mb="(\d+)" cb="(\d+)"\/>/g)) {
            const line = lineOf(path, +nr);
            line.covered = +ci > 0;
            line.branches = +mb + +cb;
            line.coveredBranches = +cb;
          }
        }
      }
    }
  }
  return reports;
}

// --- Front end: lcov.info from each Angular app's unit tests -------------------------------
// SF:<file>, then DA:<line>,<hits> and BRDA:<line>,<block>,<branch>,<taken or ->.
function readLcov(lcovPath) {
  if (!existsSync(lcovPath)) return 0;
  let path = '';
  for (const row of readFileSync(lcovPath, 'utf8').split(/\r?\n/)) {
    if (row.startsWith('SF:')) {
      // Windows runs write backslashes; SonarQube paths always use forward slashes.
      path = `${frontendDir}/${row.slice(3).replaceAll('\\', '/')}`;
    } else if (row.startsWith('DA:')) {
      const [nr, hits] = row.slice(3).split(',');
      if (+hits > 0) lineOf(path, +nr).covered = true;
      else lineOf(path, +nr);
    } else if (row.startsWith('BRDA:')) {
      const [nr, , , taken] = row.slice(5).split(',');
      const line = lineOf(path, +nr);
      line.branches++;
      if (+taken > 0) line.coveredBranches++;
    }
  }
  return 1;
}

const jacocoReports = readJacoco();
const lcovRead = lcovReports.map((report) => readLcov(join(frontendDir, report))).reduce((a, b) => a + b, 0);
// An empty report would be published as "0% covered", which is worse than a failed stage.
if (jacocoReports === 0 || lcovRead < lcovReports.length) {
  console.error(`Missing coverage reports: found ${jacocoReports} JaCoCo and ${lcovRead} of ${lcovReports.length} lcov. Run the unit tests with coverage first.`);
  process.exit(1);
}

const escape = (text) => text.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;');
const out = ['<coverage version="1">'];
for (const path of [...files.keys()].sort()) {
  out.push(`  <file path="${escape(path)}">`);
  const lines = files.get(path);
  for (const number of [...lines.keys()].sort((a, b) => a - b)) {
    const { covered, branches, coveredBranches } = lines.get(number);
    const branchPart = branches > 0 ? ` branchesToCover="${branches}" coveredBranches="${coveredBranches}"` : '';
    out.push(`    <lineToCover lineNumber="${number}" covered="${covered}"${branchPart}/>`);
  }
  out.push('  </file>');
}
out.push('</coverage>');

mkdirSync(dirname(outFile), { recursive: true });
writeFileSync(outFile, out.join('\n') + '\n');
console.log(`Wrote ${outFile}: ${files.size} files from ${jacocoReports} JaCoCo and ${lcovRead} lcov reports`);
