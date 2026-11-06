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
     * The version of this layout. Raise it whenever the layout starts allowing a directory or entry it didn't before,
     * so that an older SDK running after a downgrade doesn't sweep what a newer one wrote.
     */
    const val LAYOUT_VERSION: Int = 1

    /**
     * The file in the root that records the highest [LAYOUT_VERSION] that has run on this install
     */
    const val LAYOUT_VERSION_FILE_NAME: String = "layout_version"

    /*
     * The files allowed in [SdkDir.INSTALL_STATE]. The .bin files' stores aren't wired in yet: until they are,
     * nothing writes them.
     */

    const val ORDINALS_FILE_NAME: String = "ordinals.bin"
    const val PERSISTED_SDK_STATE_FILE_NAME: String = "persisted_sdk_state.bin"
    const val NETWORK_CAPTURE_FILE_NAME: String = "network_capture.bin"
    const val AEI_DELIVERED_FILE_NAME: String = "aei_delivered.bin"
    const val CRASH_MARKER_FILE_NAME: String = "crash_marker"

    /**
     * Returns the directories declared directly in [dir], or directly in the root if [dir] is null.
     */
    fun children(dir: SdkDir?): List<SdkDir> = SdkDir.entries.filter { it.parent == dir }
}
