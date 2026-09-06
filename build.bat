@echo off
echo Compiling JavaAnalyzer...
javac *.java
if %errorlevel% neq 0 (
    echo Compilation failed. Make sure you have a JDK 17+ installed.
    exit /b 1
)
jar cfe javalens.jar JavaAnalyzer *.class README.md index.html analyzer.properties javalens.conf
echo Build successful! JAR created: javalens.jar (including README.md, index.html, analyzer.properties, javalens.conf)
