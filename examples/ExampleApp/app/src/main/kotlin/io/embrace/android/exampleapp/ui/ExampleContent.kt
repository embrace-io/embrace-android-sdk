package io.embrace.android.exampleapp.ui

import androidx.compose.runtime.Composable
import io.embrace.android.exampleapp.ui.examples.AddBreadcrumbExample
import io.embrace.android.exampleapp.ui.examples.LogMessageExample

@Composable
fun ExampleContent(example: CodeExample) {
    when (example) {
        CodeExample.ADD_BREADCRUMB -> AddBreadcrumbExample()
        CodeExample.LOG_MESSAGE -> LogMessageExample()
    }
}
