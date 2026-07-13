@echo off
echo Compiling JavaAnalyzer...
javac *.java
if %errorlevel% neq 0 (
    echo Compilation failed. Make sure you have a JDK 17+ installed.
    exit /b 1
)
jar cfe java-analyzer.jar JavaAnalyzer *.class
echo Build successful! JAR created: java-analyzer.jar
