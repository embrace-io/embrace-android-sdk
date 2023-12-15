package io.embrace.android.embracesdk.payload

import com.google.gson.annotations.SerializedName
import io.embrace.android.embracesdk.capture.crumbs.Breadcrumb
import java.util.Arrays

/**
 * Breadcrumb that represents the dispatched actions from your state managment.
 */
internal data class RnActionBreadcrumb(

    /**
     * The action name
     */
    @SerializedName("n") val name: String,

    /**
     * The timestamp at which the action started.
     */
    @SerializedName("st") private val startTime: Long,

    /**
     * The timestamp at which the action ended.
     */
    @SerializedName("en") val endTime: Long,

    /**
     * This object is for extra properties / data that was not cover
     * with the already defined properties
     */
    @SerializedName("p")
    val properties: Map<String?, Any?>?,

    /**
     * The timestamp at which the action ended.
     */
    @SerializedName("pz") val bytesSent: Int,

    /**
     * The output message SUCCESS | FAIL | INCOMPLETE
     */
    @SerializedName("o") val output: String
) : Breadcrumb {

    internal enum class RnOutputType {
        SUCCESS, FAIL, INCOMPLETE
    }

    override fun getStartTime(): Long = startTime

    companion object {

        fun getValidRnBreadcrumbOutputName(): String = Arrays.toString(RnOutputType.values())

        /**
         * Method that validate the output is valid
         */
        fun validateRnBreadcrumbOutputName(output: String): Boolean {
            for (rnOutput in RnOutputType.values()) {
                if (rnOutput.name.equals(output, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }
    }
}
