#!/usr/bin/env bash
set -euo pipefail

reports_dir="ci/reports"
mkdir -p "$reports_dir"

inventory_csv="$reports_dir/test-inventory.csv"
by_name_txt="$reports_dir/duplicate-test-files-by-name.txt"
by_hash_txt="$reports_dir/duplicate-test-files-by-content.txt"

{
  echo "kind,path,sha256"
  find services libs apps -type f \( -name '*Test.java' -o -name '*.spec.ts' \) | sort | while read -r f; do
    kind="unit_or_integration"
    case "$f" in
      *IntegrationTest.java) kind="integration" ;;
      *.spec.ts) kind="frontend-unit" ;;
      *Test.java) kind="backend-test" ;;
    esac
    hash=$(sha256sum "$f" | awk '{print $1}')
    echo "$kind,$f,$hash"
  done
} > "$inventory_csv"

awk -F, 'NR > 1 { print $2 }' "$inventory_csv" \
  | awk -F/ '{ print $NF }' \
  | sort \
  | uniq -cd \
  | sed 's/^ *//' > "$by_name_txt" || true

awk -F, 'NR > 1 { print $3 }' "$inventory_csv" \
  | sort \
  | uniq -cd \
  | sed 's/^ *//' > "$by_hash_txt" || true

cat > "$reports_dir/test-inventory-summary.md" <<EOF
# Test Inventory Summary

Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

- Inventory file: ${inventory_csv}
- Duplicate file names: ${by_name_txt}
- Duplicate file content hashes: ${by_hash_txt}

Notes:
- Duplicate file name report highlights potential overlap candidates across modules.
- Duplicate content hash report highlights likely copy/paste duplicates.
- Final duplicate-removal decisions require behavior-level review by developers.
EOF

echo "Test inventory written to $reports_dir"