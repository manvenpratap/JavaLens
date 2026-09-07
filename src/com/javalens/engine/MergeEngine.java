package com.javalens.engine;

import com.javalens.Config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class MergeEngine {

    public static class MergeResult {
        public final String relPath;
        public final String status;
        public final String message;

        public MergeResult(String relPath, String status, String message) {
            this.relPath = relPath;
            this.status = status;
            this.message = message;
        }
    }

    public static List<MergeResult> execute(Config config) throws Exception {
        Path existingJavaFiles = Paths.get(config.getExistingPath()).toAbsolutePath().normalize();
        Path generatedJavaFiles = Paths.get(config.getGeneratedPath()).toAbsolutePath().normalize();
        String startMarker = config.getStartMarker();
        String endMarker = config.getEndMarker();

        boolean oldIsDir = Files.isDirectory(existingJavaFiles);
        boolean newIsDir = Files.isDirectory(generatedJavaFiles);

        if (oldIsDir != newIsDir) {
            System.err.println("Error: Cannot merge. Both paths must be either files or directories.");
            System.exit(1);
        }

        // Determine destination output directory
        Path outputDir;
        Path configuredPath = Paths.get(config.getOutputDir()).toAbsolutePath().normalize();
        if (configuredPath.toString().contains("/run_") || configuredPath.toString().contains("\\run_") ||
            (configuredPath.getFileName() != null && configuredPath.getFileName().toString().startsWith("run_"))) {
            outputDir = configuredPath;
            Files.createDirectories(outputDir);
        } else {
            outputDir = config.createRunFolder();
        }

        System.out.println("Starting Java Marker-Guided Merge...");
        System.out.println("  Existing Java Files (Base)             : " + existingJavaFiles);
        System.out.println("  Newly Generated Java Files (Features)  : " + generatedJavaFiles);
        System.out.println("  Output Folder (Merged Codebase)        : " + outputDir);
        System.out.println("  Start marker                           : \"" + startMarker + "\"");
        System.out.println("  End marker                             : \"" + endMarker + "\"");
        System.out.println();

        List<MergeResult> results;
        if (!oldIsDir) {
            // Merge single files
            results = mergeSingleFiles(generatedJavaFiles, existingJavaFiles, outputDir, startMarker, endMarker);
        } else {
            // Merge directories
            results = mergeDirectories(existingJavaFiles, generatedJavaFiles, outputDir, startMarker, endMarker);
        }

        System.out.println("\nMerge process completed.");
        return results;
    }

    private static List<MergeResult> mergeSingleFiles(Path sourceFile, Path baseFile, Path outputTarget, String startMarker, String endMarker) {
        List<MergeResult> results = new ArrayList<>();
        try {
            Path destOut = outputTarget;
            if (Files.isDirectory(outputTarget)) {
                destOut = outputTarget.resolve(baseFile.getFileName());
            } else if (destOut.getParent() != null) {
                Files.createDirectories(destOut.getParent());
            }
            boolean modified = mergeFileContentsToOutput(sourceFile, baseFile, destOut, startMarker, endMarker);
            if (modified) {
                System.out.println("Successfully merged to: " + destOut);
                results.add(new MergeResult(destOut.getFileName().toString(), "MERGED", "Successfully merged to " + destOut));
            } else {
                System.out.println("Preserved base file to: " + destOut);
                results.add(new MergeResult(destOut.getFileName().toString(), "COPIED", "Preserved base file to " + destOut));
            }
        } catch (Exception e) {
            System.err.println("Error merging file: " + e.getMessage());
            results.add(new MergeResult(baseFile.getFileName().toString(), "ERROR", e.getMessage()));
        }
        return results;
    }

    private static List<MergeResult> mergeDirectories(Path existingFolder, Path generatedFolder, Path outputDir, String startMarker, String endMarker) throws IOException {
        List<MergeResult> results = new ArrayList<>();
        Files.createDirectories(outputDir);

        Set<String> existingFiles = scanRelativeFiles(existingFolder);
        Set<String> generatedFiles = scanRelativeFiles(generatedFolder);

        Set<String> allRelativeFiles = new TreeSet<>(existingFiles);
        allRelativeFiles.addAll(generatedFiles);

        int mergedCount = 0;
        int copiedCount = 0;
        int addedCount = 0;

        for (String rel : allRelativeFiles) {
            Path file1 = existingFolder.resolve(rel);
            Path file2 = generatedFolder.resolve(rel);
            Path destOut = outputDir.resolve(rel);

            if (destOut.getParent() != null) {
                Files.createDirectories(destOut.getParent());
            }

            boolean inExisting = existingFiles.contains(rel);
            boolean inGenerated = generatedFiles.contains(rel);

            if (inExisting && !inGenerated) {
                // File from existing Java files does not exist in newly generated -> preserve in output folder
                Files.copy(file1, destOut, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("  [COPIED]  " + rel + " (present in existing Java files only)");
                copiedCount++;
                results.add(new MergeResult(rel, "COPIED", "Present in existing Java files only; preserved in output folder"));
            } else if (!inExisting && inGenerated) {
                // Newly generated file does not exist in existing Java files -> copy new file to output folder
                Files.copy(file2, destOut, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("  [ADDED]   " + rel + " (newly generated Java file)");
                addedCount++;
                results.add(new MergeResult(rel, "ADDED", "Newly generated file; copied to output folder"));
            } else {
                // Present in both existing and generated -> merge marker blocks and write to output folder
                try {
                    boolean modified = mergeFileContentsToOutput(file2, file1, destOut, startMarker, endMarker);
                    if (modified) {
                        System.out.println("  [MERGED]  " + rel);
                        mergedCount++;
                        results.add(new MergeResult(rel, "MERGED", "Successfully merged marked blocks into output folder"));
                    } else {
                        System.out.println("  [COPIED]  " + rel + " (no marker modifications)");
                        copiedCount++;
                        results.add(new MergeResult(rel, "COPIED", "No marker modifications; wrote to output folder"));
                    }
                } catch (Exception e) {
                    System.err.println("  [ERROR]   " + rel + " : " + e.getMessage());
                    // Fall back to copying file1 so output folder still has the file
                    try {
                        Files.copy(file1, destOut, StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {}
                    results.add(new MergeResult(rel, "ERROR", e.getMessage()));
                }
            }
        }

        System.out.printf("Summary: %d files merged, %d files copied/preserved, %d files added to %s%n",
                mergedCount, copiedCount, addedCount, outputDir);
        try {
            com.javalens.parser.ParserUtil.writeReportDataJs("MERGE", outputDir.getFileName().toString(), outputDir);
        } catch (Exception ignored) {}
        return results;
    }

    private static Set<String> scanRelativeFiles(Path dir) throws IOException {
        Set<String> set = new TreeSet<>();
        if (!Files.exists(dir)) return set;
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    set.add(dir.relativize(file).toString());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return set;
    }

    private static boolean mergeFileContentsToOutput(Path sourceFile, Path baseFile, Path destOut, String startMarker, String endMarker) throws IOException {
        List<String> sourceLines = Files.readAllLines(sourceFile, StandardCharsets.UTF_8);
        List<String> baseLines = Files.readAllLines(baseFile, StandardCharsets.UTF_8);

        // Extract blocks from source
        List<List<String>> sourceBlocks = new ArrayList<>();
        List<String> currentBlock = null;
        boolean inside = false;
        for (String line : sourceLines) {
            if (line.contains(startMarker)) {
                inside = true;
                currentBlock = new ArrayList<>();
            } else if (line.contains(endMarker)) {
                if (inside) {
                    sourceBlocks.add(currentBlock);
                    inside = false;
                    currentBlock = null;
                }
            } else if (inside) {
                currentBlock.add(line);
            }
        }

        if (sourceBlocks.isEmpty()) {
            // Source doesn't have any markers -> copy base content to output
            Files.write(destOut, baseLines, StandardCharsets.UTF_8);
            return false;
        }

        // Merge into destination lines
        List<String> mergedLines = new ArrayList<>();
        int blockIndex = 0;
        boolean destInside = false;
        boolean modified = false;

        for (int i = 0; i < baseLines.size(); i++) {
            String line = baseLines.get(i);
            if (line.contains(startMarker)) {
                mergedLines.add(line);
                destInside = true;
                if (blockIndex < sourceBlocks.size()) {
                    List<String> srcBlock = sourceBlocks.get(blockIndex);
                    // Check if block actually changed
                    List<String> oldBlock = new ArrayList<>();
                    int j = i + 1;
                    while (j < baseLines.size() && !baseLines.get(j).contains(endMarker)) {
                        oldBlock.add(baseLines.get(j));
                        j++;
                    }
                    if (!srcBlock.equals(oldBlock)) {
                        modified = true;
                    }
                    mergedLines.addAll(srcBlock);
                    blockIndex++;
                } else {
                    System.err.println("Warning [" + destOut.getFileName() + "]: Base contains more markers than source. Extra block left unchanged.");
                    int j = i + 1;
                    while (j < baseLines.size() && !baseLines.get(j).contains(endMarker)) {
                        mergedLines.add(baseLines.get(j));
                        j++;
                    }
                }
            } else if (line.contains(endMarker)) {
                mergedLines.add(line);
                destInside = false;
            } else if (!destInside) {
                mergedLines.add(line);
            }
        }

        if (blockIndex == 0) {
            // No markers found in base file -> write base lines to output
            System.err.println("Warning [" + destOut.getFileName() + "]: Markers not found in base file; copying base version.");
            Files.write(destOut, baseLines, StandardCharsets.UTF_8);
            return false;
        }

        if (blockIndex < sourceBlocks.size()) {
            System.err.println("Warning [" + destOut.getFileName() + "]: Source contains more markers (" + sourceBlocks.size() + ") than base (" + blockIndex + "). Remaining blocks ignored.");
        }

        Files.write(destOut, mergedLines, StandardCharsets.UTF_8);
        return modified;
    }
}
