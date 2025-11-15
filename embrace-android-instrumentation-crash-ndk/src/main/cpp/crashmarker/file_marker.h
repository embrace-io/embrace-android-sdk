//
// Created by Fredric Newberg on 6/2/23.
//

#ifndef EMBRACE_NATIVE_CRASHES_FILE_MARKER_H
#define EMBRACE_NATIVE_CRASHES_FILE_MARKER_H

#ifdef __cplusplus
extern "C" {
#endif

#include "../jnibridge/emb_ndk_manager.h"

// reserve "1" for JVM crashes
#define CRASH_MARKER_SOURCE_SIGNAL "2"
#define CRASH_MARKER_SOURCE_CPP_EXCEPTION "3"

void emb_write_crash_marker_file(emb_env *env, const char *source);

#ifdef __cplusplus
}
#endif

#endif //EMBRACE_NATIVE_CRASHES_FILE_MARKER_H
