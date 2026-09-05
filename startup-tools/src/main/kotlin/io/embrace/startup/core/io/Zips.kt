package io.embrace.startup.core.io

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
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
