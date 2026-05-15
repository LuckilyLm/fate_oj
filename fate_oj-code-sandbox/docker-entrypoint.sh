#!/bin/sh
set -eu

mkdir -p /run/isolate/locks

if [ "${SANDBOX_START_CG_KEEPER:-true}" = "true" ]; then
  /usr/local/sbin/isolate-cg-keeper &
fi

exec java -jar /app/fate-oj-code-sandbox.jar
