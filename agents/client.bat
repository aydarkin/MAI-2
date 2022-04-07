@echo off

@REM if not exist "%~dp0class\*.class" (
@REM         echo "No '*.class' files found in 'class' directory. Compiling.."
@REM         call utils.bat compile
@REM )
call utils.bat compile
set /p address="Enter server address: "
set /p port="Enter server port: "
set /p in_file="Enter input filename (item names): "
set /p out_file="Enter output filename: "

echo "Running.."
chcp 65001 > nul
java -cp "lib/jade.jar;class/utils;class" jade.Boot -container -host %address% -port %port% clientAgent:Client(%in_file%,%out_file%)
chcp 866 > nul

pause