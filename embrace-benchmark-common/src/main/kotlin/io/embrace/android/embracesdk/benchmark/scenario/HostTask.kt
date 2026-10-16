package io.embrace.android.embracesdk.benchmark.scenario

/**
 * What a launch of the app's scenario host runs, and what it runs on.
 *
 * A task reaches the app as the launch intent's extras: the benchmark writes them with [toExtras] and the
 * app reads the task back with [hostTaskFrom], so the extras a task needs are known only here. They are
 * all strings, because macrobenchmark launches the app through `am start` with the intent as a URI, which
 * carries only primitive extras.
 */
sealed interface HostTask {

    /**
     * Which task this is, which the launch intent carries by its [Key.value].
     */
    val key: Key

    /**
     * The extras that carry what the task runs on, besides its key.
     */
    val arguments: Map<String, String>

    /**
     * Runs [scenario], then ends the user session it produced.
     */
    data class RunPersistenceScenario(val scenario: ScenarioSpec) : HostTask {
        override val key: Key
            get() = Key.RUN_PERSISTENCE_SCENARIO

        override val arguments: Map<String, String>
            get() = mapOf(EXTRA_SCENARIO_ID to scenario.id)
    }

    /**
     * Runs [scenario], then adds what it left on disk to the recorded fixture.
     */
    data class RecordFixture(val scenario: ScenarioSpec) : HostTask {
        override val key: Key
            get() = Key.RECORD_FIXTURE

        override val arguments: Map<String, String>
            get() = mapOf(EXTRA_SCENARIO_ID to scenario.id)
    }

    /**
     * Restores the recorded fixture where the SDK reads it, in a launch that leaves the SDK stopped.
     */
    data object RestoreFixture : HostTask {
        override val key: Key = Key.RESTORE_FIXTURE
        override val arguments: Map<String, String> = emptyMap()
    }

    /**
     * Waits for the SDK to resurrect the session parts that were on disk when it started, which a previous
     * launch restored.
     */
    data object AwaitResurrection : HostTask {
        override val key: Key = Key.AWAIT_RESURRECTION
        override val arguments: Map<String, String> = emptyMap()
    }

    /**
     * Identifies each kind of task, one entry per task. [value] is the string that the launch intent carries.
     */
    enum class Key(val value: String) {
        RUN_PERSISTENCE_SCENARIO("run_persistence_scenario"),
        RECORD_FIXTURE("record_fixture"),
        RESTORE_FIXTURE("restore_fixture"),
        AWAIT_RESURRECTION("await_resurrection"),
    }
}

/**
 * The launch intent extras that carry this task to the app: its [HostTask.arguments] and its key, which
 * goes last so that no task's arguments can replace it.
 */
fun HostTask.toExtras(): Map<String, String> = arguments + (EXTRA_TASK to key.value)

/**
 * The task carried by the launch intent extras that [stringExtraLookup] reads, failing when they name no task
 * or lack what the task needs.
 */
fun hostTaskFrom(stringExtraLookup: (String) -> String?): HostTask {
    return when (keyWithValue(stringExtraLookup(EXTRA_TASK))) {
        HostTask.Key.RUN_PERSISTENCE_SCENARIO ->
            HostTask.RunPersistenceScenario(PersistenceScenarios.byId(stringExtraLookup.required(EXTRA_SCENARIO_ID)))

        HostTask.Key.RECORD_FIXTURE ->
            HostTask.RecordFixture(ResurrectionScenarios.byId(stringExtraLookup.required(EXTRA_SCENARIO_ID)))

        HostTask.Key.RESTORE_FIXTURE ->
            HostTask.RestoreFixture

        HostTask.Key.AWAIT_RESURRECTION ->
            HostTask.AwaitResurrection
    }
}

private fun keyWithValue(value: String?): HostTask.Key =
    HostTask.Key.entries.firstOrNull { it.value == value } ?: error("No matching host task found for key $value")

private fun ((String) -> String?).required(name: String): String =
    checkNotNull(this(name)) { "Required extra not found: $name" }

private const val EXTRA_TASK = "host_task"
private const val EXTRA_SCENARIO_ID = "scenario_id"
