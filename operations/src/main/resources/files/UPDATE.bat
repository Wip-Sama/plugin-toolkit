@echo off
setlocal enabledelayedexpansion
title BetterIMG - Controllo Aggiornamenti

set "APP_DIR=%~dp0"
set "LOCAL_VERSION_FILE=%APP_DIR%version.json"
set "VERSION_CHECK_URL=https://www.windsofresub.cloud/BetterIMG/version.json"
set "TEMP_REMOTE_JSON=%TEMP%\betterimg_remote_version.json"
set "TEMP_UPDATE_ZIP=%TEMP%\betterimg_update.zip"

if not exist "%LOCAL_VERSION_FILE%" (
    echo [UPDATE] version.json locale non trovato, skip aggiornamento.
    goto :EOF
)

for /f "usebackq delims=" %%V in (`powershell -NoProfile -Command ^
    "(Get-Content '%LOCAL_VERSION_FILE%' | ConvertFrom-Json).version"`) do (
    set "LOCAL_VER=%%V"
)

if "%LOCAL_VER%"=="" (
    echo [UPDATE] Impossibile leggere la versione locale.
    goto :EOF
)

echo [UPDATE] Versione locale: %LOCAL_VER%

echo [UPDATE] Controllo aggiornamenti da: %VERSION_CHECK_URL%
curl --ssl-no-revoke -fsSL "%VERSION_CHECK_URL%" -o "%TEMP_REMOTE_JSON%" 2>nul

if not exist "%TEMP_REMOTE_JSON%" (
    echo [UPDATE] Impossibile raggiungere il server. Salto aggiornamento.
    goto :EOF
)

for /f "usebackq delims=" %%V in (`powershell -NoProfile -Command ^
    "(Get-Content '%TEMP_REMOTE_JSON%' | ConvertFrom-Json).version"`) do (
    set "REMOTE_VER=%%V"
)

for /f "usebackq delims=" %%U in (`powershell -NoProfile -Command ^
    "(Get-Content '%TEMP_REMOTE_JSON%' | ConvertFrom-Json).download_url"`) do (
    set "DOWNLOAD_URL=%%U"
)

if "%REMOTE_VER%"=="" (
    echo [UPDATE] Impossibile leggere la versione remota.
    del "%TEMP_REMOTE_JSON%" 2>nul
    goto :EOF
)

echo [UPDATE] Versione disponibile: %REMOTE_VER%

for /f "usebackq delims=" %%R in (`powershell -NoProfile -Command ^
    "$l=[version]'%LOCAL_VER%'; $r=[version]'%REMOTE_VER%'; if($r -gt $l){'1'}else{'0'}"`) do (
    set "NEEDS_UPDATE=%%R"
)

if "%NEEDS_UPDATE%"=="0" (
    echo [UPDATE] Applicazione aggiornata. Nessun aggiornamento necessario.
    del "%TEMP_REMOTE_JSON%" 2>nul
    goto :EOF
)

echo.
echo ==========================================================
echo   Aggiornamento disponibile: v%LOCAL_VER% ^-^> v%REMOTE_VER%
echo ==========================================================
set /p "CONFIRM=Vuoi aggiornare ora? [Y/N]: "
if /i not "%CONFIRM%"=="Y" (
    echo [UPDATE] Aggiornamento annullato dall'utente.
    del "%TEMP_REMOTE_JSON%" 2>nul
    goto :EOF   
)

if "%DOWNLOAD_URL%"=="" (
    echo [UPDATE] ERRORE: download_url non trovato nel JSON remoto.
    del "%TEMP_REMOTE_JSON%" 2>nul
    goto :EOF
)

echo.
echo [UPDATE] Download aggiornamento da: %DOWNLOAD_URL%
curl --ssl-no-revoke -fL "%DOWNLOAD_URL%" -o "%TEMP_UPDATE_ZIP%" -#

if not exist "%TEMP_UPDATE_ZIP%" (
    echo [UPDATE] ERRORE: download fallito.
    del "%TEMP_REMOTE_JSON%" 2>nul
    goto :EOF   
)

echo.
echo [UPDATE] Installazione aggiornamento in: %APP_DIR%
set "APP_DIR_TAR=%APP_DIR:~0,-1%"
tar -xf "%TEMP_UPDATE_ZIP%" -C "%APP_DIR_TAR%"

if errorlevel 1 (
    echo [UPDATE] ERRORE: estrazione fallita.
    pause
) else (
    echo.
    echo [UPDATE] Aggiornamento v%REMOTE_VER% installato con successo!
    echo [UPDATE] Riavvia l'applicazione per usare la nuova versione.
)

del "%TEMP_REMOTE_JSON%" 2>nul
del "%TEMP_UPDATE_ZIP%" 2>nul

endlocal
