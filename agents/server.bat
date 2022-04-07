@echo off

@REM if not exist "%~dp0class\*.class" (
@REM         echo "No '*.class' files found in 'class' directory. Compiling.."
@REM         call utils.bat compile
@REM )
call utils.bat compile
set /p in_file="Enter input filename: "
echo "Running.."
chcp 65001 > nul
java -cp "lib/jade.jar;lib/json-simple-1.1.1.jar;class/utils;class" jade.Boot -nomtp -agents serverAgent:Server(%in_file%)
chcp 866 > nul

pause