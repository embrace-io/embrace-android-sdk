//
// Created by Eric Lanz on 5/17/20.
//

#include "file_writer.h"
#include "inttypes.h"
#include "base_64_encoder.h"
#include "3rdparty/parson/parson.h"
#include "utilities.h"
#include "emb_log.h"
#include <errno.h>
#include <fcntl.h>
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>

// crash keys
static const char *kDeviceMetaKey = "meta";
static const char *kReportIDKey = "report_id";
static const char *kSessionIDKey = "sid";
static const char *kCrashTSKey = "ts";
static const char *kAppStateKey = "state";
static const char *kUnwinderErrorCode = "ue";
static const char *kExceptionNameKey = "en";
static const char *kExceptionMsgKey = "em";
static const char *kExceptionCodeKey = "ec";
static const char *kExceptionErrnoKey = "ee";
static const char *kExceptionSignoKey = "es";
static const char *kExceptionFaultAddr = "fa";
static const char *kFramesKey = "fr";
static const char *kFilenameKey = "mo";
static const char *kMethodKey = "md";
static const char *kFrameAddrKey = "fa";
static const char *kOffsetAddrKey = "oa";
static const char *kModuleAddrKey = "ma";
static const char *kLineNumKey = "ln";
static const char *kBuildIdKey = "build_id";
static const char *kFullNameKey = "full_name";
static const char *kFunctionNameKey = "function_name";
static const char *kRelPcKey = "rel_pc";
static const char *kPcKey = "pc";
static const char *kSpKey = "sp";
static const char *kLrKey = "lr";
static const char *kStartKey = "start";
static const char *kEndKey = "end";
static const char *kOffsetKey = "offset";
static const char *kFunctionOffsetKey = "function_offset";
static const char *kFlagsKey = "flags";
static const char *kElfFileNotReadableKey = "elf_file_not_readable";
static const char *kCrashKey = "crash";
static const char *kVersionKey = "v";

// error keys
static const char *kErrNum = "n";
static const char *kErrContext = "c";

// when values are "" in our tracking struct this string will be used instead so the server knows it was intentional
// currently sticking with ""
static const char *kDefaultNULLFallbackString = "";
static const char *kCurrentPayloadVersion = "1";


bool emb_write_crash_to_file(emb_env *env) {
    int fd = open(env->report_path, O_WRONLY | O_CREAT, 0644);
    if (fd == -1) {
        emb_log_last_error(env, EMB_ERROR_FAILED_TO_OPEN_CRASH_FILE, 0);
        return false;
    }

    ssize_t len = write(fd, &env->crash, sizeof(emb_crash));
    close(fd);
    return len == sizeof(emb_crash);
}

emb_crash *emb_read_crash_from_file(const char *path) {
    int fd = open(path, O_RDONLY);
    if (fd == -1) {
        EMB_LOGERROR("failed to open native crash file at %s", path);
        return NULL;
    }

    size_t crash_size = sizeof(emb_crash);
    emb_crash *crash = calloc(1, crash_size);

    ssize_t len = read(fd, crash, crash_size);

    if (len == -1) { // log the error code for more debug info.
        EMB_LOGERROR("Encountered error reading emb_crash struct. %d: %s", errno, strerror(errno));
    }

    close(fd);
    if (len != crash_size) {
        EMB_LOGERROR("Exiting native crash file read because we read %d instead of %d",
                     (int) len, (int) crash_size);
        free(crash);
        return NULL;
    }
    return crash;
}

emb_error *emb_read_errors_from_file(const char *path) {
    int fd = open(path, O_RDONLY);
    if (fd == -1) {
        EMB_LOGERROR("failed to open native crash error file at %s", path);
        return NULL;
    }

    size_t error_size = sizeof(emb_error);
    emb_error *errors = calloc(EMB_MAX_ERRORS, error_size);
    emb_error *wp = errors;
    int count = 0;

    while (count < EMB_MAX_ERRORS) {
        ssize_t len = read(fd, wp++, error_size);

        if (len == -1) { // log the error code for more debug info.
            EMB_LOGERROR("Encountered error reading emb_error struct. %d: %s", errno, strerror(errno));
        }
        if (len == 0) {
            break;
        }
        if (len != error_size) {
            EMB_LOGERROR("exiting native crash error file read because we read %d instead of %d after %d errors",
                         (int) len, (int) error_size, count);
            free(errors);
            close(fd);
            return NULL;
        }
        count++;
    }

    close(fd);

    return errors;
}

