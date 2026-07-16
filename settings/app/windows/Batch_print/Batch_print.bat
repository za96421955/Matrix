@echo off
for /f "delims=" %%i in ('powershell -Command "$input='%1'|ConvertFrom-Json; Write-Output $input.print"') do echo %%i