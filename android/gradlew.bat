@rem
@rem Gradle startup script for Windows
@rem

@if "%DEBUG%"=="" @echo off
chcp 65001 >NUL
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_HOME=%DIRNAME%
set CLASSPATH=gradle\wrapper\gradle-wrapper.jar

set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

if defined JAVA_HOME goto findJavaFromJavaHome

set JAVACMD=java.exe
%JAVACMD% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
goto fail

:findJavaFromJavaHome
set JAVACMD=%JAVA_HOME%\bin\java.exe

:execute
pushd "%APP_HOME%" >NUL
"%JAVACMD%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
set EXIT_CODE=%ERRORLEVEL%
popd >NUL

:end
endlocal
exit /b %EXIT_CODE%

:fail
endlocal
exit /b 1
