package io.embrace.android.embracesdk.ndk;

import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import io.embrace.android.embracesdk.Embrace;
import io.embrace.android.embracesdk.EmbraceEvent;
import io.embrace.android.embracesdk.capture.metadata.MetadataService;
import io.embrace.android.embracesdk.capture.user.UserService;
import io.embrace.android.embracesdk.comms.api.ApiClient;
import io.embrace.android.embracesdk.comms.delivery.DeliveryService;
import io.embrace.android.embracesdk.config.ConfigService;
import io.embrace.android.embracesdk.internal.ApkToolsConfig;
import io.embrace.android.embracesdk.internal.DeviceArchitecture;
import io.embrace.android.embracesdk.internal.EmbraceSerializer;
import io.embrace.android.embracesdk.internal.SharedObjectLoader;
import io.embrace.android.embracesdk.internal.crash.CrashFileMarker;
import io.embrace.android.embracesdk.internal.utils.Uuid;
import io.embrace.android.embracesdk.logging.InternalEmbraceLogger;
import io.embrace.android.embracesdk.payload.Event;
import io.embrace.android.embracesdk.payload.EventMessage;
import io.embrace.android.embracesdk.payload.NativeCrashData;
import io.embrace.android.embracesdk.payload.NativeCrashDataError;
import io.embrace.android.embracesdk.payload.NativeCrashMetadata;
import io.embrace.android.embracesdk.payload.NativeSymbols;
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateListener;
import io.embrace.android.embracesdk.session.lifecycle.ProcessStateService;
import io.embrace.android.embracesdk.session.properties.EmbraceSessionProperties;
import io.embrace.android.embracesdk.session.SessionService;
import kotlin.Lazy;
import kotlin.LazyKt;

class EmbraceNdkService implements NdkService, ProcessStateListener {

    /**
     * Signals to the API that the application was in the foreground.
     */
    private static final String APPLICATION_STATE_ACTIVE = "active";
    /**
     * Signals to the API that the application was in the background.
     */
    private static final String APPLICATION_STATE_BACKGROUND = "background";
    /**
     * The NDK symbols name that matches with the resource name injected by the plugin.
     */
    private static final String KEY_NDK_SYMBOLS = "emb_ndk_symbols";

    private static final String CRASH_REPORT_EVENT_NAME = "_crash_report";

    private static final String NATIVE_CRASH_FILE_PREFIX = "emb_ndk";

    private static final String NATIVE_CRASH_FILE_SUFFIX = ".crash";

    private static final String NATIVE_CRASH_ERROR_FILE_SUFFIX = ".error";

    private static final String NATIVE_CRASH_MAP_FILE_SUFFIX = ".map";

    private static final String NATIVE_CRASH_FILE_FOLDER = "ndk";

    private static final int MAX_NATIVE_CRASH_FILES_ALLOWED = 4;

    private static final int EMB_DEVICE_META_DATA_SIZE = 2048;

    private static final int HANDLER_CHECK_DELAY_MS = 5000;

    /**
     * Synchronization lock.
     */
    private final Object lock = new Object();
    /**
     * The device architecture.
     */
    private final DeviceArchitecture deviceArchitecture;
    /**
     * Whether or not the NDK has been installed.
     */
    private boolean isInstalled = false;

    private final Context context;

    private final MetadataService metadataService;

    private final ConfigService configService;

    private final DeliveryService deliveryService;

    private final UserService userService;

    private final EmbraceSessionProperties sessionProperties;

    private final EmbraceSerializer serializer;

    private String unityCrashId;

    private final Lazy<File> storageDir;

    private final ExecutorService cleanCacheExecutorService;
    private final ExecutorService ndkStartupExecutorService;

    private final SharedObjectLoader sharedObjectLoader;
    private final InternalEmbraceLogger logger;
    private final kotlin.Lazy<Map<String, String>> symbolsForArch;

    private final EmbraceNdkServiceRepository repository;
    private final NdkServiceDelegate.NdkDelegate delegate;

