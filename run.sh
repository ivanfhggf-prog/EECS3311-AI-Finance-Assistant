#!/usr/bin/env bash
# Usage: ./run.sh gui | ./run.sh cli | ./run.sh -LLMAPI -input sample-data/task.txt   (same options as java -jar Agent.jar)
[ -d out ] || ./build.sh
exec java -cp out finance.app.Main "$@"
