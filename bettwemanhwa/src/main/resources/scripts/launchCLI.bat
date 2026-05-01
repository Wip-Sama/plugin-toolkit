@echo off
title BetterIMG - Modalita' CLI
echo ==========================================================
echo                Avvio BetterIMG (CLI)
echo ==========================================================
echo.

set VS_DIR=%~dp0vapoursynth-portable
set PYTHON_EXE="%VS_DIR%\python.exe"

if not exist %PYTHON_EXE% (
    echo [ERRORE] L'ambiente portable non e' stato trovato!
    echo Assicurati di aver eseguito prima il file di Setup.
    echo.
    pause
    exit /b
)

echo [1/2] Controllo aggiornamenti...
call "%~dp0UPDATE.bat"

echo.
echo [2/2] Avvio modalita' CLI...
%PYTHON_EXE% upscaler_core.py %*

echo.
pause