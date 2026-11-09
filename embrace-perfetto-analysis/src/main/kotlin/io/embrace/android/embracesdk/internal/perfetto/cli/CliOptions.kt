package io.embrace.android.embracesdk.internal.perfetto.cli

import io.embrace.android.embracesdk.internal.perfetto.report.ReportFormat
import okio.Path

internal data class CliOptions(
    val inputs: List<Path>,
    val dryRun: Boolean = false,
    val operations: List<String> = emptyList(),
    val format: ReportFormat = ReportFormat.MARKDOWN,
    val output: Path,
) {
    val input: Path get() = inputs.first()
    val allOperations: Boolean get() = operations.isEmpty()
}
