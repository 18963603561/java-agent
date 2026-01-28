@echo off
setlocal EnableDelayedExpansion

set "SCRIPT_DIR=%~dp0"
if "%JAVA_AGENT_HOME%"=="" (
  for %%I in ("%SCRIPT_DIR%..") do set "JAVA_AGENT_HOME=%%~fI"
)
if "%JAVA_AGENT_HOME%"=="" if not "%TOOLBOX_HOME%"=="" set "JAVA_AGENT_HOME=%TOOLBOX_HOME%"
set "TOOLBOX_HOME=%JAVA_AGENT_HOME%"

set "CONF_FILE=%JAVA_AGENT_HOME%\conf\java-agent.conf"
if not exist "%CONF_FILE%" (
  echo Config file not found: %CONF_FILE%
  exit /b 1
)

call :load_config "%CONF_FILE%"

if "%JAVA_HOME%"=="" (
  echo JAVA_HOME is required in conf\java-agent.conf
  exit /b 1
)
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo JAVA_HOME invalid or java.exe not found: %JAVA_HOME%\bin\java.exe
  exit /b 1
)

if "%JAVA_AGENT_CONFIG%"=="" if not "%TOOLBOX_CONFIG%"=="" set "JAVA_AGENT_CONFIG=%TOOLBOX_CONFIG%"
if "%JAVA_AGENT_CONFIG%"=="" set "JAVA_AGENT_CONFIG=%JAVA_AGENT_HOME%\conf\application.yml"
call :resolve_path "%JAVA_AGENT_CONFIG%" JAVA_AGENT_CONFIG
if not exist "%JAVA_AGENT_CONFIG%" (
  echo Application config not found: %JAVA_AGENT_CONFIG%
  exit /b 1
)

if "%JAVA_AGENT_TOOLSFILES%"=="" if not "%TOOLBOX_TOOLSFILES%"=="" set "JAVA_AGENT_TOOLSFILES=%TOOLBOX_TOOLSFILES%"
if "%JAVA_AGENT_TOOLSFILES%"=="" if not "%TOOLBOX_TOOLSFILE%"=="" set "JAVA_AGENT_TOOLSFILES=%TOOLBOX_TOOLSFILE%"
if not "%JAVA_AGENT_TOOLSFILES%"=="" call :resolve_list "%JAVA_AGENT_TOOLSFILES%" JAVA_AGENT_TOOLSFILES

set "JAVA_CMD=%JAVA_HOME%\bin\java.exe"
set "JAR_FILE=%JAVA_AGENT_HOME%\lib\java-agent.jar"
set "PID_FILE=%JAVA_AGENT_HOME%\run\java-agent.pid"
set "LOG_FILE=%JAVA_AGENT_HOME%\logs\java-agent.out"
set "LOG_ERR_FILE=%JAVA_AGENT_HOME%\logs\java-agent.err"

if not exist "%JAVA_AGENT_HOME%\logs" mkdir "%JAVA_AGENT_HOME%\logs"
if not exist "%JAVA_AGENT_HOME%\run" mkdir "%JAVA_AGENT_HOME%\run"

