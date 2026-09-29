#!/usr/bin/env bash
set -euo pipefail

reports_dir="ci/reports"
mkdir -p "$reports_dir"

backend_files=$(find libs services -type f -path '*/target/site/jacoco/jacoco.csv' | sort || true)
has_backend=false
if [ -n "$backend_files" ]; then
  has_backend=true
  backend_totals=$(awk -F, '
    {
      line_missed += $8
      line_covered += $9
      branch_missed += $6
      branch_covered += $7
    }
    END {
      line_total = line_missed + line_covered
      branch_total = branch_missed + branch_covered
      line_pct = (line_total > 0) ? (line_covered * 100.0 / line_total) : 0
      branch_pct = (branch_total > 0) ? (branch_covered * 100.0 / branch_total) : 0
      printf "%d %d %d %d %.2f %.2f", line_covered, line_total, branch_covered, branch_total, line_pct, branch_pct
    }
  ' $backend_files)

  read -r backend_line_covered backend_line_total backend_branch_covered backend_branch_total backend_line_pct backend_branch_pct <<< "$backend_totals"
fi

frontend_lcov=$(find apps/frontend/coverage -type f -name 'lcov.info' | head -n 1 || true)
has_frontend=false
if [ -n "$frontend_lcov" ]; then
  has_frontend=true
  frontend_totals=$(awk -F: '
    /^LF:/ { lf += $2 }
    /^LH:/ { lh += $2 }
    END {
      pct = (lf > 0) ? (lh * 100.0 / lf) : 0
      printf "%d %d %.2f", lh, lf, pct
    }
  ' "$frontend_lcov")

  read -r frontend_lines_covered frontend_lines_total frontend_line_pct <<< "$frontend_totals"
fi

if [ "$has_backend" = false ] && [ "$has_frontend" = false ]; then
  echo "No coverage artifacts found from backend or frontend lanes."
  exit 1
fi

cat > "$reports_dir/coverage-summary.md" <<EOF
# Coverage Summary

Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

## Backend (JaCoCo aggregate)
EOF

if [ "$has_backend" = true ]; then
  cat >> "$reports_dir/coverage-summary.md" <<EOF
- Line coverage: ${backend_line_covered}/${backend_line_total} (${backend_line_pct}%)
- Branch coverage: ${backend_branch_covered}/${backend_branch_total} (${backend_branch_pct}%)
EOF
else
  cat >> "$reports_dir/coverage-summary.md" <<EOF
- Not collected in this run.
EOF
fi

cat >> "$reports_dir/coverage-summary.md" <<EOF

## Frontend (lcov)
EOF

if [ "$has_frontend" = true ]; then
  cat >> "$reports_dir/coverage-summary.md" <<EOF
- Line coverage: ${frontend_lines_covered}/${frontend_lines_total} (${frontend_line_pct}%)
- Source: ${frontend_lcov}
EOF
else
  cat >> "$reports_dir/coverage-summary.md" <<EOF
- Not collected in this run.
EOF
fi

echo "Coverage summary written to $reports_dir/coverage-summary.md"
