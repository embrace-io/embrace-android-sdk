package io.embrace.android.embracesdk.arch

/**
 * Holds the current state of the service. This class automatically handles changes in config
 * that enable/disable the service, and creates new instances of the service as required.
 * It also is capable of disabling the service if the [EnvelopeType] is not supported.
 */
internal class DataSourceState<T : DataSource<R>, R>(

    /**
     * Provides instances of services. A service must define an interface
     * that extends [DataSource] for orchestration. This helps enforce testability
     * by making it impossible to register data capture without defining a testable interface.
     */
    factory: () -> DataSource<R>,

    /**
     * Predicate that determines if the service should be enabled or not, via a config value.
     */
    private val configGate: () -> Boolean,

    /**
     * The type of envelope that contains the data.
     */
    private var currentEnvelope: EnvelopeType,

    /**
     * An envelope type where data capture should be disabled. For example,
     * background activities capture a subset of sessions.
     */
    private val disabledEnvelopeType: EnvelopeType? = null
) {

    private val enabledDataSource by lazy(factory)
    private var dataSource: DataSource<R>? = null

    init {
        updateDataSource()
    }

    /**
     * Callback that is invoked when the envelope type changes.
     */
    fun onEnvelopeTypeChange(envelopeType: EnvelopeType) {
        this.currentEnvelope = envelopeType
        updateDataSource()
    }

    /**
     * Callback that is invoked when the config layer experiences a change.
     */
    fun onConfigChange() {
        updateDataSource()
    }

    private fun updateDataSource() {
        val enabled = currentEnvelope != disabledEnvelopeType && configGate()

        if (enabled && dataSource == null) {
            dataSource = enabledDataSource.apply {
                registerListeners()
            }
        } else if (!enabled && dataSource != null) {
            dataSource?.unregisterListeners()
            dataSource = null
        }
    }
}
