import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class MergeEngine {

    public static void execute(Config config) throws Exception {
        Path oldPath = Paths.get(config.getOldPath()).toAbsolutePath().normalize();
        Path newPath = Paths.get(config.getNewPath()).toAbsolutePath().normalize();
        String startMarker = config.getStartMarker();
        String endMarker = config.getEndMarker();

        boolean oldIsDir = Files.isDirectory(oldPath);
        boolean newIsDir = Files.isDirectory(newPath);

        if (oldIsDir != newIsDir) {
            System.err.println("Error: Cannot merge. Both paths must be either files or directories.");
            System.exit(1);
        }

        System.out.println("Starting Java Marker-Guided Merge...");
        System.out.println("  Source (new version)      : " + newPath);
        System.out.println("  Destination (old version) : " + oldPath);
        System.out.println("  Start marker              : \"" + startMarker + "\"");
        System.out.println("  End marker                : \"" + endMarker + "\"");
        System.out.println();

        if (!oldIsDir) {
            // Merge single files
            mergeSingleFiles(newPath, oldPath, startMarker, endMarker);
        } else {
            // Merge directories
            mergeDirectories(newPath, oldPath, startMarker, endMarker);
        }

        System.out.println("\nMerge process completed.");
    }

    private static void mergeSingleFiles(Path sourceFile, Path destFile, String startMarker, String endMarker) {
        try {
            boolean merged = mergeFileContents(sourceFile, destFile, startMarker, endMarker);
            if (merged) {
                System.out.println("Successfully merged: " + destFile.getFileName());
            } else {
                System.out.println("Skipped (no markers or no changes): " + destFile.getFileName());
            }
        } catch (Exception e) {
            System.err.println("Error merging file " + destFile.getFileName() + ": " + e.getMessage());
        }
    }

    private static void mergeDirectories(Path sourceDir, Path destDir, String startMarker, String endMarker) throws IOException {
        List<Path> sourceFiles = new ArrayList<>();
        Files.walkFileTree(sourceDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    sourceFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        int mergedCount = 0;
        int skippedCount = 0;

        for (Path sourceFile : sourceFiles) {
            String rel = sourceDir.relativize(sourceFile).toString();
            Path destFile = destDir.resolve(rel);

            if (Files.exists(destFile)) {
                try {
                    boolean merged = mergeFileContents(sourceFile, destFile, startMarker, endMarker);
                    if (merged) {
                        System.out.println("  [MERGED]  " + rel);
                        mergedCount++;
                    } else {
                        skippedCount++;
                    }
                } catch (Exception e) {
                    System.err.println("  [ERROR]   " + rel + " : " + e.getMessage());
                }
            } else {
                System.out.println("  [WARNING] Destination file does not exist, skipped: " + rel);
            }
        }

        System.out.printf("Summary: %d files merged, %d files skipped.%n", mergedCount, skippedCount);
    }

    private static boolean mergeFileContents(Path sourceFile, Path destFile, String startMarker, String endMarker) throws IOException {
        List<String> sourceLines = Files.readAllLines(sourceFile, StandardCharsets.UTF_8);
        List<String> destLines = Files.readAllLines(destFile, StandardCharsets.UTF_8);

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
            // Source doesn't have any markers
            return false;
        }

        // Merge into destination
        List<String> mergedLines = new ArrayList<>();
        int blockIndex = 0;
        boolean destInside = false;
        boolean modified = false;

        for (int i = 0; i < destLines.size(); i++) {
            String line = destLines.get(i);
            if (line.contains(startMarker)) {
                mergedLines.add(line);
                destInside = true;
                if (blockIndex < sourceBlocks.size()) {
                    List<String> srcBlock = sourceBlocks.get(blockIndex);
                    // Check if block actually changed to avoid unnecessary rewrite
                    // Collect old block content to compare
                    List<String> oldBlock = new ArrayList<>();
                    int j = i + 1;
                    while (j < destLines.size() && !destLines.get(j).contains(endMarker)) {
                        oldBlock.add(destLines.get(j));
                        j++;
                    }
                    if (!srcBlock.equals(oldBlock)) {
                        modified = true;
                    }
                    mergedLines.addAll(srcBlock);
                    blockIndex++;
                } else {
                    System.err.println("Warning [" + destFile.getFileName() + "]: Destination contains more markers than source. Extra block left unchanged.");
                    // Fall back: copy old block lines
                    int j = i + 1;
                    while (j < destLines.size() && !destLines.get(j).contains(endMarker)) {
                        mergedLines.add(destLines.get(j));
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
            // No markers found in destination
            System.err.println("Warning [" + destFile.getFileName() + "]: Markers not found in destination file.");
            return false;
        }

        if (blockIndex < sourceBlocks.size()) {
            System.err.println("Warning [" + destFile.getFileName() + "]: Source contains more markers (" + sourceBlocks.size() + ") than destination (" + blockIndex + "). Remaining blocks ignored.");
        }

        if (modified) {
            Files.write(destFile, mergedLines, StandardCharsets.UTF_8);
            return true;
        }

        return false;
    }
}
