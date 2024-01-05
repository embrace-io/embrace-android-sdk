package io.embrace.android.embracesdk.utils

/**
 * Simple wrapper class for accessing mutable fields across Threads during tests
 */
public class Mutable<T>(@JvmField public var value: T)