void emb_log_frame_dbg_info(int i, emb_sframe *frame) {
    EMB_LOGDEV("Logging out debug info for stackframe %d", i);
    EMB_LOGDEV("filename: %s", frame->filename);
    EMB_LOGDEV("method: %s", frame->method);
    EMB_LOGDEV("frame_addr: 0x%lx", (unsigned long) frame->frame_addr);
    EMB_LOGDEV("offset_addr: 0x%lx", (unsigned long) frame->offset_addr);
    EMB_LOGDEV("module_addr: 0x%lx", (unsigned long) frame->module_addr);
    EMB_LOGDEV("line_num: 0x%lx", (unsigned long) frame->line_num);
    EMB_LOGDEV("build_id: %s", frame->build_id);
    EMB_LOGDEV("full_name: %s", frame->full_name);
    EMB_LOGDEV("function_name: %s", frame->function_name);
    EMB_LOGDEV("rel_pc: 0x%lx", (unsigned long) frame->rel_pc);
    EMB_LOGDEV("pc: 0x%lx", (unsigned long) frame->pc);
    EMB_LOGDEV("sp: 0x%lx", (unsigned long) frame->sp);
    EMB_LOGDEV("lr: 0x%lx", (unsigned long) frame->lr);
    EMB_LOGDEV("start: 0x%lx", (unsigned long) frame->start);
    EMB_LOGDEV("end: 0x%lx", (unsigned long) frame->end);
    EMB_LOGDEV("offset: 0x%lx", (unsigned long) frame->offset);
    EMB_LOGDEV("function_offset: 0x%lx", (unsigned long) frame->function_offset);
    EMB_LOGDEV("flags: 0x%lx", (unsigned long) frame->flags);
    EMB_LOGDEV("flags: %d", frame->elf_file_not_readable);
}

