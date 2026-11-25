@echo off
rem Compile the application (Java 17+, no external libraries) and package it as the standalone Agent.jar.
if exist out rmdir /s /q out
mkdir out
dir /s /b src\main\*.java > sources.txt
javac --release 17 -d out @sources.txt
if errorlevel 1 exit /b 1
del sources.txt
jar --create --file Agent.jar --main-class finance.app.Main -C out .
echo Built into .\out and .\Agent.jar  (run: java -jar Agent.jar -help)
