package io.embrace.android.embracesdk.gating

internal interface Sanitizable<T> {

    fun sanitize(): T?
}
