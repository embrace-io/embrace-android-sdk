package io.embrace.android.embracesdk


/*** Hooks that get package-private info from Embrace for test infra purposes ***/

internal object EmbraceHooks {

    internal fun getProcessStateService() = checkNotNull(Embrace.getImpl().processStateService)

    internal fun setImpl(impl: EmbraceImpl) {
        Embrace.setImpl(impl)
    }

    internal fun stop() {
        Embrace.getImpl().stop()
    }
}

