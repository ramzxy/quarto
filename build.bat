@echo off
if not exist out mkdir out

echo Compiling sources...
javac -d out -sourcepath src src/Client/ClientApplication.java src/Server/ServerApplication.java -encoding UTF-8
if %errorlevel% neq 0 (
    echo Compilation failed!
    exit /b %errorlevel%
)

echo Creating client.jar...
"C:\Program Files\Java\jdk-23\bin\jar.exe" cfe client.jar Client.ClientApplication -C out .

echo Creating server.jar...
"C:\Program Files\Java\jdk-23\bin\jar.exe" cfe server.jar Server.ServerApplication -C out .

echo Build complete!
echo You can run them with:
echo   java -jar client.jar <args>
echo   java -jar server.jar <port>
