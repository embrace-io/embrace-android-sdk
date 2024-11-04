package io.embrace.android.exampleapp.ui

enum class CodeExample(
    val desc: String,
) {
    ADD_BREADCRUMB("Add Breadcrumb"),
    LOG_MESSAGE("Log Message");

    val route: String = name.lowercase()
}
