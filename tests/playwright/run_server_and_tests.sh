#!/bin/bash
set -e

if [ -x ./mvnw ]; then
  echo "Starting app with ./mvnw ..."
  nohup ./mvnw spring-boot:run > /tmp/rentcar-server.log 2>&1 &
else
  echo "Starting app with mvn ..."
  nohup mvn spring-boot:run > /tmp/rentcar-server.log 2>&1 &
fi
pid=$!
echo "server-pid:$pid"
for i in $(seq 1 30); do
  if curl -sSf http://localhost:8080/ > /dev/null 2>&1; then
    echo server-ready
    break
  fi
  sleep 2
done

if ! curl -sSf http://localhost:8080/ > /dev/null 2>&1; then
  echo 'Server did not become ready; tail log' >&2
  tail -n 200 /tmp/rentcar-server.log >&2
  exit 2
fi

echo 'Server ready; running Playwright'
cd tests/playwright
npm test --silent -- --reporter=list --workers=1
