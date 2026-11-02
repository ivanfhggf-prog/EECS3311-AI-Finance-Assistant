#!/usr/bin/env bash
# Compile the application (Java 17+, no external libraries).
set -e
rm -rf out && mkdir -p out
javac -d out $(find src/main -name '*.java')
echo "Built into ./out"