    EmbraceNdkService(
        @NonNull Context context,
        @NonNull Lazy<File> storageDir,
        @NonNull MetadataService metadataService,
        @NonNull ProcessStateService processStateService,
        @NonNull ConfigService configService,
        @NonNull DeliveryService deliveryService,
        @NonNull UserService userService,
        @NonNull EmbraceSessionProperties sessionProperties,
        @NonNull Embrace.AppFramework appFramework,
        @NonNull SharedObjectLoader sharedObjectLoader,
        @NonNull InternalEmbraceLogger logger,
        @NonNull EmbraceNdkServiceRepository repository,
        @NonNull NdkServiceDelegate.NdkDelegate delegate,
        @NonNull ExecutorService cleanCacheExecutorService,
        @NonNull ExecutorService ndkStartupExecutorService,
        @NonNull DeviceArchitecture deviceArchitecture,
        @NonNull EmbraceSerializer serializer) {

        this.context = context;
        this.storageDir = storageDir;
        this.metadataService = metadataService;
        this.configService = configService;
        this.deliveryService = deliveryService;
        this.userService = userService;
        this.sessionProperties = sessionProperties;
        this.sharedObjectLoader = sharedObjectLoader;
        this.logger = logger;
        this.repository = repository;
        this.delegate = delegate;
        this.deviceArchitecture = deviceArchitecture;

        this.symbolsForArch = LazyKt.lazy(() -> {
            NativeSymbols nativeSymbols = getNativeSymbols();
            if (nativeSymbols != null) {
                String arch = deviceArchitecture.getArchitecture();
                return nativeSymbols.getSymbolByArchitecture(arch);
            }
            return null;
        });

        this.cleanCacheExecutorService = cleanCacheExecutorService;
        this.ndkStartupExecutorService = ndkStartupExecutorService;
        this.serializer = serializer;

        if (configService.getAutoDataCaptureBehavior().isNdkEnabled()) {
            processStateService.addListener(this);

            if (appFramework == Embrace.AppFramework.UNITY) {
                this.unityCrashId = Uuid.getEmbUuid();
            }

            logger.logDeveloper("EmbraceNDKService", "NDK enabled - starting service installation.");
            startNdk();
            cleanOldCrashFiles();
        } else {
            logger.logDeveloper("EmbraceNDKService", "NDK disabled.");
        }
    }

    @Override
    public void testCrash(boolean isCpp) {
        if (isCpp) {
            testCrashCpp();
        } else {
            testCrashC();
        }
    }

    @Override
    public void updateSessionId(@NonNull String newSessionId) {
        logger.logDeveloper("EmbraceNDKService", "NDK update (session ID): " + newSessionId);

        if (isInstalled) {
            delegate._updateSessionId(newSessionId);
        }
    }

    @Override
    public void onSessionPropertiesUpdate(@NonNull Map<String, String> properties) {
        logger.logDeveloper("EmbraceNDKService", "NDK update: (session properties): " + properties);

        if (isInstalled) {
            updateDeviceMetaData();
        }
    }

    @Override
    public void onUserInfoUpdate() {
        logger.logDeveloper("EmbraceNDKService", "NDK update (user)");

        if (isInstalled) {
            updateDeviceMetaData();
        }
    }

    @Override
    @Nullable
    public String getUnityCrashId() {
        return this.unityCrashId;
    }

    @Override
    public void onBackground(long timestamp) {
        synchronized (lock) {
            if (isInstalled) {
                updateAppState(APPLICATION_STATE_BACKGROUND);
            }
        }
    }

    @Override
    public void onForeground(boolean coldStart, long startupTime, long timestamp) {
        synchronized (lock) {
            if (isInstalled) {
                updateAppState(APPLICATION_STATE_ACTIVE);
            }
        }
    }

