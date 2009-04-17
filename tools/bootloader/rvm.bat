@echo off
:: This file is part of the Jikes RVM project (http://jikesrvm.org).
::
:: This file is licensed to You under the Common Public License (CPL);
:: You may not use this file except in compliance with the License. You
:: may obtain a copy of the License at
::
::     http://www.opensource.org/licenses/cpl1.0.php
::
:: See the COPYRIGHT.txt file distributed with this work for information
:: regarding copyright ownership.

:: set RVM_HOME to directory of this file and then strip the trailing \
set RVM_HOME=%~dp0
set RVM_HOME=%RVM_HOME:~0,-1%

:: arguments for either normal or debug execution
set BASE_ARGS=-X:ic=%RVM_HOME%\RVM.code.image -X:id=%RVM_HOME%\RVM.data.image -X:ir=%RVM_HOME%\RVM.rmap.image -X:vmClasses=%RVM_HOME%\jksvm.jar;%RVM_HOME%\rvmrt.jar;%RVM_HOME%\lib\luni.jar;%RVM_HOME%\lib\nio.jar;%RVM_HOME%\lib\nio_char.jar;%RVM_HOME%\lib\archive.jar;%RVM_HOME%\lib\concurrent.jar;%RVM_HOME%\lib\math.jar;%RVM_HOME%\lib\regex.jar;%RVM_HOME%\lib\icu4j-charsets-3_8.jar;%RVM_HOME%\lib\icu4j-3_8.jar;%RVM_HOME%\lib\yoko-rmi-impl.jar;%RVM_HOME%\lib\instrument.jar;%RVM_HOME%\lib\beans.jar;%RVM_HOME%\lib\xml-apis.jar;%RVM_HOME%\lib\mx4j.jar;%RVM_HOME%\lib\xalan.jar;%RVM_HOME%\lib\resolver.jar;%RVM_HOME%\lib\logging.jar;%RVM_HOME%\lib\bcprov.jar;%RVM_HOME%\lib\security.jar;%RVM_HOME%\lib\sql.jar;%RVM_HOME%\lib\print.jar;%RVM_HOME%\lib\mx4j-remote.jar;%RVM_HOME%\lib\luni-kernel-stubs.jar;%RVM_HOME%\lib\misc.jar;%RVM_HOME%\lib\accessibility.jar;%RVM_HOME%\lib\crypto.jar;%RVM_HOME%\lib\yoko.jar;%RVM_HOME%\lib\rmi.jar;%RVM_HOME%\lib\security-kernel-stubs.jar;%RVM_HOME%\lib\x-net.jar;%RVM_HOME%\lib\imageio.jar;%RVM_HOME%\lib\lang-management.jar;%RVM_HOME%\lib\applet.jar;%RVM_HOME%\lib\prefs.jar;%RVM_HOME%\lib\annotation.jar;%RVM_HOME%\lib\awt.jar;%RVM_HOME%\lib\xercesImpl.jar;%RVM_HOME%\lib\yoko-rmi-spec.jar;%RVM_HOME%\lib\swing.jar;%RVM_HOME%\lib\auth.jar;%RVM_HOME%\lib\yoko-core.jar;%RVM_HOME%\lib\text.jar;%RVM_HOME%\lib\jndi.jar;%RVM_HOME%\lib\suncompat.jar;%RVM_HOME%\lib\sound.jar;%RVM_HOME%\lib\bcel-5.2.jar -Duser.timezone=GMT -Djava.home=%RVM_HOME% -Djava.library.path=%RVM_HOME%\lib -Dvm.boot.library.path=%RVM_HOME%\lib -Duser.home=%USERPROFILE% -Duser.dir=%USERPROFILE% -Duser.name=%USERNAME% -Dos.name=Windows -Dos.version=0 -Dos.arch=%PROCESSOR_ARCHITECTURE% -Dpath.separator=; -Dfile.separator=\ -Dfile.encoding=ISO-8859-1 -Djava.io.tmpdir=c:\Windows\Temp

:: allow DLLs to be found
set PATH=%RVM_HOME%\lib;%PATH%

if [%1]==[-gdb] GOTO DEBUG

%RVM_HOME%\JikesRVM %BASE_ARGS% %*
GOTO END

:DEBUG
:: Remove executable and -gdb from args
SHIFT
SHIFT
:: Silly attempt to build up other args and work around %* and batches
:: swallowing equal signs
set ARGS=
:LOOP_TO_BUILD_ARGS
if [%0]==[] GOTO END_LOOP_TO_BUILD_ARGS
  if [%1]==[] GOTO ONE_ARG
  set ARGS=%ARGS% %0=%1
  SHIFT
  SHIFT
  GOTO LOOP_TO_BUILD_ARGS
:ONE_ARG
  set ARGS=%ARGS% %0
  SHIFT
  GOTO LOOP_TO_BUILD_ARGS
:END_LOOP_TO_BUILD_ARGS

:: Run the debugger
"c:\Program Files\Microsoft Visual Studio 9.0\Common7\IDE\VCExpress.exe" /debugexe %RVM_HOME%\JikesRVM.exe %BASE_ARGS% %ARGS%

:END
