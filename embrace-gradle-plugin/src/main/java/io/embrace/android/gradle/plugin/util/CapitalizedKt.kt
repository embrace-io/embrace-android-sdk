package io.embrace.android.gradle.plugin.util

fun CharSequence.capitalizedString(): String = this.toString().replaceFirstChar { it.uppercase() }
