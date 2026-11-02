#!/usr/bin/env bash
# Compiles main + test sources with plain javac (no dependencies) and runs the Stage 2 verification suite.
set -e
rm -rf out && mkdir -p out
javac -d out $(find src/main src/test -name '*.java')
java -cp out finance.TestRunner
