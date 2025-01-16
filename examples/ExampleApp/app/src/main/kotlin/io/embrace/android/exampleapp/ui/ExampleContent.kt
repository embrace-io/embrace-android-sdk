package io.embrace.android.exampleapp.ui

import androidx.compose.runtime.Composable
import io.embrace.android.exampleapp.ui.examples.AddBreadcrumbExample
import io.embrace.android.exampleapp.ui.examples.AnrDetectionExample
import io.embrace.android.exampleapp.ui.examples.EndSessionExample
import io.embrace.android.exampleapp.ui.examples.JvmCrashExample
import io.embrace.android.exampleapp.ui.examples.LogMessageAttachmentsExample
import io.embrace.android.exampleapp.ui.examples.NdkCrashExample
import io.embrace.android.exampleapp.ui.examples.LogMessageExample
import io.embrace.android.exampleapp.ui.examples.NetworkRequestExample
import io.embrace.android.exampleapp.ui.examples.SdkStateApiExample
import io.embrace.android.exampleapp.ui.examples.SessionPropertiesExample
import io.embrace.android.exampleapp.ui.examples.TracingApiExample
import io.embrace.android.exampleapp.ui.examples.UserExample
import io.embrace.android.exampleapp.ui.examples.ViewTrackingExample

@Composable
fun ExampleContent(example: CodeExample) {
    when (example) {
        CodeExample.TRACING -> TracingApiExample()
        CodeExample.ADD_BREADCRUMB -> AddBreadcrumbExample()
        CodeExample.LOG_MESSAGE -> LogMessageExample()
        CodeExample.LOG_MESSAGE_ATTACHMENT -> LogMessageAttachmentsExample()
        CodeExample.RECORD_NETWORK_REQUEST -> NetworkRequestExample()
        CodeExample.SESSION_PROPERTIES -> SessionPropertiesExample()
        CodeExample.END_SESSION -> EndSessionExample()
        CodeExample.ALTER_USER -> UserExample()
        CodeExample.VIEW_TRACKING -> ViewTrackingExample()
        CodeExample.SDK_STATE_API -> SdkStateApiExample()
        CodeExample.JVM_CRASH -> JvmCrashExample()
        CodeExample.NDK_CRASH -> NdkCrashExample()
        CodeExample.ANR_DETECTION -> AnrDetectionExample()
    }
}
