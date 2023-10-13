package io.embrace.android.embracesdk;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * The public API that is used to start & end moments.
 */
interface MomentsApi {

    /**
     * Starts a 'moment'. Moments are used for encapsulating particular activities within
     * the app, such as a user adding an item to their shopping cart.
     * <p>
     * The length of time a moment takes to execute is recorded.
     *
     * @param name a name identifying the moment
     */
    void startMoment(@NonNull String name);

    /**
     * Starts a 'moment'. Moments are used for encapsulating particular activities within
     * the app, such as a user adding an item to their shopping cart.
     * <p>
     * The length of time a moment takes to execute is recorded.
     *
     * @param name       a name identifying the moment
     * @param identifier an identifier distinguishing between multiple moments with the same name
     */
    void startMoment(@NonNull String name, @Nullable String identifier);

    /**
     * Starts a 'moment'. Moments are used for encapsulating particular activities within
     * the app, such as a user adding an item to their shopping cart.
     * <p>
     * The length of time a moment takes to execute is recorded
     *
     * @param name       a name identifying the moment
     * @param identifier an identifier distinguishing between multiple moments with the same name
     * @param properties custom key-value pairs to provide with the moment
     */
    void startMoment(@NonNull String name,
                     @Nullable String identifier,
                     @Nullable Map<String, Object> properties);

    /**
     * Signals the end of a moment with the specified name.
     * <p>
     * The duration of the moment is computed.
     *
     * @param name the name of the moment to end
     */
    void endMoment(@NonNull String name);

    /**
     * Signals the end of a moment with the specified name.
     * <p>
     * The duration of the moment is computed.
     *
     * @param name       the name of the moment to end
     * @param identifier the identifier of the moment to end, distinguishing between moments with the same name
     */
    void endMoment(@NonNull String name, @Nullable String identifier);

    /**
     * Signals the end of a moment with the specified name.
     * <p>
     * The duration of the moment is computed.
     *
     * @param name       the name of the moment to end
     * @param properties custom key-value pairs to provide with the moment
     */
    void endMoment(@NonNull String name,
                   @Nullable Map<String, Object> properties);

    /**
     * Signals the end of a moment with the specified name.
     * <p>
     * The duration of the moment is computed.
     *
     * @param name       the name of the moment to end
     * @param identifier the identifier of the moment to end, distinguishing between moments with the same name
     * @param properties custom key-value pairs to provide with the moment
     */
    void endMoment(@NonNull String name,
                   @Nullable String identifier,
                   @Nullable Map<String, Object> properties);

    /**
     * Signals that the app has completed startup.
     */
    void endAppStartup();

    /**
     * Signals that the app has completed startup.
     *
     * @param properties properties to include as part of the startup moment
     */
    void endAppStartup(@NonNull Map<String, Object> properties);
}
