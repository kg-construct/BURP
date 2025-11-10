package burp.tools

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Cross-platform fetcher that downloads RML module repositories as ZIP archives
 * (no need for Git installed), extracts them, copies test-cases and shapes
 * into this project, and cleans up temporary files.
 *
 * Usage (recommended via Maven):
 *   mvn -q -DskipTests=true -Dexec.mainClass=burp.tools.FetchTestCases exec:java
 */
object FetchTestCases {

    @JvmStatic
    fun main(args: Array<String>) {
        val resourcesDir = Paths.get("src/test/resources")
        val shapesResourcesDir = Paths.get("src/main/resources/shapes")

        val repos = listOf(
            "rml-core",
            "rml-cc",
            "rml-io",
            "rml-fnml",
            "rml-lv",
            "rml-io-registry"
        )

        for (repo in repos) {
            val base = "https://github.com/kg-construct/$repo"
            val zipMain = "$base/archive/refs/heads/main.zip"
            val zipMaster = "$base/archive/refs/heads/master.zip"

            try {
                println("Downloading and extracting $repo …")
                val tempDir = Files.createTempDirectory("burp-fetch-$repo")

                try {
                    if (!downloadWithFallback(zipMain, zipMaster, tempDir)) {
                        System.err.println("• Failed to download $repo (main/master not found). Skipping.")
                        continue
                    }

                    // GitHub zip contains a single top-level directory named like <repo>-<branch>
                    val innerRoot = Files.list(tempDir).use { stream ->
                        stream.filter { Files.isDirectory(it) }.findFirst().orElse(tempDir)
                    }

                    // Copy test-cases
                    val srcTestCases = innerRoot.resolve("test-cases")
                    val destTestCases = resourcesDir.resolve(repo)
                    if (Files.isDirectory(srcTestCases)) {
                        Files.createDirectories(destTestCases)
                        copyDir(srcTestCases, destTestCases)
                        println("• Copied test-cases: $srcTestCases -> $destTestCases")
                    } else {
                        println("• Skipped $repo (no test-cases directory found)")
                    }

                    // Copy shapes
                    val srcShapes = innerRoot.resolve("shapes")
                    val destShapes = shapesResourcesDir.resolve(repo)
                    if (Files.isDirectory(srcShapes)) {
                        Files.createDirectories(destShapes)
                        copyDir(srcShapes, destShapes)
                        println("• Copied shapes: $srcShapes -> $destShapes")
                    } else {
                        println("• Skipped $repo (no shapes directory found)")
                    }
                } finally {
                    // Cleanup extracted content
                    safeDeleteRecursively(tempDir)
                }
            } catch (e: Exception) {
                System.err.println()
                System.err.println("Error occurred while processing $repo.")
                System.err.println("• Check the repository URLs and your internet connection.")
                System.err.println("Details: ${e.message}")
            }
        }

        println("Done.")
    }



    private fun downloadWithFallback(url1: String, url2: String, dest: Path): Boolean {
        return try {
            downloadAndExtract(url1, dest); true
        } catch (_: IOException) {
            try {
                downloadAndExtract(url2, dest); true
            } catch (_: IOException) {
                false
            }
        }
    }

    private fun downloadAndExtract(urlStr: String, destDir: Path): Boolean {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = Duration.ofSeconds(20).toMillis().toInt()
                readTimeout = Duration.ofSeconds(60).toMillis().toInt()
                requestMethod = "GET"
                setRequestProperty("Accept", "application/zip")
            }
            if (conn.responseCode !in 200..299) {
                conn.inputStream?.close()
                return false
            }

            // Stream directly from HTTP to ZIP extraction
            Files.createDirectories(destDir)
            BufferedInputStream(conn.inputStream).use { input ->
                unzipStream(input, destDir)
            }
            true
        } catch (_: IOException) {
            false
        }
    }

    private fun unzipStream(input: InputStream, destDir: Path) {
        ZipInputStream(input).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val outPath = destDir.resolve(entry.name).normalize()
                // Prevent Zip Slip
                if (!outPath.startsWith(destDir)) {
                    throw IOException("Bad zip entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    Files.createDirectories(outPath)
                } else {
                    Files.createDirectories(outPath.parent)
                    copyStream(zis, outPath)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }


    private fun copyStream(input: InputStream, dest: Path) {
        Files.newOutputStream(dest, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { out ->
            input.copyTo(out)
        }
    }

    private fun copyDir(src: Path, dest: Path) {
        Files.walkFileTree(src, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                val target = dest.resolve(src.relativize(dir))
                Files.createDirectories(target)
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                val target = dest.resolve(src.relativize(file))
                Files.createDirectories(target.parent)
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                return FileVisitResult.CONTINUE
            }
        })
    }

    private fun safeDeleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.deleteIfExists(file)
                return FileVisitResult.CONTINUE
            }

            override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                Files.deleteIfExists(dir)
                return FileVisitResult.CONTINUE
            }
        })
    }
}