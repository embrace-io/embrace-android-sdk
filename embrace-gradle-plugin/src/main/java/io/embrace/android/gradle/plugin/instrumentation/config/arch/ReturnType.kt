package io.embrace.android.gradle.plugin.instrumentation.config.arch

/**
 * The return type of a config method.
 */
enum class ReturnType(val descriptor: String) {
    BOOLEAN("()Z"),
    INT("()I"),
    LONG("()J"),
    STRING("()Ljava/lang/String;"),
    STRING_LIST("()Ljava/util/List;"),
    MAP("()Ljava/util/Map;"),
}
