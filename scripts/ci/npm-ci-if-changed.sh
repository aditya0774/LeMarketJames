#!/usr/bin/env sh
# Installs the npm packages of the folder it is run from, as `npm ci` does, but only when
# something changed. Jenkins keeps its workspace between builds, so a node_modules folder that
# already matches the lock file is reused instead of being deleted and downloaded again.
#
# The stamp records what the last install was made from: the Node version, package.json and the
# lock file. package.json is part of it so that an edit without a matching lock file still reaches
# `npm ci`, which refuses the two when they disagree. The stamp is kept inside node_modules, and
# `npm ci` deletes that folder before it installs, so a stamp is only ever found next to the
# packages it describes: an install that fails halfway leaves none.
#
# Plain sh: it also runs inside the Playwright image (see the Jenkinsfile's E2E stages).
set -eu

stamp_file=node_modules/.ci-install-stamp
stamp="$(node --version) $(sha256sum package.json package-lock.json)"

if [ -f "$stamp_file" ] && [ "$(cat "$stamp_file")" = "$stamp" ]; then
    echo "node_modules already matches package-lock.json; skipping npm ci"
    exit 0
fi

npm ci --no-audit --no-fund
echo "$stamp" > "$stamp_file"
