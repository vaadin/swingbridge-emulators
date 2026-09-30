@echo off
@rem The static sweep's worklist, read off the app's own compiled classes.
@rem
@rem   bin\static-sweep <app>\target\classes --lib <app>\target\dependency --report static-sweep.md
@rem
@rem Point it at the app you have NOT migrated yet: its classes exist before a single import
@rem is rewritten.
@rem
@rem The wildcard classpath IS the launcher - cmd.exe passes "..\lib\*" through to the JVM,
@rem which expands it itself.
@rem
@rem ASCII only, deliberately: a batch file is parsed in the console's active codepage.
setlocal
set "JAVA=java"
if not "%JAVA_HOME%"=="" set "JAVA=%JAVA_HOME%\bin\java"
"%JAVA%" -cp "%~dp0..\lib\*" com.vaadin.swingbridge.migration.tool.staticsweep.StaticSweep %*
exit /b %ERRORLEVEL%
