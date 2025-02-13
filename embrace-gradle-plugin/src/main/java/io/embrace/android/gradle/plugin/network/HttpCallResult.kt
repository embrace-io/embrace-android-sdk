package io.embrace.android.gradle.plugin.network

sealed class HttpCallResult {
    data class Success<T>(val body: T?, val code: Int) : HttpCallResult()
    data class Failure(val errorMessage: String?, val code: Int) : HttpCallResult()
    data class Error(val exception: Exception) : HttpCallResult()
}