if not "%SERVER_PORT%"=="" (
  set "APP_OPTS_PAD= !APP_OPTS! "
  echo(!APP_OPTS_PAD! | findstr /C:" --server.port=" >nul
  if errorlevel 1 (
    if "!APP_OPTS!"=="" (
      set "APP_OPTS=--server.port=%SERVER_PORT%"
    ) else (
      set "APP_OPTS=!APP_OPTS! --server.port=%SERVER_PORT%"
    )
  )
)
if not "%UPLOAD_URL%"=="" (
  set "APP_OPTS_PAD= !APP_OPTS! "
  echo(!APP_OPTS_PAD! | findstr /C:" --upload.url=" >nul
  if errorlevel 1 (
    if "!APP_OPTS!"=="" (
      set "APP_OPTS=--upload.url=%UPLOAD_URL%"
    ) else (
      set "APP_OPTS=!APP_OPTS! --upload.url=%UPLOAD_URL%"
    )
  )
)
if not "%JAVA_AGENT_TOOLSFILES%"=="" (
  set "APP_OPTS_PAD= !APP_OPTS! "
  echo(!APP_OPTS_PAD! | findstr /C:" --java-agent.toolsFiles=" >nul
  if errorlevel 1 (
    if "!APP_OPTS!"=="" (
      set "APP_OPTS=--java-agent.toolsFiles=%JAVA_AGENT_TOOLSFILES%"
    ) else (
      set "APP_OPTS=!APP_OPTS! --java-agent.toolsFiles=%JAVA_AGENT_TOOLSFILES%"
    )
  )
)

set "CMD=%~1"
if "%CMD%"=="" goto :usage
shift /1

if /I "%CMD%"=="start" goto :start
if /I "%CMD%"=="stop" goto :stop
if /I "%CMD%"=="restart" goto :restart
if /I "%CMD%"=="status" goto :status
if /I "%CMD%"=="run" goto :run
if /I "%CMD%"=="update" goto :update
goto :usage

:start
call :is_running
if "%RUNNING%"=="1" (
  echo Already running: pid=%PID%
  exit /b 0
)
if not exist "%JAR_FILE%" (
  echo Jar not found: %JAR_FILE%
  exit /b 1
)
powershell -NoProfile -Command ^
  "$p=Start-Process -FilePath '%JAVA_CMD%' -ArgumentList '%JAVA_OPTS% -jar \"\"%JAR_FILE%\"\" --spring.config.location=\"\"%JAVA_AGENT_CONFIG%\"\" %APP_OPTS%' -RedirectStandardOutput '%LOG_FILE%' -RedirectStandardError '%LOG_ERR_FILE%' -PassThru; $p.Id | Set-Content '%PID_FILE%'" >nul
if errorlevel 1 (
  echo Start failed
  exit /b 1
)
for /f %%P in ('type "%PID_FILE%"') do set "PID=%%P"
echo Started: pid=%PID%
exit /b 0

:stop
call :is_running
if "%RUNNING%"=="0" (
  if exist "%PID_FILE%" del /f /q "%PID_FILE%" >nul 2>&1
  echo Not running
  exit /b 0
)
taskkill /PID %PID% /T >nul 2>&1
del /f /q "%PID_FILE%" >nul 2>&1
echo Stopped
exit /b 0

:restart
call :stop
call :start
exit /b %errorlevel%

:status
call :is_running
if "%RUNNING%"=="1" (
  echo Running: pid=%PID%
  for /f "tokens=2,*" %%A in ('tasklist /FI "PID eq %PID%" /FO LIST ^| findstr /I "Image Name"') do echo Image: %%B
  exit /b 0
)
echo Not running
exit /b 1

:run
if not exist "%JAR_FILE%" (
  echo Jar not found: %JAR_FILE%
  exit /b 1
)
"%JAVA_CMD%" %JAVA_OPTS% -jar "%JAR_FILE%" --spring.config.location="%JAVA_AGENT_CONFIG%" %APP_OPTS%
exit /b %errorlevel%

:update
echo Update is not supported on Windows in this package
exit /b 2

:is_running
set "RUNNING=0"
set "PID="
if not exist "%PID_FILE%" goto :eof
for /f %%P in ('type "%PID_FILE%"') do set "PID=%%P"
if "%PID%"=="" goto :eof
tasklist /FI "PID eq %PID%" | findstr /I "%PID%" >nul
if %errorlevel%==0 set "RUNNING=1"
goto :eof

:load_config
for /f "usebackq tokens=1* delims==" %%A in ("%~1") do (
  set "key=%%A"
  set "val=%%B"
  for /f "tokens=* delims= " %%K in ("!key!") do set "key=%%K"
  for /f "tokens=* delims= " %%V in ("!val!") do set "val=%%V"
  if not "!key!"=="" (
    if not "!key:~0,1!"=="#" (
      set "!key!=!val!"
    )
  )
)
goto :eof

:resolve_path
set "INPUT=%~1"
set "VAR_NAME=%~2"
if "%INPUT%"=="" (
  set "%VAR_NAME%="
  goto :eof
)
set "IS_ABS=0"
if "%INPUT:~1,1%"==":" set "IS_ABS=1"
if "%INPUT:~0,1%"=="\" set "IS_ABS=1"
if "%INPUT:~0,1%"=="/" set "IS_ABS=1"
if "%IS_ABS%"=="1" (
  set "%VAR_NAME%=%INPUT%"
) else (
  set "%VAR_NAME%=%JAVA_AGENT_HOME%\%INPUT%"
)
goto :eof

:resolve_list
set "INPUT=%~1"
set "VAR_NAME=%~2"
if "%INPUT%"=="" (
  set "%VAR_NAME%="
  goto :eof
)
set "RESULT="
for %%P in (%INPUT:,= %) do (
  call :resolve_path "%%P" RESOLVED
  if "!RESULT!"=="" (
    set "RESULT=!RESOLVED!"
  ) else (
    set "RESULT=!RESULT!,!RESOLVED!"
  )
)
set "%VAR_NAME%=%RESULT%"
goto :eof

:usage
echo Usage: java-agent.cmd ^<start^|stop^|restart^|status^|run^|update^>
exit /b 2
