package io.embrace.android.gradle.plugin.buildreporter

import org.gradle.build.event.BuildEventsListenerRegistry
import javax.inject.Inject

/**
 * It serves as a provider to fetch a BuildEventsListenerRegistry.
 */
interface BuildEventsListenerRegistryProvider {

    /**
     * Gradle will automatically inject it. It is Needed to register for task events.
     * Please note that in order for gradle to inject it, this class needs to be created by Gradle.
     * That can be achieved by doing something like:
     * ObjectFactory.newInstance(BuildEventsListenerRegistryProvider::class.java).
     *
     * Basically, starting on gradle 6.1, BuildService feature is supported.
     * So what will end up happening is that Gradle will override this method and inject our listener registry.
     *
     * For gradle versions lower than 6.1, BuildService feature is not supported. So with this approach, we are
     * decoupling this method and letting someone else call us. Whoever calls this method needs to
     * make sure that gradle's runtime version is at least 6.1.
     */
    @Inject
    fun getBuildEventsListenerRegistry(): BuildEventsListenerRegistry
}
