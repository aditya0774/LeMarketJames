@echo off
setlocal enabledelayedexpansion
for /f "tokens=*" %%A in ('cd') do set "currentDir=%%A"
start "" "%currentDir%\apps\backend\target\reports\apidocs\index.html"