    private void startNdk() {
        try {
            if (sharedObjectLoader.loadEmbraceNative()) {
                installSignals();
                createCrashReportDirectory();
                Handler handler = new Handler(Looper.myLooper());
                handler.postDelayed(this::checkSignalHandlersOverwritten, HANDLER_CHECK_DELAY_MS);
                logger.logInfo("NDK library successfully loaded");
            } else {
                logger.logDeveloper("EmbraceNDKService", "Failed to load embrace library - probable unsatisfied linkage.");
            }
        } catch (Exception ex) {
            logger.logError("Failed to load NDK library", ex);
        }
    }

    
    void checkSignalHandlersOverwritten() {
        if (configService.getAutoDataCaptureBehavior().isSigHandlerDetectionEnabled()) {
            String culprit = delegate._checkForOverwrittenHandlers();

            if (culprit != null) {
                if (shouldIgnoreOverriddenHandler(culprit)) {
                    return;
                }
                String errMsg = "Embrace detected that another signal handler has replaced our signal handler.\n" +
                    "This may lead to unexpected behaviour and lost NDK crashes.\n" +
                    "We will attempt to reinstall our signal handler but please consider disabling\n" +
                    "other signal handlers if you observed unexpected behaviour.\n" +
                    "If you believe this is a false positive, please contact support@embrace.io.\n" +
                    "Handler origin: " + culprit;
                RuntimeException exc = new RuntimeException(errMsg);
                exc.setStackTrace(new StackTraceElement[0]);
                logger.logWarningWithException(errMsg, exc, false);
                delegate._reinstallSignalHandlers();
            }
        }
    }

    /**
     * Contains a list of SO files which are known to install signal handlers that do not
     * interfere with crash detection. This list will probably expand over time.
     *
     * @param culprit the culprit SO file as identified by dladdr
     * @return true if we can safely ignore
     */
    private boolean shouldIgnoreOverriddenHandler(@NonNull String culprit) {
        List<String> allowList = Collections.singletonList("libwebviewchromium.so");
        for (String allowed : allowList) {
            if (culprit.contains(allowed)) {
                return true;
            }
        }
        return false;
    }

    protected void createCrashReportDirectory() {
        String directory = storageDir.getValue() + "/" + NATIVE_CRASH_FILE_FOLDER;
        File directoryFile = new File(directory);

        if (directoryFile.exists()) {
            return;
        }

        if (!directoryFile.mkdirs()) {
            logger.logError("Failed to create crash report directory {crashDirPath=" + directoryFile.getAbsolutePath() + "}");
        }
    }

    protected void installSignals() {
        String reportBasePath = storageDir.getValue().getAbsolutePath() + "/" + NATIVE_CRASH_FILE_FOLDER;
        String markerFilePath = storageDir.getValue().getAbsolutePath() + "/" + CrashFileMarker.CRASH_MARKER_FILE_NAME;
        logger.logDeveloper("EmbraceNDKService", "Creating report path at " + reportBasePath);

        String nativeCrashId;
        // Assign the native crash id to the unity crash id. Then when a unity crash occurs, the
        // Embrace crash service will set the unity crash id to the java crash.
        if (this.unityCrashId != null) {
            nativeCrashId = this.unityCrashId;
        } else {
            nativeCrashId = Uuid.getEmbUuid();
        }

        boolean is32bit = deviceArchitecture.is32BitDevice();
        logger.logDeveloper("EmbraceNDKService", "Installing signal handlers. 32bit=" + is32bit + ", crashId=" + nativeCrashId);

        String initialMetaData = new NativeCrashMetadata(
            this.metadataService.getLightweightAppInfo(),
            this.metadataService.getLightweightDeviceInfo(),
            this.userService.getUserInfo(),
            this.sessionProperties.get()).toJson();

        delegate._installSignalHandlers(
            reportBasePath,
            markerFilePath,
            initialMetaData,
            "null",
            this.metadataService.getAppState(),
            nativeCrashId,
            Build.VERSION.SDK_INT,
            is32bit,
            ApkToolsConfig.IS_DEVELOPER_LOGGING_ENABLED);

        updateDeviceMetaData();

        isInstalled = true;
    }

