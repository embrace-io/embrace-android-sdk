package io.embrace.android.embracesdk.internal.delivery.intake

/**
 * Classifies failures of [IntakeService] based on whether it could succeed in the future if retried.
 */
enum class IntakeFailure {

    /**
     * Intake failed due to an intermittent cause that may not exist if the attempt was retried in
     * the future.
     */
    RECOVERABLE,

    /**
     * Intake failed because of a cause endemic to the intake data, and no amount of retrying will
     * lead to intake succeeding.
     */
    UNRECOVERABLE,
}
