package io.embrace.android.embracesdk.internal.delivery

enum class PayloadType(
    val value: String,
) {
    SESSION("ux.session"),
    LOG("sys.log"),
    CRASH("sys.android.crash"),
    NATIVE_CRASH("sys.android.native_crash"),
    REACT_NATIVE_CRASH("sys.android.react_native_crash"),
    FLUTTER_EXCEPTION("sys.flutter_exception"),
    AEI("sys.exit"),
    EXCEPTION("sys.exception"),
    NETWORK_CAPTURE("sys.network_capture"),
    INTERNAL_ERROR("sys.internal"),
    UNKNOWN("unknown");

    companion object {
        fun fromValue(value: String?): PayloadType {
            return values().firstOrNull { it.value == value } ?: UNKNOWN
        }

        fun toFilenamePart(payloadType: PayloadType): String {
            return when (payloadType) {
                SESSION -> "session"
                CRASH -> "crash"
                LOG -> "log"
                NATIVE_CRASH -> "native"
                REACT_NATIVE_CRASH -> "react"
                FLUTTER_EXCEPTION -> "flutter"
                AEI -> "aei"
                EXCEPTION -> "exception"
                NETWORK_CAPTURE -> "network"
                INTERNAL_ERROR -> "internal"
                else -> "unknown"
            }
        }

        fun fromFilenameComponent(component: String): PayloadType {
            return when (component) {
                "session" -> SESSION
                "crash" -> CRASH
                "log" -> LOG
                "native" -> NATIVE_CRASH
                "react" -> REACT_NATIVE_CRASH
                "flutter" -> FLUTTER_EXCEPTION
                "aei" -> AEI
                "exception" -> EXCEPTION
                "network" -> NETWORK_CAPTURE
                "internal" -> INTERNAL_ERROR
                else -> UNKNOWN
            }
        }
    }
}
