package io.embrace.android.exampleapp.ui

enum class CodeExample(
    val desc: String,
) {
    JVM_CRASH("JVM Uncaught Exception"),
    NDK_CRASH("NDK crash"),
    ANR_DETECTION("ANR detection"),
    TRACING("Tracing API"),
    ADD_BREADCRUMB("Add Breadcrumb"),
    LOG_MESSAGE("Send Log Message"),
    LOG_MESSAGE_ATTACHMENT("Log with Attachment"),
    RECORD_NETWORK_REQUEST("Network Requests"),
    SESSION_PROPERTIES("Session Properties"),
    END_SESSION("End Session"),
    ALTER_USER("Alter User"),
    VIEW_TRACKING("View Tracking"),
    SDK_STATE_API("SDK State API");

    val route: String = name.lowercase()
}
