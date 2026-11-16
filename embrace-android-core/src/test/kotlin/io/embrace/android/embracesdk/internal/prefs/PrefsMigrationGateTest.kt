package io.embrace.android.embracesdk.internal.prefs

import io.embrace.android.embracesdk.internal.store.PersistedSdkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

internal class PrefsMigrationGateTest {

    private val gate = PrefsMigrationGate()

    @Test
    fun `settling while decoding abandons the migration`() {
        assertEquals(PrefsMigrationPhase.ABANDONED, gate.settle())
        assertTrue(gate.writes.isOpen)
        assertFalse(gate.commit(MigratedInstallState(PersistedSdkState("device-id", 1L), emptyMap(), emptySet())) { true })
        assertNull(gate.migrated)
    }

    @Test
    fun `an adopted fresh install opens writes even though nothing was migrated`() {
        gate.commit(null) {
            gate.settle()
            false
        }
        assertTrue(gate.writes.isOpen)
    }
}
