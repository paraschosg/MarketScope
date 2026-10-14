#!/usr/bin/env bash
# Runs Maven with JDK 25 when neither is on PATH (Git Bash).
# Usage: tools/mvn.sh -q test
set -euo pipefail

if [ -z "${JAVA_HOME:-}" ] || [ ! -x "$JAVA_HOME/bin/java.exe" ]; then
  for candidate in "$HOME"/.jdks/*25*; do
    if [ -x "$candidate/bin/java.exe" ]; then JAVA_HOME="$candidate"; fi
  done
fi
if [ -z "${JAVA_HOME:-}" ]; then
  echo "No JDK 25 found. Set JAVA_HOME." >&2
  exit 1
fi
export JAVA_HOME

MVN="$(command -v mvn || true)"
if [ -z "$MVN" ]; then
  # IntelliJ's bundled Maven: machine-wide, per-user, or under JetBrains Toolbox.
  LOCAL="$(cygpath -u "${LOCALAPPDATA:-$HOME/AppData/Local}" 2>/dev/null || echo "$HOME/AppData/Local")"
  for candidate in "/c/Program Files/JetBrains/"*/plugins/maven-plugin/lib/maven3/bin/mvn.cmd \
                   "$LOCAL/Programs/"IntelliJ*/plugins/maven-plugin/lib/maven3/bin/mvn.cmd \
                   "$LOCAL/JetBrains/Toolbox/apps/"*/*/IntelliJ*/plugins/maven-plugin/lib/maven3/bin/mvn.cmd \
                   "$LOCAL/JetBrains/Toolbox/apps/"*IDEA*/plugins/maven-plugin/lib/maven3/bin/mvn.cmd; do
    if [ -f "$candidate" ]; then MVN="$candidate"; fi
  done
fi
if [ -z "$MVN" ]; then
  echo "No Maven found. Install Maven or IntelliJ IDEA." >&2
  exit 1
fi

cd "$(dirname "$0")/.."
exec "$MVN" "$@"
