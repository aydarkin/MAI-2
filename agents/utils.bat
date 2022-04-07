@echo off
if [%1]==[] (
        echo "You must specify a command (e.g. compile, clean)"
        goto :Exit
) else (
        goto :Main
)

:compile
echo "Идет компиляция.."
if not exist "%~dp0class" mkdir "%~dp0class"
javac "src/utils/*.java" -cp "lib/jade.jar;lib/json-simple-1.1.1.jar;class/" -encoding utf-8 -d class
javac "src/*.java" -cp "lib/jade.jar;lib/json-simple-1.1.1.jar;class/" -encoding utf-8 -d class
goto :eof

:clean
echo "Cleaning up.."
del /s /q class\*.class 2> nul > nul
rmdir /s /q class
goto :eof

:Main
if %1==compile (
        call :compile
) else if %1==clean (
        call :clean
)

:Exit