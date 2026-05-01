@echo off
echo ==========================================================
echo    Inizializzazione Ambiente VapourSynth Standalone
echo ==========================================================

set VS_DIR=%~dp0vapoursynth-portable
set PYTHON_EXE="%VS_DIR%\python.exe"
set CORE_PLUGINS_DIR="%VS_DIR%\vs-coreplugins"
set DLL_FOLDER="%CORE_PLUGINS_DIR%\models"

if not exist %VS_DIR% (
    echo.
    echo [1/4] Avvio installazione di VapourSynth e Python...
    PowerShell -NoProfile -ExecutionPolicy Bypass -Command "& '.\Install-Portable-VapourSynth-*.ps1'"
)

if not exist %PYTHON_EXE% (
    echo ERRORE: python.exe non trovato. L'installazione di VapourSynth ha fallito.
    pause
    exit /b
)

echo.
echo [2/4] Installazione librerie Python...
%PYTHON_EXE% -m pip install --upgrade pip setuptools wheel
%PYTHON_EXE% -m pip install -r requirements.txt

if not exist %DLL_FOLDER% (
    echo.
    echo [3/4] Download delle DLL necessarie...
    set DLL_ZIP_URL="https://www.windsofresub.cloud/BetterIMG/vs-coreplugins.zip"

    curl --ssl-no-revoke -L %DLL_ZIP_URL% -o "temp_dlls.zip" -#

    echo.
    echo Estrazione in corso...
    if not exist %CORE_PLUGINS_DIR% mkdir %CORE_PLUGINS_DIR%
    tar -xf "temp_dlls.zip" -C %CORE_PLUGINS_DIR%

    del "temp_dlls.zip"
)

echo.
echo [4/4] Setup completato con successo!

pause