package io.embrace.android.embracesdk.internal.storage

import io.embrace.android.embracesdk.internal.config.PersistedConfig
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that the orphan sweeper's allow-list for the remote config dir accepts every file the config store writes.
 */
internal class SweptRemoteConfigNamesTest {

    @Test
    fun `every remote config file is allowed`() {
        listOf(
            PersistedConfig.CONFIG_RESPONSE_FILE_NAME,
            PersistedConfig.ETAG_FILE_NAME,
            PersistedConfig.CACHED_CONFIG_FILE_NAME,
        ).forEach { name ->
            assertTrue("$name rejected", SdkDir.REMOTE_CONFIG.allowedEntries(listOf(name)))
        }
    }
}
