@file:OptIn(ExperimentalSerializationApi::class)

package io.embrace.android.embracesdk.internal.serialization

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo

/**
 * Cache schema version of a `@Serializable` type stored via [EmbraceBinary] — a 64-bit uid
 * analogous to `serialVersionUID`. It is written as the stream header and bumping it invalidates
 * previously-cached entries of the type.
 *
 * [uid] must equal the type's structural fingerprint, which is verified at build time (see
 * `EmbraceBinaryVersionGuardTest`). Only the root cached type needs this annotation.
 */
@SerialInfo
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class BinaryVersion(val uid: Long)
