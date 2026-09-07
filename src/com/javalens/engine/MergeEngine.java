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
        Path folder1 = Paths.get(config.getOldPath()).toAbsolutePath().normalize();
        Path folder2 = Paths.get(config.getNewPath()).toAbsolutePath().normalize();
        String startMarker = config.getStartMarker();
        String endMarker = config.getEndMarker();

        boolean oldIsDir = Files.isDirectory(folder1);
        boolean newIsDir = Files.isDirectory(folder2);

        if (oldIsDir != newIsDir) {
            System.err.println("Error: Cannot merge. Both paths must be either files or directories.");
            System.exit(1);
        }

        // Determine destination output directory
        Path outputDir = Paths.get(config.getOutputDir()).toAbsolutePath().normalize();
        if (oldIsDir && outputDir.getFileName() != null && outputDir.getFileName().toString().equals("java_analysis_output")) {
            outputDir = outputDir.resolve("merged");
        }
        Files.createDirectories(outputDir);

        System.out.println("Starting Java Marker-Guided Merge...");
        System.out.println("  Input Folder 1 (Base)      : " + folder1);
        System.out.println("  Input Folder 2 (Markers)   : " + folder2);
        System.out.println("  Output Folder (Merged)     : " + outputDir);
        System.out.println("  Start marker               : \"" + startMarker + "\"");
        System.out.println("  End marker                 : \"" + endMarker + "\"");
        System.out.println();

        List<MergeResult> results;
        if (!oldIsDir) {
            // Merge single files
            results = mergeSingleFiles(folder2, folder1, outputDir, startMarker, endMarker);
        } else {
            // Merge directories
            results = mergeDirectories(folder1, folder2, outputDir, startMarker, endMarker);
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

    private static List<MergeResult> mergeDirectories(Path folder1, Path folder2, Path outputDir, String startMarker, String endMarker) throws IOException {
        List<MergeResult> results = new ArrayList<>();
        Files.createDirectories(outputDir);

        Set<String> files1 = scanRelativeFiles(folder1);
        Set<String> files2 = scanRelativeFiles(folder2);

        Set<String> allRelativeFiles = new TreeSet<>(files1);
        allRelativeFiles.addAll(files2);

        int mergedCount = 0;
        int copiedCount = 0;
        int addedCount = 0;

        for (String rel : allRelativeFiles) {
            Path file1 = folder1.resolve(rel);
            Path file2 = folder2.resolve(rel);
            Path destOut = outputDir.resolve(rel);

            if (destOut.getParent() != null) {
                Files.createDirectories(destOut.getParent());
            }

            boolean in1 = files1.contains(rel);
            boolean in2 = files2.contains(rel);

            if (in1 && !in2) {
                // File from folder1 does not exist in folder2 -> preserve in output folder
                Files.copy(file1, destOut, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("  [COPIED]  " + rel + " (present in folder1 only)");
                copiedCount++;
                results.add(new MergeResult(rel, "COPIED", "Present in folder1 only; copied to output folder"));
            } else if (!in1 && in2) {
                // File from folder2 does not exist in folder1 -> copy new file to output folder
                Files.copy(file2, destOut, StandardCopyOption.REPLACE_EXISTING);
                System.out.println("  [ADDED]   " + rel + " (new file in folder2)");
                addedCount++;
                results.add(new MergeResult(rel, "ADDED", "New file in folder2; copied to output folder"));
            } else {
                // Present in both folder1 and folder2 -> merge marker blocks and write to output folder
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
