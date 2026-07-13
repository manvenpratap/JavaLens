#!/bin/bash
echo "Compiling JavaAnalyzer..."
javac *.java
if [ $? -eq 0 ]; then
    jar cfe javalens.jar JavaAnalyzer *.class
    echo "Build successful! JAR created: javalens.jar"
else
    echo "Compilation failed. Make sure you have a JDK 17+ installed."
    exit 1
fi
