#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_HOME="${JAVA_HOME:?JAVA_HOME is required}"

JAVA_HOME="$JAVA_HOME" mvn -q -nsu -f "$ROOT/pom.xml" clean package

if ! jar tf "$ROOT/system/target/AchievementSystem.jar" | rg -qx 'velocity-plugin.json'; then
  echo "AchievementSystem.jar is missing velocity-plugin.json." >&2
  exit 1
fi

mkdir -p \
  "$ROOT/dist/velocity" \
  "$ROOT/dist/lobby" \
  "$ROOT/dist/hook/paper" \
  "$ROOT/dist/hook/fabric" \
  "$ROOT/dist/integrations/maniac2"

cp "$ROOT/system/target/AchievementSystem.jar" "$ROOT/dist/velocity/AchievementSystem.jar"
cp "$ROOT/gui/target/AchievementGui.jar" "$ROOT/dist/lobby/AchievementGui.jar"
cp "$ROOT/hook-paper/target/AchievementHook.jar" "$ROOT/dist/hook/paper/AchievementHook.jar"
cp "$ROOT/hook-fabric/target/AchievementHook.jar" "$ROOT/dist/hook/fabric/AchievementHook.jar"
cp "$ROOT/integrations/maniac2-paper/target/ManiacAchievementHook.jar" \
  "$ROOT/dist/integrations/maniac2/ManiacAchievementHook.jar"

shasum -a 256 \
  "$ROOT/dist/velocity/AchievementSystem.jar" \
  "$ROOT/dist/lobby/AchievementGui.jar" \
  "$ROOT/dist/hook/paper/AchievementHook.jar" \
  "$ROOT/dist/hook/fabric/AchievementHook.jar" \
  "$ROOT/dist/integrations/maniac2/ManiacAchievementHook.jar"
