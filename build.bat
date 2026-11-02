@echo off
rem Compile the application (Java 17+, no external libraries).
if exist out rmdir /s /q out
mkdir out
dir /s /b src\main\*.java > sources.txt
javac -d out @sources.txt
del sources.txt
echo Built into .\out