    /**
     * Find and parse a native error File to NativeCrashData Error List
     *
     * @return List of NativeCrashData error
     */
    protected List<NativeCrashDataError> getNativeCrashErrors(NativeCrashData nativeCrash, File errorFile) {
        if (errorFile != null) {
            String absolutePath = errorFile.getAbsolutePath();
            logger.logDeveloper("EmbraceNDKService", "Processing error file at " + absolutePath);

            String errorsRaw = delegate._getErrors(absolutePath);
            if (errorsRaw != null) {
                try {
                    return serializer.<ArrayList<NativeCrashDataError>>fromJson(errorsRaw);
                } catch (Exception e) {
                    logger.logError("Failed to parse native crash error file {crashId=" + nativeCrash.getNativeCrashId() +
                        ", errorFilePath=" + absolutePath + "}");
                }
            } else {
                logger.logDeveloper("EmbraceNDKService", "Failed to load errorsRaw.");
            }
        } else {
            logger.logDeveloper("EmbraceNDKService", "Failed to find error file for crash.");
        }

        return null;
    }

    /**
     * Process map file for crash to read and return its content as String
     */
    private String getMapFileContent(File mapFile) {
        if (mapFile != null) {
            logger.logDeveloper("EmbraceNDKService", "Processing map file at " + mapFile.getAbsolutePath());

            String mapContents = readMapFile(mapFile);
            if (mapContents != null) {
                return mapContents;
            } else {
                logger.logDeveloper("EmbraceNDKService", "Failed to load mapContents.");
            }
        } else {
            logger.logDeveloper("EmbraceNDKService", "Failed to find map file for crash.");
        }

        return null;
    }

    /**
     * Check if a native crash file exists. Also checks for the symbols file in the build dir.
     * If so, attempt to send an event message and call {@link SessionService} to update the crash
     * report id in the appropriate pending session.
     *
     * @return Crash data, if a native crash file was found
     */
    @Nullable
    @Override
    public NativeCrashData checkForNativeCrash() {
        logger.logDeveloper("EmbraceNDKService", "Processing native crash check runnable.");

        NativeCrashData nativeCrash = null;
        List<File> matchingFiles = repository.sortNativeCrashes(false);
        logger.logDeveloper("EmbraceNDKService", "Found " + matchingFiles.size() + " native crashes.");

        for (File crashFile : matchingFiles) {
            try {
                String path = crashFile.getPath();
                String crashRaw = delegate._getCrashReport(path);
                logger.logDeveloper("EmbraceNDKService", "Processing native crash at " + path);

                if (crashRaw != null) {
                    nativeCrash = serializer.fromJson(crashRaw, NativeCrashData.class);

                    if (nativeCrash == null) {
                        logger.logError("Failed to deserialize native crash error file: " + crashFile.getAbsolutePath());
                    }
                } else {
                    logger.logError("Failed to load crash report at " + path);
                }

                File errorFile = repository.errorFileForCrash(crashFile);
                if (nativeCrash != null) {
                    List<NativeCrashDataError> errors = getNativeCrashErrors(nativeCrash, errorFile);
                    if (errors != null) {
                        nativeCrash.setErrors(errors);
                    } else {
                        logger.logDeveloper("EmbraceNDKService", "Failed to find error file for crash.");
                    }
                } else {
                    logger.logDeveloper("EmbraceNDKService", "Failed to find error file for crash.");
                }

                File mapFile = repository.mapFileForCrash(crashFile);
                if (mapFile != null && nativeCrash != null) {
                    nativeCrash.setMap(getMapFileContent(mapFile));
                } else {
                    logger.logDeveloper("EmbraceNDKService", "Failed to find map file for crash.");
                }

                // Retrieve deobfuscated symbols
                if (nativeCrash != null) {
                    final Map<String, String> symbols = getSymbolsForCurrentArch();
                    if (symbols == null) {
                        logger.logError("Failed to find symbols for native crash - stacktraces will not symbolicate correctly.");
                    } else {
                        nativeCrash.setSymbols(symbols);
                        logger.logDeveloper("EmbraceNDKService", "Added symbols for native crash");
                    }
                    sendNativeCrash(nativeCrash);
                }

                repository.deleteFiles(crashFile, errorFile, mapFile, nativeCrash);
            } catch (Exception ex) {
                //noinspection ResultOfMethodCallIgnored
                crashFile.delete();
                logger.logError("Failed to read native crash file {crashFilePath=" + crashFile.getAbsolutePath() + "}.", ex, true);
            }
        }

        return nativeCrash;
    }

