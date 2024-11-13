package io.embrace.android.embracesdk.testframework.actions

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.fakes.FakeClock
import java.io.File

internal class EmbracePreSdkStartInterface(
    private val setup: EmbraceSetupInterface,
) {
    val embrace = Embrace.getInstance()

    val clock: FakeClock
        get() = setup.overriddenClock

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
