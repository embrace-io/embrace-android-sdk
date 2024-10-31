package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.AppFramework.FLUTTER
import io.embrace.android.embracesdk.internal.payload.AppFramework.NATIVE
import io.embrace.android.embracesdk.internal.payload.AppFramework.REACT_NATIVE
import io.embrace.android.embracesdk.internal.payload.AppFramework.UNITY

@Suppress("DEPRECATION")
internal fun fromFramework(
    appFramework: io.embrace.android.embracesdk.AppFramework,
): AppFramework = when (appFramework) {
    io.embrace.android.embracesdk.AppFramework.NATIVE -> NATIVE
    io.embrace.android.embracesdk.AppFramework.REACT_NATIVE -> REACT_NATIVE
    io.embrace.android.embracesdk.AppFramework.UNITY -> UNITY
    io.embrace.android.embracesdk.AppFramework.FLUTTER -> FLUTTER
}
