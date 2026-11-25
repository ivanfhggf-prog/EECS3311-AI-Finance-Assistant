#!/usr/bin/env bash
# Compile the application (Java 17+, no external libraries) and package it as the standalone Agent.jar.
set -e
rm -rf out && mkdir -p out
javac --release 17 -d out $(find src/main -name '*.java')
jar --create --file Agent.jar --main-class finance.app.Main -C out .
echo "Built into ./out and ./Agent.jar  (run: java -jar Agent.jar -help)"
