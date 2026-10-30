package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.arch.store.DeliveredAeiStore

class FakeDeliveredAeiStore(
    var storedIds: Set<String>? = null,
) : DeliveredAeiStore {

    override fun ids(): Set<String> = storedIds ?: emptySet()

    override fun add(id: String) {
        storedIds = ids().plus(id)
    }

    override fun retainOnly(ids: Set<String>) {
        storedIds = this.ids().intersect(ids)
    }
}
