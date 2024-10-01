package io.embrace.android.embracesdk.internal.delivery.scheduling

class NoopSchedulingService : SchedulingService {
    override fun shutdown() {
    }

    override fun onPayloadIntake() {
    }
}
