package io.embrace.android.embracesdk.fakes

import androidx.compose.runtime.snapshots.SnapshotStateList

interface HasBackStack {
    fun getBackStack(): SnapshotStateList<Any>
}
