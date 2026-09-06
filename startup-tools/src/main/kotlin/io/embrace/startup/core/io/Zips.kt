package io.embrace.startup.core.io

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Archived record sets. A generated set that is written once and never edited afterwards - one campaign's
 * per-pass datasets, one experiment's result files, the frozen trace goldens - is kept as a single deflated
 * archive rather than as hundreds of loose files, and unpacked to a temporary directory when something needs
 * to read it. Anything a person edits or reviews as a diff stays a plain file.
 */
object Zips {

    /** Pack [files] as flat, deflated entries into [dest] (replacing it); the caller decides the order. */
    fun pack(files: List<Path>, dest: Path) {
        dest.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        ZipOutputStream(Files.newOutputStream(dest)).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(file.fileName.toString()))
                Files.copy(file, zip)
                zip.closeEntry()
            }
        }
    }

    /** Pack every file under [root] into [dest], keeping the tree's own relative paths as entry names. */
    fun packTree(root: Path, dest: Path) {
        val files = Files.walk(root).use { walk -> walk.filter { Files.isRegularFile(it) }.sorted().toList() }
        dest.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        ZipOutputStream(Files.newOutputStream(dest)).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(ZipEntry(root.relativize(file).toString()))
                Files.copy(file, zip)
                zip.closeEntry()
            }
        }
    }

    /**
     * Add [files] to [dest] by file name, keeping every entry it already holds and never replacing one.
     * Returns the entry count afterwards. Used to fold a run's loose output into the archive for its month.
     */
    fun merge(dest: Path, files: List<Path>): Int {
        val existing = LinkedHashMap<String, ByteArray>()
        if (Files.exists(dest)) {
            ZipFile(dest.toFile()).use { zip ->
                zip.entries().asSequence().filterNot { it.isDirectory }.forEach { entry ->
                    existing[entry.name] = zip.getInputStream(entry).use { it.readBytes() }
                }
            }
        }
        val added = files.filterNot { existing.containsKey(it.fileName.toString()) }
        dest.toAbsolutePath().parent?.let { Files.createDirectories(it) }
        val temp = dest.resolveSibling("${dest.fileName}.tmp")
        ZipOutputStream(Files.newOutputStream(temp)).use { zip ->
            existing.toSortedMap().forEach { (name, blob) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(blob)
                zip.closeEntry()
            }
            added.forEach { file ->
                zip.putNextEntry(ZipEntry(file.fileName.toString()))
                Files.copy(file, zip)
                zip.closeEntry()
            }
        }
        Files.move(temp, dest, StandardCopyOption.REPLACE_EXISTING)
        return existing.size + added.size
    }

    /**
     * Unpack [archive] into [into], keeping any nested entries. An absolute entry, or one with a `..`
     * component, is refused rather than written outside the target.
     */
    fun unpack(archive: Path, into: Path): Path {
        ZipInputStream(Files.newInputStream(archive)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val target = resolveEntry(into, entry.name, archive)
                if (entry.isDirectory) {
                    Files.createDirectories(target)
                } else {
                    target.parent?.let { Files.createDirectories(it) }
                    Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING)
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return into
    }

    /** [unpack] into a fresh temporary directory named after the archive. */
    fun unpackToTemp(archive: Path): Path {
        val stem = archive.fileName.toString().removeSuffix(".zip")
        return unpack(archive, Files.createTempDirectory("$stem-"))
    }

    private fun resolveEntry(into: Path, name: String, archive: Path): Path {
        require(!name.startsWith("/") && !name.startsWith("\\") && name.split('/', '\\').none { it == ".." }) {
            "refusing entry '$name' in $archive"
        }
        return into.resolve(name)
    }
}
