#!/usr/bin/env bash
# Usage: ./run.sh gui | ./run.sh cli | ./run.sh cli --script sample-data/demo-script.txt
[ -d out ] || ./build.sh
exec java -cp out finance.app.Main "$@"
