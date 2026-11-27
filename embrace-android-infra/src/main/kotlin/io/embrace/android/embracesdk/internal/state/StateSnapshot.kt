package io.embrace.android.embracesdk.internal.state

/**
 * Every state's value at one point in the order of changes: the set of values that was in force once the change
 * numbered [seq] had been made. [seq] is 0 before the first change of any state.
 *
 * A snapshot is immutable and replaced as a whole on each change, so whatever reads one reads values of one moment.
 * The arrays are indexed by the order in which the states were defined, and must not be modified.
 */
class StateSnapshot(
    val seq: Long,
    val recordedAtMs: Long,
    private val names: Array<String>,
    private val values: Array<String>,
    private val system: BooleanArray,
) {

    val size: Int
        get() = names.size

    fun name(index: Int): String = names[index]

    fun value(index: Int): String = values[index]

    fun isSystem(index: Int): Boolean = system[index]

    fun indexOf(name: String): Int = names.indexOf(name)

    /**
     * Every value packed into one string, as Embrace's own payload carries it on a log. Built once, the first time
     * it is read, so a change that no log reads costs nothing here.
     */
    val packed: String by lazy(LazyThreadSafetyMode.PUBLICATION) { StatePacking.pack(this) }

    /**
     * A copy of this snapshot with one state's value replaced, or added if the state is new.
     */
    fun with(seq: Long, recordedAtMs: Long, name: String, value: String, isSystem: Boolean): StateSnapshot {
        val index = indexOf(name)
        return if (index >= 0) {
            val newValues = values.copyOf()
            val newSystem = system.copyOf()
            newValues[index] = value
            newSystem[index] = isSystem
            StateSnapshot(seq, recordedAtMs, names, newValues, newSystem)
        } else {
            StateSnapshot(
                seq = seq,
                recordedAtMs = recordedAtMs,
                names = names + name,
                values = values + value,
                system = system + isSystem,
            )
        }
    }

    companion object {
        val EMPTY: StateSnapshot = StateSnapshot(0, 0, emptyArray(), emptyArray(), BooleanArray(0))
    }
}
