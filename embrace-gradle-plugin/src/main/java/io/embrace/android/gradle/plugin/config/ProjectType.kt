package io.embrace.android.gradle.plugin.config

/*
*  TODO: React Native type is not used. Should we remove it?
*  Can we consolidate behavior.isReactNativeProject with this ProjectType? Should we access all project types through behavior?
*/
enum class ProjectType {
    NATIVE,
    REACT_NATIVE,
    UNITY,
    OTHER
}
