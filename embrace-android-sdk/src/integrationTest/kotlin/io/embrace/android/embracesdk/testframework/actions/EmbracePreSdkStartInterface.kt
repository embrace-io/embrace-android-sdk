package io.embrace.android.embracesdk.testframework.actions

import io.embrace.android.embracesdk.internal.storage.SdkDir
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.api.SdkApi

internal class EmbracePreSdkStartInterface(
    private val setup: EmbraceSetupInterface,
    private val embraceSupplier: () -> SdkApi,
) {
    val embrace by lazy { embraceSupplier() }

    val clock: FakeClock
        get() = setup.getClock()

    /**
     * Asserts that no config has been persisted on disk yet.
     */
    internal fun assertNoConfigPersisted() {
        val storageDir = setup.sdkStorage().path(SdkDir.REMOTE_CONFIG).toFile()
        if (storageDir.exists()) {
            throw IllegalStateException("Did not expect config storage directory to exist.")
        }
    }
}