    @Override
    @Nullable
    public Map<String, String> getSymbolsForCurrentArch() {
        return symbolsForArch.getValue();
    }

    @SuppressWarnings("DiscouragedApi")
    private NativeSymbols getNativeSymbols() {
        Resources resources = context.getResources();
        int resourceId = resources.getIdentifier(KEY_NDK_SYMBOLS, "string", context.getPackageName());

        if (resourceId != 0) {
            try {
                String encodedSymbols = new String(Base64.decode(context.getResources().getString(resourceId), Base64.DEFAULT));
                return serializer.fromJson(encodedSymbols, NativeSymbols.class);
            } catch (Exception ex) {
                logger.logError(String.format(Locale.getDefault(), "Failed to decode symbols from resources {resourceId=%d}.",
                        resourceId),
                    ex);
            }
        } else {
            logger.logError(String.format(Locale.getDefault(), "Failed to find symbols in resources {resourceId=%d}.",
                resourceId)
            );
        }

        return null;
    }

    private File[] getNativeFiles(FilenameFilter filter) {
        File[] matchingFiles = null;
        final File[] files = storageDir.getValue().listFiles();

        if (files == null) {
            return null;
        }

        for (File cached : files) {
            if (cached.isDirectory() && cached.getName().equals(NATIVE_CRASH_FILE_FOLDER)) {
                matchingFiles = cached.listFiles(filter);
                break;
            }
        }

        return matchingFiles;
    }

    private File[] getNativeErrorFiles() {
        FilenameFilter nativeCrashFilter = (f, name) -> name.startsWith(NATIVE_CRASH_FILE_PREFIX) && name.endsWith(NATIVE_CRASH_ERROR_FILE_SUFFIX);
        return getNativeFiles(nativeCrashFilter);
    }

    private File[] getNativeMapFiles() {
        FilenameFilter nativeCrashFilter = (f, name) -> name.startsWith(NATIVE_CRASH_FILE_PREFIX) && name.endsWith(NATIVE_CRASH_MAP_FILE_SUFFIX);
        return getNativeFiles(nativeCrashFilter);
    }

