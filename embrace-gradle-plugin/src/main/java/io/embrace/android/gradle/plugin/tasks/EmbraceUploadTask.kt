package io.embrace.android.gradle.plugin.tasks

import io.embrace.android.gradle.plugin.tasks.common.RequestParams
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input

/**
 * Marker interface that contains the bare minimum properties required for tasks used in the
 * Embrace gradle plugin.
 */
interface EmbraceUploadTask : EmbraceTask {

    @get:Input
    val requestParams: Property<RequestParams>
}
