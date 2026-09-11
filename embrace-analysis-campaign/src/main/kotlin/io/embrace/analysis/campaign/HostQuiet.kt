package io.embrace.analysis.campaign

import io.embrace.analysis.common.json.PyJson

/**
 * No competing driver in flight on the host: another campaign or cell runner, Python or Kotlin. This
 * process and its ancestors are excluded - the shell that launched `cell-runner` carries the marker in
 * its own command line, which the first real check-only run tripped over.
 */
class HostQuiet(private val processList: () -> String) : Invariant {

    override fun check(): Check {
        val mine = HashSet<String>()
        var handle: java.util.Optional<ProcessHandle> = java.util.Optional.of(ProcessHandle.current())
        while (handle.isPresent) {
            mine.add(handle.get().pid().toString())
            handle = handle.get().parent()
        }
        val others = processList().lines().filter { line ->
            val pid = line.trim().substringBefore(" ")
            DRIVER_MARKERS.any { it in line } && pid !in mine
        }
        return if (others.isEmpty()) {
            Check(NAME, true, "host quiet")
        } else {
            Check(NAME, false, "other drivers alive: ${PyJson.reprList(others.take(2))}")
        }
    }

    companion object {
        const val NAME: String = "host quiet"
        private val DRIVER_MARKERS =
            listOf("fleet_campaign", "fleet-campaign", "device_driver", "p6b", "cell_runner", "cell-runner")
    }
}
