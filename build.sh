#!/bin/bash
if [ -d "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin" ]; then
    export PATH="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin:$PATH"
fi

echo "Compiling JavaLens packages..."
mkdir -p bin
javac -d bin $(find src -name "*.java")
if [ $? -eq 0 ]; then
    jar cfe javalens.jar com.javalens.JavaAnalyzer -C bin . README.md index.html analyzer.properties javalens.conf samples
    echo "Build successful! JAR created: javalens.jar (including README.md, index.html, analyzer.properties, javalens.conf, samples)"
else
    echo "Compilation failed. Make sure you have a JDK 17+ installed."
    exit 1
fi
