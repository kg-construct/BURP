package burp.tools;

import org.jspecify.annotations.NonNull;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Cross-platform fetcher that downloads RML module repositories as ZIP archives
 * (no need for Git installed), extracts them, copies test-cases and shapes
 * into this project, and cleans up temporary files.
 *
 * Usage (recommended via Maven):
 *   mvn -q -DskipTests=true -Dexec.mainClass=burp.tools.FetchTestCases exec:java
 */
public class FetchTestCases {

    public static void main(String[] args) {
        Path root = Paths.get(".");
        run(
            root.resolve("src/test/resources"),
            root.resolve("src/main/resources/shapes"),
            root.resolve("src/main/resources/vocabularies/rml/")
        );
    }

    public static void run(Path testCasesDir, Path shapesDir, Path vocabulariesDir) {
        List<String> repos = List.of(
            "rml-core",
            "rml-cc",
            "rml-io",
            "rml-fnml",
            "rml-lv",
            "rml-io-registry"
        );

        for (String repo : repos) {
            String base = "https://github.com/kg-construct/" + repo;
            String zipMain = base + "/archive/refs/heads/main.zip";
            String zipMaster = base + "/archive/refs/heads/master.zip";

            try {
                System.out.println("Downloading and extracting " + repo + " …");
                Path tempDir = Files.createTempDirectory("burp-fetch-" + repo);

                try {
                    if (!downloadWithFallback(zipMain, zipMaster, tempDir)) {
                        System.err.println("• Failed to download " + repo + " (main/master not found). Skipping.");
                        continue;
                    }

                    // GitHub zip contains a single top-level directory named like <repo>-<branch>
                    Path innerRoot;
                    try (var stream = Files.list(tempDir)) {
                        innerRoot = stream.filter(Files::isDirectory).findFirst().orElse(tempDir);
                    }

                    // Copy test-cases
                    Path srcTestCases = innerRoot.resolve("test-cases");
                    if (testCasesDir == null) {
                        System.out.println("• Skipped " + repo + " test-cases (destination not configured)");
                    } else if (Files.isDirectory(srcTestCases)) {
                        Path destTestCases = testCasesDir.resolve(repo);
                        Files.createDirectories(destTestCases);
                        copyDir(srcTestCases, destTestCases);
                        System.out.println("• Copied test-cases: " + srcTestCases + " -> " + destTestCases);
                    } else {
                        System.out.println("• Skipped " + repo + " (no test-cases directory found)");
                    }

                    // Copy shapes
                    Path srcShapes = innerRoot.resolve("shapes");
                    if (shapesDir == null) {
                        System.out.println("• Skipped " + repo + " shapes (destination not configured)");
                    } else if (Files.isDirectory(srcShapes)) {
                        Path destShapes = shapesDir.resolve(repo);
                        Files.createDirectories(destShapes);
                        copyDir(srcShapes, destShapes);
                        System.out.println("• Copied shapes: " + srcShapes + " -> " + destShapes);
                    } else {
                        System.out.println("• Skipped " + repo + " (no shapes directory found)");
                    }

                    // Copy vocabulary
                    Path srcVoc = innerRoot.resolve("ontology/" + repo + ".owl");
                    if (vocabulariesDir == null) {
                        System.out.println("• Skipped " + repo + " vocabulary (destination not configured)");
                    } else if (Files.exists(srcVoc)) {
                        Path destVoc = vocabulariesDir.resolve(repo + ".owl");
                        Files.createDirectories(vocabulariesDir);
                        Files.copy(srcVoc, destVoc, StandardCopyOption.REPLACE_EXISTING);
                        System.out.println("• Copied vocabulary: " + srcVoc + " -> " + destVoc);
                    } else {
                        System.out.println("• Skipped " + repo + " (no ontology file found)");
                    }

                } finally {
                    // Cleanup extracted content
                    safeDeleteRecursively(tempDir);
                }
            } catch (Exception e) {
                System.err.println();
                System.err.println("Error occurred while processing " + repo + ".");
                System.err.println("• Check the repository URLs and your internet connection.");
                System.err.println("Details: " + e.getMessage());
            }
        }

        System.out.println("Done.");
    }

    private static boolean downloadWithFallback(String url1, String url2, Path dest) {
        try {
            return downloadAndExtract(url1, dest);
        } catch (IOException e) {
            try {
                return downloadAndExtract(url2, dest);
            } catch (IOException ex) {
                return false;
            }
        }
    }

    private static boolean downloadAndExtract(String urlStr, Path destDir) throws IOException {
        try {
            URI uri = new URI(urlStr);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/zip");

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                try (InputStream is = conn.getErrorStream()) {
                    if (is != null) is.close();
                }
                return false;
            }

            Files.createDirectories(destDir);
            try (InputStream is = conn.getInputStream();
                 BufferedInputStream bis = new BufferedInputStream(is)) {
                unzipStream(bis, destDir);
            }
            return true;
        } catch (URISyntaxException e) {
            throw new IOException(e);
        }
    }

    private static void unzipStream(InputStream input, Path destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(input)) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                Path outPath = destDir.resolve(entry.getName()).normalize();
                // Prevent Zip Slip
                if (!outPath.startsWith(destDir)) {
                    throw new IOException("Bad zip entry: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    copyStream(zis, outPath);
                }
                zis.closeEntry();
                entry = zis.getNextEntry();
            }
        }
    }

    private static void copyStream(InputStream input, Path dest) throws IOException {
        try (OutputStream out = Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            input.transferTo(out);
        }
    }

    private static void copyDir(Path src, Path dest) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public @NonNull FileVisitResult preVisitDirectory(@NonNull Path dir, @NonNull BasicFileAttributes attrs) throws IOException {
                Path target = dest.resolve(src.relativize(dir));
                Files.createDirectories(target);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
                Path target = dest.resolve(src.relativize(file));
                Files.createDirectories(target.getParent());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void safeDeleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) return;
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public @NonNull FileVisitResult visitFile(@NonNull Path file, @NonNull BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NonNull FileVisitResult postVisitDirectory(@NonNull Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
