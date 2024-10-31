package io.embrace.android.embracesdk


/*** Hooks that get package-private info from Embrace for test infra purposes ***/

internal object EmbraceHooks {

    internal fun setImpl(impl: EmbraceImpl) {
        Embrace.getInstance().impl = impl
    }

    internal fun stop() {
        Embrace.getInstance().impl.stop()
    }
}

