package io.embrace.android.gradle.config

enum class JdkEnv(val path: String) {
    JAVA_17(resolveJdkPath(17)),
    JAVA_21(resolveJdkPath(21))
}

private fun resolveJdkPath(version: Int): String {
    return System.getenv("JAVA_HOME_${version}_X64")
        ?: System.getenv("LOCAL_JAVA_${version}_PATH")
        ?: error(
            "No JDK path supplied for Java $version. Please check the " +
                "LOCAL_JAVA_${version}_PATH or JAVA_HOME_${version}_X64 envars are set."
        )
}
