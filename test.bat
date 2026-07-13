@echo off
echo ==================================================
echo               JAVALENS TEST SUITE RUN             
echo ==================================================

rem 1. Compile codebase
echo.
echo 1. Compiling codebase...
call build.bat
if %ERRORLEVEL% neq 0 (
    echo Build failed. Aborting tests.
    exit /b 1
)

rem Reset test files
echo Resetting test files...
copy /Y test_workspace\v1\MyClass.java test_workspace\v1_backup.java > nul

rem 2. Test Analyze Mode
echo.
echo 2. Running Analyze Mode on test_workspace\v1...
call run.bat --mode analyze --source test_workspace\v1 --output-dir test_out\analyze_out

echo.
echo Attributes Output CSV:
type test_out\analyze_out\java_attributes.csv
echo.
echo Methods Output CSV:
type test_out\analyze_out\java_methods.csv

rem 3. Test Compare Mode
echo.
echo 3. Running Compare Mode between v1 and v2...
call run.bat --mode compare --old test_workspace\v1 --new test_workspace\v2 --output-dir test_out\compare_out

echo.
echo Attributes Compare Delta CSV:
type test_out\compare_out\comparison_attributes.csv
echo.
echo Methods Compare Delta CSV:
type test_out\compare_out\comparison_methods.csv

rem 4. Test Merge Mode
echo.
echo 4. Running Merge Mode (coping v2 markers into v1)...
call run.bat --mode merge --old test_workspace\v1 --new test_workspace\v2 --start-marker "// START_MERGE" --end-marker "// END_MERGE"

echo.
echo Merged v1\MyClass.java contents:
type test_workspace\v1\MyClass.java

rem Restore test files
move /Y test_workspace\v1_backup.java test_workspace\v1\MyClass.java > nul
rd /s /q test_out

echo.
echo ==================================================
echo               TEST SUITE CONCLUDED                
echo ==================================================
