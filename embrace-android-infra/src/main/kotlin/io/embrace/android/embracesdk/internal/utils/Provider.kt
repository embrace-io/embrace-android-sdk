package io.embrace.android.embracesdk.internal.utils

/**
 * Function that returns an instance of T meant to represent a provider/supplier that does not require any input parameters
 */
typealias Provider<T> = () -> T
