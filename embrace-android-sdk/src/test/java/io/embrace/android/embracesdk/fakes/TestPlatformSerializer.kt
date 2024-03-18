package io.embrace.android.embracesdk.fakes

import io.embrace.android.embracesdk.internal.serialization.EmbraceSerializer
import io.embrace.android.embracesdk.internal.serialization.PlatformSerializer

internal class TestPlatformSerializer(
    realSerializer: PlatformSerializer = EmbraceSerializer()
) : PlatformSerializer by realSerializer
