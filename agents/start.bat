@echo off
chcp 65001 > nul
if [%1]==[] (
        echo "You must specify a command (server or client)"
        goto :Exit
) else (
        goto :Main
)

:server
call utils.bat compile
echo "Running.."

java -cp "lib/jade.jar;lib/json-simple-1.1.1.jar;class/item;class/utils;class" jade.Boot -nomtp -agents serverAgent:Server(files\server.json,files\serverOutput.json) -gui

goto :eof

:client
call utils.bat compile
echo "Running.."

java -cp "lib/jade.jar;lib/json-simple-1.1.1.jar;class/item;class/utils;class" jade.Boot -container -host localhost -port 1099 clientAgent:Client(files\client.json,files\output.json) -gui

goto :eof

:Main
if %1==server (
        call :server
) else if %1==client (
        call :client
)


:Exit
pause