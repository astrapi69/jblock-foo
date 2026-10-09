#!/usr/bin/env bash
# Builds the command lines measure.sh compares: 0.1.0, 0.2.0 and 0.3.0 from their release tags, and
# the commit under test with the full gate. Run from a clone of lethenon; the worktrees go next to
# this script.
#   bash docs/launch/compatibility-0.4.0/build.sh [<commit under test, default HEAD>]
set -uo pipefail
C=$(cd "$(dirname "$0")" && pwd)
UNDER_TEST=${1:-HEAD}
for v in 0.1.0 0.2.0 0.3.0; do
  [ -d "$C/wt-$v" ] || git worktree add -q --detach "$C/wt-$v" "RELEASE-$v"
  (cd "$C/wt-$v" && ./gradlew installDist -x test --no-watch-fs -q > "$C/build-$v.log" 2>&1; echo "installDist $v exit=$?" | tee -a "$C/build-$v.log")
done
[ -d "$C/wt-0.4.0" ] || git worktree add -q --detach "$C/wt-0.4.0" "$UNDER_TEST"
(cd "$C/wt-0.4.0" && ./gradlew clean build installDist --no-watch-fs > "$C/build-0.4.0.log" 2>&1; echo "build 0.4.0 exit=$?" | tee -a "$C/build-0.4.0.log")
grep -E "^test:" "$C/build-0.4.0.log"
