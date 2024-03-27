//
// Created by Eric Lanz on 5/17/20.
//

#ifndef EMBRACE_NATIVE_CRASHES_FILE_WRITER_H
#define EMBRACE_NATIVE_CRASHES_FILE_WRITER_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdbool.h>
#include "../jnibridge/emb_ndk_manager.h"

bool emb_write_crash_to_file(emb_env *stack);
emb_crash *emb_read_crash_from_file(const char *path);
emb_error *emb_read_errors_from_file(const char *path);
char *emb_crash_to_json(emb_crash *crash);
char *emb_errors_to_json(emb_error *errors);

#ifdef __cplusplus
}
#endif

#endif //EMBRACE_NATIVE_CRASHES_FILE_WRITER_H
