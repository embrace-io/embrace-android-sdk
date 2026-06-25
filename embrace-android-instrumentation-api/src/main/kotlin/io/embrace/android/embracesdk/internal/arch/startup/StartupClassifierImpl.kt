package io.embrace.android.embracesdk.internal.arch.startup

import java.util.concurrent.atomic.AtomicReference

class StartupClassifierImpl : StartupClassifier {

    private val startupType: AtomicReference<StartupType?> = AtomicReference(null)

    override fun startupType(): StartupType? = startupType.get()

    override fun evaluateStartup(
        sdkInitEndMs: Long?,
        appInitEndMs: Long?,
        postAppInitTimeMs: Long,
    ) {
        val initEndMs = appInitEndMs ?: sdkInitEndMs ?: return
        startupType.compareAndSet(
            null,
            if (postAppInitTimeMs - initEndMs <= MAX_COLD_STARTUP_INIT_GAP_MS) {
                StartupType.COLD
            } else {
                StartupType.WARM
            },
        )
    }

    override fun assumeBackgroundStartup() {
        startupType.compareAndSet(null, StartupType.BACKGROUND)
    }
}
