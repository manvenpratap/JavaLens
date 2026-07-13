#!/bin/bash
echo "=================================================="
echo "              JAVALENS TEST SUITE RUN             "
echo "=================================================="

# Create directories
mkdir -p test_out

# 1. Compile codebase
echo -e "\n1. Compiling codebase..."
./build.sh
if [ $? -ne 0 ]; then
    echo "Build failed. Aborting tests."
    exit 1
fi

# Reset test files
echo "Resetting test files..."
cp test_workspace/v1/MyClass.java test_workspace/v1_backup.java

# 2. Test Analyze Mode
echo -e "\n2. Running Analyze Mode on test_workspace/v1..."
./run.sh --mode analyze --source test_workspace/v1 --output-dir test_out/analyze_out

echo -e "\nAttributes Output CSV:"
cat test_out/analyze_out/java_attributes.csv
echo -e "\nMethods Output CSV:"
cat test_out/analyze_out/java_methods.csv

# 3. Test Compare Mode
echo -e "\n3. Running Compare Mode between v1 and v2..."
./run.sh --mode compare --old test_workspace/v1 --new test_workspace/v2 --output-dir test_out/compare_out

echo -e "\nAttributes Compare Delta CSV:"
cat test_out/compare_out/comparison_attributes.csv
echo -e "\nMethods Compare Delta CSV:"
cat test_out/compare_out/comparison_methods.csv

# 4. Test Merge Mode
echo -e "\n4. Running Merge Mode (coping v2 markers into v1)..."
./run.sh --mode merge --old test_workspace/v1 --new test_workspace/v2 --start-marker "// START_MERGE" --end-marker "// END_MERGE"

echo -e "\nMerged v1/MyClass.java contents:"
cat test_workspace/v1/MyClass.java

# Restore test files
mv test_workspace/v1_backup.java test_workspace/v1/MyClass.java
rm -rf test_out

echo -e "\n=================================================="
echo "              TEST SUITE CONCLUDED                "
echo "=================================================="
