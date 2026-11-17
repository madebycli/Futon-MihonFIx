#!/usr/bin/env bash
set -euo pipefail
cd "$RUNNER_TEMP/futon-preview"
exec 9>"$RUNNER_TEMP/futon-preview-gradle.lock"
flock 9
LOG="$RUNNER_TEMP/preview-gradle-$(date +%s%N).log"
RESULT=1
for ATTEMPT in 1 2 3; do
  set +e
  ./gradlew -Dorg.gradle.java.home="$JAVA_HOME" "$@" --stacktrace --no-configuration-cache 2>&1 | tee "$LOG"
  RESULT=${PIPESTATUS[0]}
  set -e
  if [ "$RESULT" -eq 0 ]; then break; fi
  if ! grep -Eq '429|Too Many Requests|could not resolve plugin artifact' "$LOG"; then break; fi
done
echo "EXIT=$RESULT" | tee -a "$LOG"
exit "$RESULT"
