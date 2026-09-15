package io.embrace.android.embracesdk.internal.perfetto

import java.io.File

internal data class CliOptions(
    val inputs: List<File>,
    val dryRun: Boolean = false,
    val operations: List<String> = emptyList(),
    val format: ReportFormat = ReportFormat.MARKDOWN,
    val output: File,
) {
    val input: File get() = inputs.first()
    val allOperations: Boolean get() = operations.isEmpty()
}
