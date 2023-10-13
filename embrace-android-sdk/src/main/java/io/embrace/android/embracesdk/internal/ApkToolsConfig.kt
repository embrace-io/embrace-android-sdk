package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.BuildConfig

/**
 * The purpose of this class is allowing us to change its flags with APKTools, directly from the bytecode.
 * This shouldn't have any uses in the code, besides unit tests.
 */
internal object ApkToolsConfig {

    @JvmField
    var IS_DEVELOPER_LOGGING_ENABLED: Boolean = BuildConfig.DEBUG

    @JvmField
    var IS_SDK_DISABLED: Boolean = false

    @JvmField
    var IS_EXCEPTION_CAPTURE_DISABLED: Boolean = false

    @JvmField
    var IS_NDK_DISABLED: Boolean = false

    @JvmField
    var IS_ANR_MONITORING_DISABLED: Boolean = false

    @JvmField
    var IS_BREADCRUMB_TRACKING_DISABLED: Boolean = false

    @JvmField
    var IS_NETWORK_CAPTURE_DISABLED: Boolean = false
}
