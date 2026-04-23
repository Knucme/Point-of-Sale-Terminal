@echo off
set PATH=C:\Program Files\nodejs;%PATH%
echo Node version:
node --version
echo.
echo Installing server dependencies...
cd /d "C:\Users\CHEPI\Desktop\SE PROJECT\server"
npm install
echo.
echo Installing client dependencies...
cd /d "C:\Users\CHEPI\Desktop\SE PROJECT\client"
npm install
echo.
echo Done!
