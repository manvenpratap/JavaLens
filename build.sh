#!/bin/bash
if [ -d "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin" ]; then
    export PATH="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin:$PATH"
fi

echo "Compiling JavaAnalyzer..."
javac *.java
if [ $? -eq 0 ]; then
    jar cfe javalens.jar JavaAnalyzer *.class README.md index.html analyzer.properties
    echo "Build successful! JAR created: javalens.jar (including README.md, index.html, analyzer.properties)"
else
    echo "Compilation failed. Make sure you have a JDK 17+ installed."
    exit 1
fi
