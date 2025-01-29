package io.embrace.android.embracesdk.internal.delivery

enum class PayloadType(
    val value: String,
    val filenameComponent: String,
) {
    SESSION("ux.session", "session"),
    LOG("sys.log", "log"),
    CRASH("sys.android.crash", "crash"),
    NATIVE_CRASH("sys.android.native_crash", "native"),
    REACT_NATIVE_CRASH("sys.android.react_native_crash", "react"),
    FLUTTER_EXCEPTION("sys.flutter_exception", "flutter"),
    AEI("sys.exit", "aei"),
    EXCEPTION("sys.exception", "exception"),
    NETWORK_CAPTURE("sys.network_capture", "network"),
    INTERNAL_ERROR("sys.internal", "internal"),
    ATTACHMENT("attachment", "attachment"),
    UNKNOWN("unknown", "unknown");

    companion object {

        private val filenameMap = PayloadType.values().associateBy { it.filenameComponent }

        fun fromValue(value: String?): PayloadType {
            return values().firstOrNull { it.value == value } ?: UNKNOWN
        }

        fun fromFilenameComponent(component: String): PayloadType = filenameMap[component] ?: UNKNOWN
    }
}
