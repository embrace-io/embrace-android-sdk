#ifndef EMBRACE_LOG_H
#define EMBRACE_LOG_H

#include <android/log.h>

#define EMB_LOG_TAG "emb_ndk"
#define EMB_DEV_LOG_TAG "emb_ndk_dev"

void emb_enable_dev_logging();
bool emb_dev_logging_enabled();

#ifndef EMB_LOGERROR
#define EMB_LOGERROR(fmt, ...)                                                  \
    __android_log_print(ANDROID_LOG_ERROR, EMB_LOG_TAG, fmt, ##__VA_ARGS__)
#endif

#ifndef EMB_LOGWARN
#define EMB_LOGWARN(fmt, ...)                                                  \
    __android_log_print(ANDROID_LOG_WARN, EMB_LOG_TAG, fmt, ##__VA_ARGS__)
#endif

#ifndef EMB_LOGINFO
#define EMB_LOGINFO(fmt, ...)                                                  \
    __android_log_print(ANDROID_LOG_INFO, EMB_LOG_TAG, fmt, ##__VA_ARGS__)
#endif

#ifndef EMB_LOGDEV
#define EMB_LOGDEV(fmt, ...) \
    do {                       \
        if (emb_dev_logging_enabled()) {   \
            __android_log_print(ANDROID_LOG_ERROR, EMB_DEV_LOG_TAG, fmt, ##__VA_ARGS__); \
        } \
    } while (0)
#endif

#endif //EMBRACE_LOG_H
