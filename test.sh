#!/bin/bash
echo "=================================================="
echo "              JAVALENS TEST SUITE RUN             "
echo "=================================================="

# Clean and create directories
rm -rf test_out && mkdir -p test_out
[ -f javalens.conf ] && cp javalens.conf javalens.conf.test_bak
[ -f analyzer.properties ] && cp analyzer.properties analyzer.properties.test_bak
[ -f java_report_data.js ] && cp java_report_data.js java_report_data.js.test_bak

# 1. Compile codebase
echo -e "\n1. Compiling codebase..."
./build.sh
if [ $? -ne 0 ]; then
    echo "Build failed. Aborting tests."
    exit 1
fi

# Reset test files
echo "Resetting test files..."
rm -rf test_workspace/v1_backup && cp -r test_workspace/v1 test_workspace/v1_backup

# 2. Test Analyze Mode
echo -e "\n2. Running Analyze Mode on test_workspace/v1..."
./run.sh --mode analyze --source test_workspace/v1 --output-dir test_out/analyze_out

echo -e "\nAttributes Output CSV:"
cat test_out/analyze_out/*/java_attributes.csv
echo -e "\nMethods Output CSV:"
cat test_out/analyze_out/*/java_methods.csv

# 3. Test Compare Mode
echo -e "\n3. Running Compare Mode between v1 and v2..."
./run.sh --mode compare --old test_workspace/v1 --new test_workspace/v2 --output-dir test_out/compare_out

echo -e "\nAttributes Compare Delta CSV:"
cat test_out/compare_out/comparison_attributes.csv
echo -e "\nMethods Compare Delta CSV:"
cat test_out/compare_out/comparison_methods.csv

# 4. Test Merge Mode
echo -e "\n4. Running Merge Mode (reading folder1 and folder2, creating merged versions in output folder)..."
echo -e "package com.example;\npublic class ExtraV1Only {\n    private String note = \"Exclusive to folder1\";\n}\n" > test_workspace/v1/ExtraV1Only.java

./run.sh --mode merge --old test_workspace/v1 --new test_workspace/v2 --output-dir test_out/merge_out --start-marker "// START_MERGE" --end-marker "// END_MERGE"

echo -e "\nMerged output MyClass.java contents:"
cat test_out/merge_out/MyClass.java

echo -e "\nVerified folder1-only file preserved in output folder (ExtraV1Only.java):"
cat test_out/merge_out/ExtraV1Only.java

rm -f test_workspace/v1/ExtraV1Only.java

# 5. Test Report Mode
echo -e "\n5. Running Unified Report Mode (analyze -> compare -> merge -> report)..."
./run.sh --mode report --old test_workspace/v1 --new test_workspace/v2 --output-dir test_out/report_out

echo -e "\nUnified Reports Generated in Output Run Folder:"
ls -lh test_out/report_out/*/javalens_report*

echo -e "\nUnified Report CSV (first 10 lines):"
head -n 10 test_out/report_out/*/javalens_report.csv

echo -e "\nUnified Report JSON (first 25 lines):"
head -n 25 test_out/report_out/*/javalens_report.json

# 6. Test Configuration .conf File Mode
echo -e "\n6. Testing .conf configuration file workflow..."
./run.sh --source test_workspace/v1 --output-dir test_out/conf_test_out --threads 4 --save-config test_out/custom.conf
echo "Saved config file:"
cat test_out/custom.conf
echo -e "\nRunning Analyze using saved .conf:"
./run.sh --config test_out/custom.conf

# Restore test files
rm -rf test_workspace/v1 && cp -r test_workspace/v1_backup test_workspace/v1 && rm -rf test_workspace/v1_backup
rm -rf test_out
[ -f javalens.conf.test_bak ] && mv javalens.conf.test_bak javalens.conf
[ -f analyzer.properties.test_bak ] && mv analyzer.properties.test_bak analyzer.properties
[ -f java_report_data.js.test_bak ] && mv java_report_data.js.test_bak java_report_data.js

echo -e "\n=================================================="
echo "              TEST SUITE CONCLUDED                "
echo "=================================================="
