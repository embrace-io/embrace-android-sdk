package io.embrace.android.embracesdk.internal.state

/**
 * Every state's value at one instant, with [seq], the number of the last change before it. A snapshot never changes: a change of
 * any state replaces the whole of it, so everything read from one describes the same instant.
 */
class StateSnapshot internal constructor(

    /** The number of the last change this snapshot includes, or 0 before any state has a value. */
    val seq: Long,

    /** When the SDK recorded that change, by its own clock. */
    val recordedAtMs: Long,

    private val definitions: Array<StateDefinition>,
    private val values: Array<String>,
    private val system: BooleanArray,
) {

    @Volatile
    private var packedForm: String? = null

    @Volatile
    private var seqForm: String? = null

    /** How many states have a value. */
    val size: Int
        get() = definitions.size

    fun definition(index: Int): StateDefinition = definitions[index]

    fun name(index: Int): String = definitions[index].name

    fun value(index: Int): String = values[index]

    /** Whether the SDK generated the value at [index], rather than it being one the app or the system gave. */
    fun isSystem(index: Int): Boolean = system[index]

    /** The index of the state called [name], or -1 if no data source has declared it. */
    fun indexOf(name: String): Int {
        for (i in definitions.indices) {
            if (definitions[i].name == name) {
                return i
            }
        }
        return -1
    }

    /**
     * Every state's value in the packed form a log sent to Embrace carries. It is built on first use and kept, so that stamping a
     * log costs the same however many states there are.
     */
    fun packed(): String = packedForm ?: PackedStates.pack(this).also { packedForm = it }

    /**
     * [seq] as text, which is how Embrace's payload carries it. Built on first use and kept, as [packed] is.
     */
    fun seqText(): String = seqForm ?: seq.toString().also { seqForm = it }

    /** This snapshot with the state at [index] changed, as of the change numbered [seq]. */
    internal fun with(index: Int, value: String, system: Boolean, seq: Long, recordedAtMs: Long): StateSnapshot {
        val newValues = values.copyOf()
        val newSystem = this.system.copyOf()
        newValues[index] = value
        newSystem[index] = system
        return StateSnapshot(seq, recordedAtMs, definitions, newValues, newSystem)
    }

    /** This snapshot with a newly declared state added, as of the change numbered [seq] that gave it its first value. */
    internal fun plus(
        definition: StateDefinition,
        value: String,
        system: Boolean,
        seq: Long,
        recordedAtMs: Long,
    ): StateSnapshot =
        StateSnapshot(seq, recordedAtMs, definitions + definition, values + value, this.system + system)

    companion object {

        /** What holds before any state has a value. */
        val EMPTY: StateSnapshot = StateSnapshot(0L, Long.MIN_VALUE, emptyArray(), emptyArray(), BooleanArray(0))
    }
}
