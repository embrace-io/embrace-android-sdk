package io.embrace.android.embracesdk.internal.gating

internal interface Sanitizable<T> {

    fun sanitize(): T?
}
