package io.embrace.android.embracesdk.config.remote

import io.embrace.android.embracesdk.annotation.InternalApi

@InternalApi
public enum class Unwinder(internal val code: Int) {
    LIBUNWIND(0),
    LIBUNWINDSTACK(1)
}
