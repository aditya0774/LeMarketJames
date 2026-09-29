#!/usr/bin/env bash
set -euo pipefail

reports_dir="ci/reports"
coverage_file="$reports_dir/coverage-summary.md"
timing_file="$reports_dir/pipeline-timing.md"
compare_file="$reports_dir/baseline-comparison.md"

mkdir -p "$reports_dir"

if [ ! -f "$coverage_file" ]; then
  echo "Coverage summary missing at $coverage_file"
  exit 1
fi

if [ ! -f "$timing_file" ]; then
  echo "Pipeline timing report missing at $timing_file"
  exit 1
fi

backend_pct=$(awk '
  /## Backend/ { in_backend=1; next }
  /## Frontend/ { in_backend=0 }
  in_backend && /Line coverage:/ {
    if (match($0, /\(([0-9.]+)%\)/, m)) {
      print m[1]
      exit
    }
  }
' "$coverage_file")

frontend_pct=$(awk '
  /## Frontend/ { in_frontend=1; next }
  in_frontend && /Line coverage:/ {
    if (match($0, /\(([0-9.]+)%\)/, m)) {
      print m[1]
      exit
    }
  }
' "$coverage_file")

current_duration_ms=$(awk -F': ' '/- Duration ms:/ { print $2; exit }' "$timing_file")

baseline_backend_pct="${BASELINE_BACKEND_LINE_PCT:-}"
baseline_frontend_pct="${BASELINE_FRONTEND_LINE_PCT:-}"
baseline_duration_ms="${BASELINE_DURATION_MS:-}"
enforce_coverage="${ENFORCE_COVERAGE_BASELINE:-false}"

is_number() {
  [[ "$1" =~ ^[0-9]+([.][0-9]+)?$ ]]
}

is_integer() {
  [[ "$1" =~ ^[0-9]+$ ]]
}

less_than() {
  awk -v a="$1" -v b="$2" 'BEGIN { exit !(a < b) }'
}

delta_pct() {
  awk -v current="$1" -v base="$2" 'BEGIN { printf "%.2f", (current - base) }'
}

delta_ms() {
  awk -v current="$1" -v base="$2" 'BEGIN { printf "%.0f", (base - current) }'
}

backend_status="not-compared"
frontend_status="not-compared"
backend_delta="n/a"
frontend_delta="n/a"
timing_delta="n/a"

regression=false

if [ -n "$baseline_backend_pct" ] && is_number "$baseline_backend_pct" && [ -n "$backend_pct" ] && is_number "$backend_pct"; then
  backend_delta=$(delta_pct "$backend_pct" "$baseline_backend_pct")
  if less_than "$backend_pct" "$baseline_backend_pct"; then
    backend_status="regressed"
    regression=true
  else
    backend_status="ok"
  fi
fi

if [ -n "$baseline_frontend_pct" ] && is_number "$baseline_frontend_pct" && [ -n "$frontend_pct" ] && is_number "$frontend_pct"; then
  frontend_delta=$(delta_pct "$frontend_pct" "$baseline_frontend_pct")
  if less_than "$frontend_pct" "$baseline_frontend_pct"; then
    frontend_status="regressed"
    regression=true
  else
    frontend_status="ok"
  fi
fi

if [ -n "$baseline_duration_ms" ] && is_integer "$baseline_duration_ms" && [ -n "$current_duration_ms" ] && is_integer "$current_duration_ms"; then
  timing_delta=$(delta_ms "$current_duration_ms" "$baseline_duration_ms")
fi

cat > "$compare_file" <<EOF
# Baseline Comparison

Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

## Coverage
- Backend current line %: ${backend_pct:-n/a}
- Backend baseline line %: ${baseline_backend_pct:-n/a}
- Backend delta (current - baseline): ${backend_delta}
- Backend status: ${backend_status}

- Frontend current line %: ${frontend_pct:-n/a}
- Frontend baseline line %: ${baseline_frontend_pct:-n/a}
- Frontend delta (current - baseline): ${frontend_delta}
- Frontend status: ${frontend_status}

## Pipeline Timing
- Current duration ms: ${current_duration_ms:-n/a}
- Baseline duration ms: ${baseline_duration_ms:-n/a}
- Time saved ms (baseline - current): ${timing_delta}

## Enforcement
- Coverage baseline enforcement: ${enforce_coverage}
EOF

if [ "$enforce_coverage" = "true" ] && [ "$regression" = "true" ]; then
  echo "Coverage regression detected. See $compare_file"
  exit 1
fi

echo "Baseline comparison written to $compare_file"