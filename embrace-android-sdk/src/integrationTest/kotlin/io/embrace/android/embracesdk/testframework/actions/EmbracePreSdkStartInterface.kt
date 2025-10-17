package io.embrace.android.embracesdk.testframework.actions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.fakes.FakeClock
import io.embrace.android.embracesdk.internal.api.SdkApi
import java.io.File

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
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val storageDir = File(ctx.filesDir, "embrace_remote_config")
        if (storageDir.exists()) {
            throw IllegalStateException("Did not expect config storage directory to exist.")
        }
    }
}
