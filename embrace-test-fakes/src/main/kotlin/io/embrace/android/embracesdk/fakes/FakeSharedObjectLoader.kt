package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.SharedObjectLoader
import java.util.concurrent.atomic.AtomicBoolean

class FakeSharedObjectLoader(
    var failLoad: Boolean = false
) : SharedObjectLoader {

    var throwWhenLoading = false

    override val loaded: AtomicBoolean = AtomicBoolean(false)

    override fun loadEmbraceNative(): Boolean {
        if (throwWhenLoading) {
            throw SecurityException()
        }
        loaded.set(!failLoad)
        return loaded.get()
    }
}
