//
// Created by Eric Lanz on 5/12/20.
//

#ifndef EMBRACE_NATIVE_CRASHES_STACK_FRAMES_H
#define EMBRACE_NATIVE_CRASHES_STACK_FRAMES_H

#include <sys/types.h>
#include <stdbool.h>

#ifndef kEMBSamplePathMaxLen
#define kEMBSamplePathMaxLen 256
#endif

#ifndef kEMBSampleAddrLen
#define kEMBSampleAddrLen 32
#endif

#ifndef kEMBMaxSFrames
#define kEMBMaxSFrames 100
#endif

#ifndef kEMBSampleUnwindLimit
/**
 * The number of frames that can be unwound in a sample.
 */
#define kEMBSampleUnwindLimit 256
#endif

#ifndef kEMBMaxSampleSFrames
/**
 * The number of frames that can be serialized in a sample.
 */
#define kEMBMaxSampleSFrames 100
#endif

#ifndef kEMBMaxSamples
#define kEMBMaxSamples 10
#endif

#ifndef kEMBMaxExceptionNameSize
#define kEMBMaxExceptionNameSize 64
#endif

#ifndef kEMBMaxExceptionMessageSize
#define kEMBMaxExceptionMessageSize 256
#endif

#ifndef EMB_APP_DATA_SIZE
#define EMB_APP_DATA_SIZE 128
#endif

#ifndef EMB_DEVICE_META_DATA_SIZE
#define EMB_DEVICE_META_DATA_SIZE 2048
#endif

#ifndef EMB_REPORT_ID_SIZE
#define EMB_REPORT_ID_SIZE 256
#endif

#ifndef EMB_SESSION_ID_SIZE
#define EMB_SESSION_ID_SIZE 256
#endif

#ifndef EMB_FRAME_STR_SIZE
#define EMB_FRAME_STR_SIZE 512
#endif

#ifndef EMB_PATH_SIZE
#define EMB_PATH_SIZE 512
#endif

typedef struct {
    char filename[256];
    char method[256];
    char build_id[EMB_FRAME_STR_SIZE];

    uintptr_t frame_addr;
    uintptr_t offset_addr;
    uintptr_t module_addr;
    uintptr_t line_num;
    uint64_t rel_pc;
    uint64_t pc;
    uint64_t sp;
    uint64_t lr; // only populated for the first frame.
    uint64_t function_offset;
    char function_name[EMB_FRAME_STR_SIZE];

    bool elf_file_not_readable;
    uint64_t start;
    uint64_t end;
    uint64_t offset;
    uint16_t flags;
    char full_name[EMB_FRAME_STR_SIZE];
} emb_sframe;

typedef struct {
    char name[kEMBMaxExceptionNameSize];
    char message[kEMBMaxExceptionMessageSize];

    ssize_t num_sframes;
    emb_sframe stacktrace[kEMBMaxSFrames];
} emb_exception;

typedef struct {
    emb_exception capture;
    bool unhandled;
    int unhandled_count;
    char session_id[EMB_SESSION_ID_SIZE];
    char report_id[EMB_REPORT_ID_SIZE];
    char meta_data[EMB_DEVICE_META_DATA_SIZE];
    char app_state[EMB_APP_DATA_SIZE];
    int64_t crash_ts;
    int64_t start_ts;
    int sig_code;
    int sig_no;
    int sig_errno;
    uintptr_t fault_addr;
    uint8_t unwinder_error;
} emb_crash;

#endif //EMBRACE_NATIVE_CRASHES_STACK_FRAMES_H
