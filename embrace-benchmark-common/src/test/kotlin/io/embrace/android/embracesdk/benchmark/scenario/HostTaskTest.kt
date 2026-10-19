package io.embrace.android.embracesdk.benchmark.scenario

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

internal class HostTaskTest {

    private val tasks = listOf(
        HostTask.RunPersistenceScenario(PersistenceScenarios.all.first()),
        HostTask.RecordFixture(ResurrectionScenarios.largePart),
        HostTask.RestoreFixture,
        HostTask.AwaitResurrection,
    )

    @Test
    fun `every task reads back from its own extras`() {
        tasks.forEach { task -> assertEquals(task, hostTaskFrom(task.toExtras()::get)) }
    }

    @Test
    fun `every key belongs to exactly one task`() {
        assertEquals(HostTask.Key.entries.sorted(), tasks.map(HostTask::key).sorted())
    }

    @Test
    fun `the keys have distinct values`() {
        assertEquals(HostTask.Key.entries.size, HostTask.Key.entries.map(HostTask.Key::value).toSet().size)
    }

    @Test
    fun `extras naming no task fail`() {
        assertThrows(IllegalStateException::class.java) { hostTaskFrom(emptyMap<String, String>()::get) }
    }

    @Test
    fun `an unknown task key fails`() {
        tasks.forEach { task ->
            val extras = task.toExtras().mapValues { (_, value) ->
                if (value == task.key.value) {
                    "NoSuchTask"
                } else {
                    value
                }
            }

            assertThrows(IllegalStateException::class.java) { hostTaskFrom(extras::get) }
        }
    }

    @Test
    fun `a task missing any of its arguments fails`() {
        tasks.forEach { task ->
            task.arguments.keys.forEach { missing ->
                val extras = task.toExtras() - missing

                assertThrows(IllegalStateException::class.java) { hostTaskFrom(extras::get) }
            }
        }
    }
}
