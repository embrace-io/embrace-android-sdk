package io.embrace.android.embracesdk.internal

import io.embrace.android.embracesdk.Embrace
import io.embrace.android.embracesdk.internal.payload.AppFramework
import io.embrace.android.embracesdk.internal.payload.AppFramework.FLUTTER
import io.embrace.android.embracesdk.internal.payload.AppFramework.NATIVE
import io.embrace.android.embracesdk.internal.payload.AppFramework.REACT_NATIVE
import io.embrace.android.embracesdk.internal.payload.AppFramework.UNITY

@Suppress("DEPRECATION")
internal fun fromFramework(appFramework: Embrace.AppFramework): AppFramework = when (appFramework) {
    Embrace.AppFramework.NATIVE -> NATIVE
    Embrace.AppFramework.REACT_NATIVE -> REACT_NATIVE
    Embrace.AppFramework.UNITY -> UNITY
    Embrace.AppFramework.FLUTTER -> FLUTTER
}