char *emb_crash_to_json(emb_crash *crash) {
    EMB_LOGDEV("Starting serialization of emb_crash struct to JSON string.");
    JSON_Value *root_value = json_value_init_object();
    JSON_Object *root_object = json_value_get_object(root_value);
    char *serialized_string = NULL;

    JSON_Value *meta_value = json_parse_string(crash->meta_data);

    if (meta_value != NULL) {
        EMB_LOGDEV("Successfully parsed crash JSON metadata");
        json_object_set_value(root_object, kDeviceMetaKey, meta_value);
    } else {
        EMB_LOGERROR("Could not JSON decode metadata: %s", crash->meta_data);
    }

    EMB_LOGDEV("Serializing IDs + payload version.");
    json_object_set_string(root_object, kReportIDKey, crash->report_id);
    json_object_set_string(root_object, kVersionKey, kCurrentPayloadVersion);
    json_object_set_number(root_object, kCrashTSKey, crash->crash_ts);
    json_object_set_string(root_object, kSessionIDKey, crash->session_id);
    json_object_set_string(root_object, kAppStateKey, crash->app_state);

    // crash data
    EMB_LOGDEV("Serializing crash data.");
    JSON_Value *crash_value = json_value_init_object();
    JSON_Object *crash_object = json_value_get_object(crash_value);

    json_object_set_number(root_object, kUnwinderErrorCode, crash->unwinder_error);

    emb_exception *exception = &crash->capture;
    // exception name
    if (strlen(exception->name) == 0) {
        EMB_LOGDEV("Defaulting to NULL exception name.");
        json_object_set_string(crash_object, kExceptionNameKey, kDefaultNULLFallbackString);
    } else {
        EMB_LOGDEV("Serializing exception name %s", exception->name);
        json_object_set_string(crash_object, kExceptionNameKey, exception->name);
    }
    // exception message
    if (strlen(exception->message) == 0) {
        EMB_LOGDEV("Defaulting to NULL exception message.");
        json_object_set_string(crash_object, kExceptionMsgKey, kDefaultNULLFallbackString);
    } else {
        EMB_LOGDEV("Serializing exception message %s", exception->message);
        json_object_set_string(crash_object, kExceptionMsgKey, exception->message);
    }

    EMB_LOGDEV("Serializing signal information. sig_code=%d, sig_errno=%d, sig_no=%d",
               crash->sig_code, crash->sig_errno, crash->sig_no);
    json_object_set_number(crash_object, kExceptionCodeKey, crash->sig_code);
    json_object_set_number(crash_object, kExceptionErrnoKey, crash->sig_errno);
    json_object_set_number(crash_object, kExceptionSignoKey, crash->sig_no);
    json_object_set_number(crash_object, kExceptionFaultAddr, crash->fault_addr);

    JSON_Value *frames_value = json_value_init_array();
    JSON_Array *frames_object = json_value_get_array(frames_value);
    EMB_LOGDEV("About to serialize %d stack frames.", (int) exception->num_sframes);

    for (int i = 0; i < exception->num_sframes; ++i) {
        JSON_Value *frame_value = json_value_init_object();
        JSON_Object *frame_object = json_value_get_object(frame_value);

        emb_sframe frame = exception->stacktrace[i];

        // module name
        if (strlen(frame.filename) == 0) {
            json_object_set_string(frame_object, kFilenameKey, kDefaultNULLFallbackString);
        } else {
            json_object_set_string(frame_object, kFilenameKey, frame.filename);
        }
        // symbol name
        if (strlen(frame.method) == 0) {
            json_object_set_string(frame_object, kMethodKey, kDefaultNULLFallbackString);
        } else {
            json_object_set_string(frame_object, kMethodKey, frame.method);
        }
        // TODO: lu vs u?
        json_object_set_number(frame_object, kFrameAddrKey, frame.frame_addr);
        json_object_set_number(frame_object, kOffsetAddrKey, frame.offset_addr);
        json_object_set_number(frame_object, kModuleAddrKey, frame.module_addr);
        json_object_set_number(frame_object, kLineNumKey, frame.line_num);
        json_object_set_string(frame_object, kBuildIdKey, frame.build_id);

        // extra debug info
        json_object_set_string(frame_object, kFullNameKey, frame.full_name);
        json_object_set_string(frame_object, kFunctionNameKey, frame.function_name);
        json_object_set_number(frame_object, kRelPcKey, (unsigned long) frame.rel_pc);
        json_object_set_number(frame_object, kPcKey, (unsigned long) frame.pc);
        json_object_set_number(frame_object, kSpKey, (unsigned long) frame.sp);
        json_object_set_number(frame_object, kLrKey, (unsigned long) frame.lr);
        json_object_set_number(frame_object, kStartKey, (unsigned long) frame.start);
        json_object_set_number(frame_object, kEndKey, (unsigned long) frame.end);
        json_object_set_number(frame_object, kOffsetKey, (unsigned long) frame.offset);
        json_object_set_number(frame_object, kFunctionOffsetKey,(unsigned long) frame.function_offset);
        json_object_set_number(frame_object, kFlagsKey, frame.flags);
        json_object_set_number(frame_object, kElfFileNotReadableKey, frame.elf_file_not_readable);

        json_array_append_value(frames_object, frame_value);
        emb_log_frame_dbg_info(i, &frame);
    }
    EMB_LOGDEV("Finished serializing stackframes.");

    json_object_set_value(crash_object, kFramesKey, frames_value);

    EMB_LOGDEV("Converting tree to JSON string.");
    char *serialized_crash = json_serialize_to_string_pretty(crash_value);

    EMB_LOGDEV("Starting Base64 encoding.");
    char *base64_crash = b64_encode(serialized_crash, strlen(serialized_crash));
    json_free_serialized_string(serialized_crash);

    EMB_LOGDEV("Altering JSON tree root.");
    json_object_set_string(root_object, kCrashKey, base64_crash);
    free(base64_crash);

    // final result
    EMB_LOGDEV("Serializing final JSON string");
    serialized_string = json_serialize_to_string_pretty(root_value);
//    json_free_serialized_string(serialized_string);
    json_value_free(root_value);
    json_value_free(crash_value);
    return serialized_string;
}

char *emb_errors_to_json(emb_error *errors) {
    EMB_LOGDEV("Starting serialization of emb_error struct to JSON string.");
    char *serialized_string = NULL;
    emb_error *cur_error = errors;
    int count = 0;

    JSON_Value *errors_value = json_value_init_array();
    JSON_Array *errors_object = json_value_get_array(errors_value);

    while (count < EMB_MAX_ERRORS) {
        // errors is calloc'd so we know that once we hit a value with zero, we are done.
        if (cur_error->num == 0) {
            break;
        }

        JSON_Value *error_value = json_value_init_object();
        JSON_Object *error_object = json_value_get_object(error_value);

        json_object_set_number(error_object, kErrNum, cur_error->num);
        json_object_set_number(error_object, kErrContext, cur_error->context);

        json_array_append_value(errors_object, error_value);

        cur_error++;
        count++;
    }
    EMB_LOGDEV("Converted %d errors.", count);
    EMB_LOGDEV("Serializing final JSON string.");
    serialized_string = json_serialize_to_string_pretty(errors_value);
    json_value_free(errors_value);
    return serialized_string;
}
