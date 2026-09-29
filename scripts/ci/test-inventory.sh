#!/usr/bin/env bash
set -euo pipefail

reports_dir="ci/reports"
mkdir -p "$reports_dir"

inventory_csv="$reports_dir/test-inventory.csv"
by_name_txt="$reports_dir/duplicate-test-files-by-name.txt"
by_hash_txt="$reports_dir/duplicate-test-files-by-content.txt"
java_sig_csv="$reports_dir/java-test-signatures.csv"
ts_title_csv="$reports_dir/ts-test-titles.csv"
java_dupes_txt="$reports_dir/duplicate-java-test-signatures.txt"
ts_dupes_txt="$reports_dir/duplicate-ts-test-titles.txt"
pr_mapping_md="$reports_dir/removed-tests-mapping-template.md"

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

# Extract Java method-level test signatures as stronger overlap candidates.
{
  echo "path,test_signature"
  find services libs -type f -name '*Test.java' | sort | while read -r f; do
    awk -v p="$f" '
      /@Test/ { in_test=1; next }
      in_test && /^[[:space:]]*(public|protected|private)?[[:space:]]*(void|[A-Za-z0-9_<>,\[\]?]+)[[:space:]]+[A-Za-z_][A-Za-z0-9_]*[[:space:]]*\(/ {
        line=$0
        gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
        sub(/[[:space:]]*\{[[:space:]]*$/, "", line)
        print p ",\"" line "\""
        in_test=0
      }
      in_test && /^[[:space:]]*$/ { next }
      in_test && !/@Test/ && /;/ { in_test=0 }
    ' "$f"
  done
} > "$java_sig_csv"

# Extract TS/Jest/Karma test titles as overlap candidates.
{
  echo "path,test_title"
  find apps -type f -name '*.spec.ts' | sort | while read -r f; do
    awk -v p="$f" '
      {
        if (match($0, /[[:space:]]*(it|test)\([[:space:]]*"[^"]+"/, m)) {
          title=m[0]
          sub(/^[[:space:]]*(it|test)\([[:space:]]*"/, "", title)
          print p ",\"" title "\""
        } else if (match($0, /[[:space:]]*(it|test)\([[:space:]]*\047[^\047]+\047/, m2)) {
          title=m2[0]
          sub(/^[[:space:]]*(it|test)\([[:space:]]*\047/, "", title)
          print p ",\"" title "\""
        }
      }
    ' "$f"
  done
} > "$ts_title_csv"

awk -F, 'NR > 1 { print $2 }' "$inventory_csv" \
  | awk -F/ '{ print $NF }' \
  | sort \
  | uniq -cd \
  | sed 's/^ *//' > "$by_name_txt" || true

awk -F, 'NR > 1 { print $3 }' "$inventory_csv" \
  | sort \
  | uniq -cd \
  | sed 's/^ *//' > "$by_hash_txt" || true

awk -F, 'NR > 1 { print $2 }' "$java_sig_csv" \
  | sed 's/^"//; s/"$//' \
  | sort \
  | uniq -cd \
  | sed 's/^ *//' > "$java_dupes_txt" || true

awk -F, 'NR > 1 { print $2 }' "$ts_title_csv" \
  | sed 's/^"//; s/"$//' \
  | sort \
  | uniq -cd \
  | sed 's/^ *//' > "$ts_dupes_txt" || true

cat > "$pr_mapping_md" <<EOF
# Removed/Merged Test Mapping (Fill In For PR)

Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

Use this table for every removed or merged test.

| Removed/Merged Test | File | Covered By Test | Covering File | Behavior Equivalence Note | Reviewer |
|---|---|---|---|---|---|
| | | | | | |

Guardrails:
- Remove only tests that verify exactly the same behavior.
- Do not lower coverage thresholds.
- Do not skip or disable tests to get green.
- If any flaky test is quarantined, add ticket id and owner in PR notes.
EOF

cat > "$reports_dir/test-inventory-summary.md" <<EOF
# Test Inventory Summary

Generated: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

- Inventory file: ${inventory_csv}
- Duplicate file names: ${by_name_txt}
- Duplicate file content hashes: ${by_hash_txt}
- Java test signatures: ${java_sig_csv}
- Duplicate Java test signatures: ${java_dupes_txt}
- TS test titles: ${ts_title_csv}
- Duplicate TS test titles: ${ts_dupes_txt}
- PR mapping template: ${pr_mapping_md}

Notes:
- Duplicate file name report highlights potential overlap candidates across modules.
- Duplicate content hash report highlights likely copy/paste duplicates.
- Method/title-level reports provide stronger overlap candidates but still require behavior-level review.
- Final duplicate-removal decisions require behavior-level review by developers.
EOF

echo "Test inventory written to $reports_dir"