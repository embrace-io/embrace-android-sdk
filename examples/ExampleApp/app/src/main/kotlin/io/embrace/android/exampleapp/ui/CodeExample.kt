package io.embrace.android.exampleapp.ui

enum class CodeExample(
    val desc: String,
    val route : String = toString().lowercase()
) {
    ADD_BREADCRUMB("Add Breadcrumb"),
    LOG_MESSAGE("Log Message");
}
