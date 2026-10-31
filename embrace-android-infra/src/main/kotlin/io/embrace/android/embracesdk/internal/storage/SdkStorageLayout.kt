package io.embrace.android.embracesdk.internal.storage

/**
 * Declares where the SDK's storage root lives and which [SdkDir]s sit in each directory.
 */
object SdkStorageLayout {

    /**
     * The name of the storage root within the app's no-backup files directory
     */
    const val ROOT_DIR_NAME: String = "xcor"

    /**
     * The name of the directory the legacy storage migration stages files in before renaming it to [ROOT_DIR_NAME]
     */
    const val MIGRATION_DIR_NAME: String = "xcor.migration"

    /**
     * Returns the directories declared directly in [dir], or directly in the root if [dir] is null.
     */
    fun children(dir: SdkDir?): List<SdkDir> = SdkDir.entries.filter { it.parent == dir }
}
