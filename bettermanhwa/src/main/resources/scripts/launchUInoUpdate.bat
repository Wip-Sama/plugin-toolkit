@echo off
title BetterIMG - Interfaccia Grafica
echo ==========================================================
echo                Avvio BetterIMG (GUI)
echo ==========================================================
echo.

set VS_DIR=%~dp0vapoursynth-portable
set PYTHON_EXE="%VS_DIR%\python.exe"

if not exist %PYTHON_EXE% (
    echo [ERRORE] L'ambiente portable non e' stato trovato!
    echo Assicurati di aver eseguito prima il file di Setup.
    echo.
    exit /b
)

echo.
echo [1/1] Avvio interfaccia grafica...
%PYTHON_EXE% app_ui.py

echo.