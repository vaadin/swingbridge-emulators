@echo off
@rem Phase 1 of the Swing-to-emulators migration: find the known hazards in a source tree.
@rem
@rem   bin\hazard-scan <app>\src\main\java <app>\src\main\resources --report hazards.md
@rem
@rem The wildcard classpath IS the launcher - cmd.exe passes "..\lib\*" through to the JVM,
@rem which expands it itself. Drop a jar in there to add a table.
@rem
@rem ASCII only, deliberately: a batch file is parsed in the console's active codepage.
setlocal
set "JAVA=java"
if not "%JAVA_HOME%"=="" set "JAVA=%JAVA_HOME%\bin\java"
"%JAVA%" -cp "%~dp0..\lib\*" com.vaadin.swingbridge.migration.tool.hazardscan.HazardScan %*
exit /b %ERRORLEVEL%