    @Nullable
    private String readMapFile(File mapFile) {
        try (FileInputStream fin = new FileInputStream(mapFile);
             BufferedReader reader = new BufferedReader(new InputStreamReader(fin))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }

    private void cleanOldCrashFiles() {
        cleanCacheExecutorService.submit(() -> {
            logger.logDeveloper("EmbraceNDKService", "Processing clean of old crash files.");

            List<File> sortedFiles = repository.sortNativeCrashes(true);

            int deleteCount = sortedFiles.size() - MAX_NATIVE_CRASH_FILES_ALLOWED;

            if (deleteCount > 0) {
                LinkedList<File> files = new LinkedList<>(sortedFiles);

                try {
                    for (int i = 0; i < deleteCount; i++) {
                        File removed = files.get(i);
                        if (files.get(i).delete()) {
                            logger.logDebug("Native crash file " + removed.getName() + " removed from cache");
                        }
                    }
                } catch (Exception ex) {
                    logger.logError("Failed to delete native crash from cache.", ex);
                }
            }

            // delete error files that don't have matching crash files
            File[] errorFiles = getNativeErrorFiles();
            if (errorFiles != null) {
                for (File errorFile : errorFiles) {
                    if (hasNativeCrashFile(errorFile)) {
                        logger.logDeveloper("EmbraceNDKService",
                            "Skipping error file as it has a matching crash file " + errorFile.getAbsolutePath());
                        continue;
                    }
                    errorFile.delete();
                    logger.logDeveloper("EmbraceNDKService",
                        "Deleting error file as it has no matching crash file " + errorFile.getAbsolutePath());
                }
            }

            // delete map files that don't have matching crash files
            File[] mapFiles = getNativeMapFiles();
            if (mapFiles != null) {
                for (File mapFile : mapFiles) {
                    if (hasNativeCrashFile(mapFile)) {
                        logger.logDeveloper("EmbraceNDKService",
                            "Skipping map file as it has a matching crash file " + mapFile.getAbsolutePath());
                        continue;
                    }
                    mapFile.delete();
                    logger.logDeveloper("EmbraceNDKService",
                        "Deleting map file as it has no matching crash file " + mapFile.getAbsolutePath());
                }
            }

            return null;
        });
    }

    private boolean hasNativeCrashFile(File file) {
        String filename = file.getAbsolutePath();
        if (!filename.contains(".")) {
            return false;
        }
        String crashFilename = filename.substring(0, filename.lastIndexOf('.')) + NATIVE_CRASH_FILE_SUFFIX;
        File crashFile = new File(crashFilename);
        return crashFile.exists();
    }

    private void sendNativeCrash(NativeCrashData nativeCrash) {
        logger.logDeveloper("EmbraceNDKService", "Constructing EventMessage from native crash.");

        NativeCrashMetadata metadata = nativeCrash.getMetadata();
        Event nativeCrashEvent = new Event(
            CRASH_REPORT_EVENT_NAME,
            null,
            Uuid.getEmbUuid(),
            nativeCrash.getSessionId(),
            EmbraceEvent.Type.CRASH,
            nativeCrash.getTimestamp(),
            null,
            false,
            null,
            nativeCrash.getAppState(),
            null,
            metadata != null ? metadata.getSessionProperties() : null,
            null,
            null,
            null,
            null,
            null
        );

        EventMessage nativeCrashMessageEvent = new EventMessage(
            nativeCrashEvent,
            null,
            metadata != null ? metadata.getDeviceInfo() : null,
            metadata != null ? metadata.getAppInfo() : null,
            metadata != null ? metadata.getUserInfo() : null,
            null,
            null,
            ApiClient.MESSAGE_VERSION,
            nativeCrash.getCrash());

        try {
            logger.logDeveloper("EmbraceNDKService", "About to send EventMessage from native crash.");
            deliveryService.sendEventAndWait(nativeCrashMessageEvent);
            logger.logDeveloper("EmbraceNDKService", "Finished send attempt for EventMessage from native crash.");
        } catch (Exception ex) {
            logger.logError("Failed to report native crash to the api {sessionId=" + nativeCrash.getSessionId() +
                    ", crashId=" + nativeCrash.getNativeCrashId(),
                ex);
        }
    }

    private void updateAppState(String newAppState) {
        logger.logDeveloper("EmbraceNDKService", "NDK update (app state): " + newAppState);
        delegate._updateAppState(newAppState);
    }

    /**
     * Compute NDK metadata on a background thread.
     */
    private void updateDeviceMetaData() {
        ndkStartupExecutorService.submit(() -> {
            logger.logDeveloper("EmbraceNDKService", "Processing NDK metadata update on bg thread.");

            String newDeviceMetaData = getMetaData(true);
            logger.logDeveloper("EmbraceNDKService", "NDK update (metadata): " + newDeviceMetaData);

            if (newDeviceMetaData.length() >= EMB_DEVICE_META_DATA_SIZE) {
                logger.logDebug("Removing session properties from metadata to avoid exceeding size limitation for NDK metadata.");
                newDeviceMetaData = getMetaData(false);
            }

            delegate._updateMetaData(newDeviceMetaData);

            return null;
        });
    }

    private String getMetaData(Boolean includeSessionProperties) {
        return new NativeCrashMetadata(
            this.metadataService.getAppInfo(),
            this.metadataService.getDeviceInfo(),
            this.userService.getUserInfo(),
            includeSessionProperties ? this.sessionProperties.get() : null).toJson();
    }

    private void uninstallSignals() {
        delegate._uninstallSignals();
    }

    private void testCrashC() {
        delegate._testNativeCrash_C();
    }

    private void testCrashCpp() {
        delegate._testNativeCrash_CPP();
    }
}