package io.embrace.android.embracesdk.internal.injection

import java.io.OutputStream

/**
 * Typealias for a function that writes to an [OutputStream]. This is used to make it
 * easier to pass around logic for serializing data to arbitrary streams.
 */
typealias SerializationAction = (outputStream: OutputStream) -> Unit
