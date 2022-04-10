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
if not [%1]==[] (
    echo "set host"
    goto :serverHost
)
echo "localhost"
java -cp "lib/jade.jar;lib/json-simple-1.1.1.jar;class/item;class/utils;class" jade.Boot -nomtp -agents serverAgent:Server(files\server.json,files\serverOutput.csv) -gui

goto :eof

:serverHost
echo %1
java -cp "lib/jade.jar;lib/json-simple-1.1.1.jar;class/item;class/utils;class" jade.Boot -nomtp -local-host %1 -agents serverAgent:Server(files\server.json,files\serverOutput.csv)
goto :eof

:client
call utils.bat compile
echo "Running.."
java -cp "lib/jade.jar;lib/json-simple-1.1.1.jar;class/item;class/utils;class" jade.Boot -container -host %1 -port 1099 clientAgent:Client(files\client.json,files\output.csv)

goto :eof

:Main
if %1==server (
        call :server %2
) else if %1==client (
        call :client %2
)


:Exit
pause