#!/usr/bin/env bash
# Sets up a SonarQube server for this repo the way the course's Classroom Setup guide asks:
# the texoma-* quality profiles, the LeMarketJames-Project project, the Classroom Quality Gate
# and the webhook that tells Jenkins the result. Run it on the Linux box that runs SonarQube
# and Jenkins, from the repo root:
#
#   bash scripts/sonarqube/configure-sonarqube.sh
#
# It asks for the SonarQube admin password and stores it nowhere. It is safe to run again:
# whatever is missing is created, and the profiles and gate are reset to the values below.
#
# Optional environment variables:
#   SONAR_URL             default http://localhost:9000
#   SONAR_ADMIN_LOGIN     default admin
#   SONAR_ADMIN_PASSWORD  asked for when not set
#   JENKINS_URL           default http://<this machine's first IP address>:8080. Not localhost:
#                         SonarQube runs in a container, where localhost is the container
#                         itself, and SonarQube refuses loopback webhook addresses anyway.
set -euo pipefail

SONAR_URL="${SONAR_URL:-http://localhost:9000}"
SONAR_ADMIN_LOGIN="${SONAR_ADMIN_LOGIN:-admin}"
# Assigned by the instructor; must match sonar-project.properties.
PROJECT="LeMarketJames-Project"
GATE="Classroom Quality Gate"
# The course repository's profile backups. Its folder names really are spelled this way.
PROFILES_URL="https://raw.githubusercontent.com/bhickey777/FidelityLeapDailyReviews/main/Extras/SonarQube%20Configuration%20/quality-profilles"

if [ -z "${SONAR_ADMIN_PASSWORD:-}" ]; then
    read -rsp "SonarQube password for $SONAR_ADMIN_LOGIN: " SONAR_ADMIN_PASSWORD
    echo
fi
if [ -z "${JENKINS_URL:-}" ]; then
    JENKINS_URL="http://$(hostname -I | awk '{print $1}'):8080"
fi

# api <path> [curl arguments]: prints the response, and stops the script with SonarQube's own
# message when the call is refused.
api() {
    local out status
    out=$(curl -sS -u "$SONAR_ADMIN_LOGIN:$SONAR_ADMIN_PASSWORD" -w '\n%{http_code}' "$SONAR_URL$1" "${@:2}")
    status=${out##*$'\n'}
    out=${out%$'\n'*}
    if [ "$status" -ge 400 ]; then
        echo "SonarQube refused $1 (HTTP $status): $out" >&2
        return 1
    fi
    printf '%s\n' "$out"
}

echo "SonarQube at $SONAR_URL"
if ! curl -sS -m 10 "$SONAR_URL/api/system/status" | grep -q '"status":"UP"'; then
    echo "SonarQube is not up at $SONAR_URL. Start it and wait until /api/system/status says UP." >&2
    exit 1
fi
if ! api /api/authentication/validate | grep -q '"valid":true'; then
    echo "SonarQube did not accept that login." >&2
    exit 1
fi

# --- Quality profiles: language key, profile name inside the backup, backup file ------------
# The Python profile's name is misspelled in the course backup; it has to be used as it is.
profiles="css:texoma-css-profile:texoma-css-profile.xml
docker:texoma-docker-profile:texoma-docker-profile.xml
web:texoma-html-profile:texoma-html-profile.xml
java:texoma-java-profile:texoma-java-profile.xml
js:texoma-javascript-profile:texoma-javascript-profile.xml
py:texoma-python-profille:texoma-python-profile.xml
ts:texoma-typescript-profile:texoma-typescript-profile.xml"

download_dir=$(mktemp -d)
trap 'rm -rf "$download_dir"' EXIT
for entry in $profiles; do
    file=${entry##*:}
    curl -sS -f -o "$download_dir/$file" "$PROFILES_URL/$file"
    api /api/qualityprofiles/restore -F "backup=@$download_dir/$file" >/dev/null
    echo "Restored profile $file"
done

# --- Project ---------------------------------------------------------------------------------
if api /api/projects/search -G --data-urlencode "projects=$PROJECT" | grep -q "\"key\":\"$PROJECT\""; then
    echo "Project $PROJECT already exists"
else
    api /api/projects/create --data-urlencode "project=$PROJECT" --data-urlencode "name=$PROJECT" \
        --data-urlencode "mainBranch=main" >/dev/null
    echo "Created project $PROJECT"
fi
for entry in $profiles; do
    language=${entry%%:*}
    name=${entry#*:}
    name=${name%%:*}
    api /api/qualityprofiles/add_project --data-urlencode "language=$language" \
        --data-urlencode "qualityProfile=$name" --data-urlencode "project=$PROJECT" >/dev/null
done
echo "Assigned the seven profiles to $PROJECT"

# --- Quality gate ----------------------------------------------------------------------------
if ! api /api/qualitygates/list | grep -q "\"name\":\"$GATE\""; then
    api /api/qualitygates/create --data-urlencode "name=$GATE" >/dev/null
fi
# A new gate comes with SonarQube's default conditions, and a rerun finds the old ones; both
# are removed so the gate ends up with exactly the conditions below.
for id in $(api /api/qualitygates/show -G --data-urlencode "name=$GATE" | grep -o '"id":"[^"]*"' | cut -d'"' -f4); do
    api /api/qualitygates/delete_condition --data-urlencode "id=$id" >/dev/null
done
# Metric, operator (GT fails above the value, LT below it), value. The first four judge new
# code, the rest all code. From section 9 of the Classroom Setup guide.
conditions="new_violations:GT:5
new_security_hotspots_reviewed:LT:100
new_coverage:LT:80
new_duplicated_lines_density:GT:20
duplicated_lines_density:GT:30
software_quality_high_issues:GT:5
software_quality_medium_issues:GT:10
skipped_tests:GT:5"
for condition in $conditions; do
    metric=${condition%%:*}
    rest=${condition#*:}
    api /api/qualitygates/create_condition --data-urlencode "gateName=$GATE" \
        --data-urlencode "metric=$metric" --data-urlencode "op=${rest%%:*}" --data-urlencode "error=${rest##*:}" >/dev/null
done
# "Security Issue Severity on Overall Code is greater than or equal to Major". SonarQube stores
# severities as 5, 10, 15, 20, 25 (Info to Blocker), so "Major (15) or worse" is "above 14".
# Older versions do not have this metric; there the gate simply goes without it.
if ! api /api/qualitygates/create_condition --data-urlencode "gateName=$GATE" \
        --data-urlencode "metric=security_issue_severity" --data-urlencode "op=GT" --data-urlencode "error=14" >/dev/null; then
    echo "Skipped the security severity condition: this SonarQube version does not offer it."
fi
api /api/qualitygates/select --data-urlencode "gateName=$GATE" --data-urlencode "projectKey=$PROJECT" >/dev/null
echo "Quality gate \"$GATE\" set up and assigned to $PROJECT"

# --- Webhook: how the Jenkins "Quality Gate" stage hears the result -------------------------
if api /api/webhooks/list | grep -q '"name":"Jenkins"'; then
    echo "Webhook \"Jenkins\" already exists; left as it is (Administration > Configuration > Webhooks)"
else
    api /api/webhooks/create --data-urlencode "name=Jenkins" --data-urlencode "url=$JENKINS_URL/sonarqube-webhook/" >/dev/null
    echo "Created webhook \"Jenkins\" to $JENKINS_URL/sonarqube-webhook/"
fi

echo "Done. Next: create an analysis token for $PROJECT and add it to Jenkins (README, SonarQube)."
