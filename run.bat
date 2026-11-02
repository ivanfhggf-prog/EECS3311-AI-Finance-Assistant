@echo off
rem Usage: run.bat gui ^| run.bat cli ^| run.bat cli --script sample-data\demo-script.txt
if not exist out call build.bat
java -cp out finance.app.Main %*
