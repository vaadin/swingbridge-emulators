@echo off
@rem Phase 2 of the Swing-to-emulators migration: rewrite java.awt / javax.swing references
@rem onto their vaadinx emulators.
@rem
@rem   bin\import-swap <app>\src\main\java --dry-run --report import-swap.md
@rem
@rem The wildcard classpath IS the launcher, and for this tool it is also the configuration:
@rem every META-INF\emul\ported-types.tsv in ..\lib is unioned into the swap table. With no
@rem table-carrying jar there the run fails with a message naming the fix - see README.md.
@rem
@rem ASCII only, deliberately: a batch file is parsed in the console's active codepage.
setlocal
set "JAVA=java"
if not "%JAVA_HOME%"=="" set "JAVA=%JAVA_HOME%\bin\java"
"%JAVA%" -cp "%~dp0..\lib\*" com.vaadin.swingbridge.migration.tool.importswap.ImportSwap %*
exit /b %ERRORLEVEL%
