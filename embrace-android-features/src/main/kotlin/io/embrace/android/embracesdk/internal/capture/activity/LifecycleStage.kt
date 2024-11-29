package io.embrace.android.embracesdk.internal.capture.activity

/**
 * The defined stages in the UI Load lifecycle for which child spans will be logged, if applicable.
 */
enum class LifecycleStage(private val typeName: String) {
    CREATE("create"),
    START("start"),
    RESUME("resume"),
    RENDER("render");

    fun spanName(componentName: String): String = "$componentName-$typeName"
}
