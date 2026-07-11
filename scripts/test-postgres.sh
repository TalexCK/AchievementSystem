#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PG_HOME="${PG_HOME:?PG_HOME is required}"
JAVA_HOME="${JAVA_HOME:?JAVA_HOME is required}"
PORT="${ACHIEVEMENT_TEST_PORT:-55439}"
WORK="$(mktemp -d /tmp/achievement-postgres.XXXXXX)"
DATA="$WORK/data"
LOG="$WORK/postgresql.log"
PG_CTL="$PG_HOME/bin/pg_ctl"
INITDB="$PG_HOME/bin/initdb"
CREATEDB="$PG_HOME/bin/createdb"
USER="achievement_test"
PASSWORD="integration-test"
PASSWORD_FILE="$WORK/password"

cleanup() {
  if "$PG_CTL" --pgdata="$DATA" status >/dev/null 2>&1; then
    "$PG_CTL" --pgdata="$DATA" --wait stop >/dev/null
  fi
  rm -rf "$WORK"
}

trap cleanup EXIT INT TERM

printf '%s\n' "$PASSWORD" >"$PASSWORD_FILE"
"$INITDB" \
  --pgdata="$DATA" \
  --username="$USER" \
  --pwfile="$PASSWORD_FILE" \
  --auth-local=scram-sha-256 \
  --auth-host=scram-sha-256 \
  --no-locale \
  --encoding=UTF8 \
  >/dev/null
rm -f "$PASSWORD_FILE"
"$PG_CTL" \
  --pgdata="$DATA" \
  --log="$LOG" \
  --options="-h 127.0.0.1 -p $PORT" \
  --wait \
  start >/dev/null
PGPASSWORD="$PASSWORD" \
  "$CREATEDB" --host=127.0.0.1 --port="$PORT" --username="$USER" achievements

ACHIEVEMENT_TEST_DATABASE_URL="jdbc:postgresql://127.0.0.1:$PORT/achievements" \
ACHIEVEMENT_TEST_DATABASE_USER="$USER" \
ACHIEVEMENT_TEST_DATABASE_PASSWORD="$PASSWORD" \
JAVA_HOME="$JAVA_HOME" \
  mvn -q -nsu -f "$ROOT/pom.xml" -pl system -am test
