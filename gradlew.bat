@echo off
@rem
@rem Copyright © 2015-2021 the original authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem

setlocal

set APP_NAME=Gradle
set APP_BASE_NAME=%~n0

set DEFAULT_JVM_OPTS=-Xmx64m -Xms64m

set APP_HOME=%~dp0
if "%APP_HOME%"=="" set APP_HOME=.
set APP_HOME=%APP_HOME:~0,-1%

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

if not defined JAVA_HOME goto findJavaFromPath

set JAVA_EXE=%JAVA_HOME%\bin\java.exe
if exist "%JAVA_EXE%" goto execute

echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.
goto fail

:findJavaFromPath
set JAVA_EXE=java.exe

:execute
set CMD_LINE_ARGS=
set _SKIP=2

:parse
if "%~1"=="" goto endparse
if "%~1"=="-Dorg.gradle.appname=%APP_BASE_NAME%" goto skip
set CMD_LINE_ARGS=%CMD_LINE_ARGS% "%~1"
:skip
shift
goto parse

:endparse
set CMD_LINE_ARGS=%CMD_LINE_ARGS%

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %CMD_LINE_ARGS%
if %ERRORLEVEL%==0 goto end

:fail
set EXIT_CODE=%ERRORLEVEL%
if "%EXIT_CODE%"=="0" set EXIT_CODE=1
exit /b %EXIT_CODE%

:end
endlocal
