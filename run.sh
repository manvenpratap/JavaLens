#!/bin/bash
if [ -d "/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin" ]; then
    export PATH="/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin:$PATH"
fi

java -jar javalens.jar "$@"
