package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.logging.ErrorHandler
import io.embrace.android.embracesdk.internal.logging.SdkErrorType
import io.embrace.android.embracesdk.internal.logging.guardSdkCode
import okio.Path

/**
 * Deletes everything under [storage]'s root that [SdkStorageLayout] doesn't declare: unknown files and directories at
 * any level, and entries a leaf [SdkDir]'s [SdkDir.allowedEntries] rejects. In a directory with
 * [SdkDir.sweepRequiresStale], an entry is only deleted once its own mtime is before [processStartMs].
 *
 * The root's [SdkStorageLayout.LAYOUT_VERSION_FILE_NAME] records the highest [layoutVersion] that has run. If it is
 * higher than [layoutVersion], or can't be parsed, a newer SDK may have written this layout and the sweep is skipped.
 */
class OrphanSweeper(
    private val storage: SdkStorageImpl,
    private val layoutVersion: Int,
    private val processStartMs: Long,
    private val errorHandler: ErrorHandler,
) {

    private val fileSystem = storage.fileSystem

    /**
     * Runs the sweep and returns the number of entries deleted. Never throws.
     */
    fun sweep(): Int = errorHandler.guardSdkCode(SdkErrorType.OrphanSweepFail, 0) {
        when {
            !fileSystem.exists(storage.root) || !claimLayoutVersion() -> 0
            else -> sweepDir(storage.root, null)
        }
    }

    private fun claimLayoutVersion(): Boolean {
        val marker = storage.root / SdkStorageLayout.LAYOUT_VERSION_FILE_NAME
        if (fileSystem.metadataOrNull(marker) != null) {
            val contents = fileSystem.read(marker) { readUtf8() }
            val stored = contents.trim().toIntOrNull()
            if (stored == null || stored > layoutVersion) {
                errorHandler.log(
                    "Orphan sweep skipped: layout version '$contents' isn't at or below $layoutVersion",
                    severity = ErrorHandler.Severity.WARNING,
                )
                return false
            }
            if (stored == layoutVersion) {
                return true
            }
        }
        // a failed write leaves the stored version at or below this one
        errorHandler.guardSdkCode(SdkErrorType.OrphanSweepFail) {
            fileSystem.writeAtomically(marker) { it.writeUtf8(layoutVersion.toString()) }
        }
        return true
    }

    private fun sweepDir(dir: Path, sdkDir: SdkDir?): Int {
        val children = SdkStorageLayout.children(sdkDir)
        if (sdkDir != null && children.isEmpty()) {
            return sweepLeaf(dir, sdkDir, emptyList())
        }
        val requiresStale = sdkDir?.sweepRequiresStale == true
        return sumEntries(dir) { entry ->
            val child = children.find { it.dirName == entry.name }
            when {
                child != null && fileSystem.metadataOrNull(entry)?.isDirectory == true -> sweepDir(entry, child)
                sdkDir == null && entry.name == SdkStorageLayout.LAYOUT_VERSION_FILE_NAME -> 0
                else -> delete(entry, requiresStale)
            }
        }
    }

    private fun sweepLeaf(dir: Path, sdkDir: SdkDir, segments: List<String>): Int = sumEntries(dir) { entry ->
        val entrySegments = segments + entry.name
        when {
            sdkDir.allowedEntries(entrySegments) -> sweepLeaf(entry, sdkDir, entrySegments)
            else -> delete(entry, sdkDir.sweepRequiresStale)
        }
    }

    /**
     * Applies [action] to each entry of [dir], guarding each one separately. Returns 0 if [dir] isn't a directory.
     */
    private inline fun sumEntries(dir: Path, action: (Path) -> Int): Int =
        fileSystem.listOrNull(dir).orEmpty().sumOf { entry ->
            errorHandler.guardSdkCode(SdkErrorType.OrphanSweepFail, 0) { action(entry) }
        }

    private fun delete(entry: Path, requiresStale: Boolean): Int {
        if (requiresStale && !isStale(entry)) {
            return 0
        }
        fileSystem.deleteRecursively(entry)
        return 1
    }

    private fun isStale(entry: Path): Boolean =
        fileSystem.metadataOrNull(entry)?.lastModifiedAtMillis?.let { it < processStartMs } == true
}
