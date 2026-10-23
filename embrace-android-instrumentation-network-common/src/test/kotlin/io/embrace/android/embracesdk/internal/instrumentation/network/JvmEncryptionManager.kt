package io.embrace.android.embracesdk.internal.instrumentation.network

import java.util.Base64

/**
 * A [NetworkCaptureEncryptionManager] backed by `java.util.Base64`, as `android.util.Base64` is a stub on the JVM.
 */
internal fun jvmEncryptionManager(): NetworkCaptureEncryptionManager = NetworkCaptureEncryptionManager(
    encodeBase64 = { Base64.getMimeEncoder().encodeToString(it) },
    decodeBase64 = { Base64.getMimeDecoder().decode(it) },
)
