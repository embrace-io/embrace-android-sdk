package io.embrace.android.embracesdk.fakes.injection

import io.embrace.android.embracesdk.fakes.FakeStorageService
import io.embrace.android.embracesdk.internal.injection.StorageModule
import io.embrace.android.embracesdk.internal.storage.StorageService

class FakeStorageModule(
    override val storageService: StorageService = FakeStorageService(),
) : StorageModule
