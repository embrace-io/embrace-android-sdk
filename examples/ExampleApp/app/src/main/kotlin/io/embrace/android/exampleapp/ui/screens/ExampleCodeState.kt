package io.embrace.android.exampleapp.ui.screens

import io.embrace.android.embracesdk.Embrace

data class ExampleCodeState(
    val text: String,
    val action: () -> Unit,
)

val codeExamples = listOf(
    ExampleCodeState("Add Breadcrumb") { Embrace.getInstance().addBreadcrumb("Hello, world!") },
    ExampleCodeState("Log Message") { Embrace.getInstance().logInfo("Hello, world!") }
)
