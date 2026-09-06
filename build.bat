@echo off
echo Compiling JavaLens packages...
if not exist bin mkdir bin
dir /s /b src\*.java > sources.txt
javac -d bin @sources.txt
if %errorlevel% neq 0 (
    echo Compilation failed. Make sure you have a JDK 17+ installed.
    if exist sources.txt del sources.txt
    exit /b 1
)
if exist sources.txt del sources.txt
jar cfe javalens.jar com.javalens.JavaAnalyzer -C bin . README.md index.html analyzer.properties javalens.conf
echo Build successful! JAR created: javalens.jar (including README.md, index.html, analyzer.properties, javalens.conf)
