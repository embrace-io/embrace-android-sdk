//
// Created by Eric Lanz on 5/17/20.
//

#include "file_writer.h"
#include "inttypes.h"
#include "../3rdparty/base64/base_64_encoder.h"
#include "parson.h"
#include "../utils/utilities.h"
#include "../utils/emb_log.h"
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

#define RETURN_ON_JSON_FAILURE(func) \
if ((func) != JSONSuccess) { \
    return false; \
}

bool emb_add_metadata_to_json(const emb_crash *crash, JSON_Object *root_object);

bool emb_add_basic_info_to_json(const emb_crash *crash, JSON_Object *root_object);

bool emb_add_exc_info_to_json(const emb_crash *crash, JSON_Object *crash_object,
                              const emb_exception *exception);

bool emb_add_frame_dbg_to_json(JSON_Object *frame_object, emb_sframe *frame);

bool emb_add_frame_info_to_json(JSON_Object *frame_object, emb_sframe *frame);

bool emb_add_exc_to_json(const emb_exception *exception, JSON_Array *frames_object);

bool emb_add_b64_value_to_json(JSON_Object *root_object, const JSON_Value *crash_value);

bool emb_build_crash_json_tree(emb_crash *crash, JSON_Object *root_object,
                               JSON_Object *crash_object);

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

    if (crash == NULL) {
        close(fd);
        return NULL;
    }

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
    if (errors == NULL) {
        close(fd);
        return NULL;
    }
    emb_error *wp = errors;
    int count = 0;

    while (count < EMB_MAX_ERRORS) {
        ssize_t len = read(fd, wp++, error_size);

        if (len == -1) { // log the error code for more debug info.
            EMB_LOGERROR("Encountered error reading emb_error struct. %d: %s", errno,
                         strerror(errno));
        }
        if (len == 0) {
            break;
        }
        if (len != error_size) {
            EMB_LOGERROR(
                    "exiting native crash error file read because we read %d instead of %d after %d errors",
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

char *emb_crash_to_json(emb_crash *crash) {
    if (crash == NULL) {
        return NULL;
    }
    EMB_LOGDEV("Starting serialization of emb_crash struct to JSON string.");
    JSON_Value *root_value = NULL;
    JSON_Object *root_object = NULL;
    JSON_Value *crash_value = NULL;
    JSON_Object *crash_object = NULL;
    char *serialized_string = NULL;

    // initialize JSON objects.
    root_value = json_value_init_object();
    if (root_value == NULL) {
        goto Exit;
    }
    root_object = json_value_get_object(root_value);
    if (root_object == NULL) {
        goto Exit;
    }
    crash_value = json_value_init_object();
    if (crash_value == NULL) {
        goto Exit;
    }
    crash_object = json_value_get_object(crash_value);
    if (crash_object == NULL) {
        goto Exit;
    }

    // start adding metadata/basic info to tree
    if (!emb_add_metadata_to_json(crash, root_object)) {
        goto Exit;
    }
    if (!emb_add_basic_info_to_json(crash, root_object)) {
        goto Exit;
    }

    // add crash data to tree
    if (!emb_build_crash_json_tree(crash, root_object, crash_object)) {
        goto Exit;
    }

    if (!emb_add_b64_value_to_json(root_object, crash_value)) {
        goto Exit;
    }

    // serialize final result & return as string
    EMB_LOGDEV("Serializing final JSON string");
    serialized_string = json_serialize_to_string(root_value);

    Exit:
    if (root_value != NULL) {
        json_value_free(root_value);
    }
    if (crash_value != NULL) {
        json_value_free(crash_value);
    }
    return serialized_string;
}

bool emb_add_metadata_to_json(const emb_crash *crash, JSON_Object *root_object) {
    JSON_Value *meta_value = json_parse_string(crash->meta_data);
    bool success = false;

    if (meta_value != NULL) {
        success = json_object_set_value(root_object, kDeviceMetaKey, meta_value) == JSONSuccess;
    }
    return success;
}

bool emb_add_basic_info_to_json(const emb_crash *crash, JSON_Object *root_object) {
    EMB_LOGDEV("Serializing IDs + payload version.");
    RETURN_ON_JSON_FAILURE(json_object_set_string(root_object, kReportIDKey, crash->report_id));
    RETURN_ON_JSON_FAILURE(json_object_set_string(root_object, kVersionKey, kCurrentPayloadVersion));
    RETURN_ON_JSON_FAILURE(json_object_set_number(root_object, kCrashTSKey, crash->crash_ts));
    RETURN_ON_JSON_FAILURE(json_object_set_string(root_object, kSessionIDKey, crash->session_id));
    RETURN_ON_JSON_FAILURE(json_object_set_string(root_object, kAppStateKey, crash->app_state));
    return true;
}

bool emb_add_exc_info_to_json(const emb_crash *crash, JSON_Object *crash_object,
                              const emb_exception *exception) {// exception name
    if (strlen(exception->name) == 0) {
        EMB_LOGDEV("Defaulting to NULL exception name.");
        RETURN_ON_JSON_FAILURE(json_object_set_string(crash_object, kExceptionNameKey, kDefaultNULLFallbackString));
    } else {
        EMB_LOGDEV("Serializing exception name %s", exception->name);
        RETURN_ON_JSON_FAILURE(json_object_set_string(crash_object, kExceptionNameKey, exception->name));
    }
    // exception message
    if (strlen(exception->message) == 0) {
        EMB_LOGDEV("Defaulting to NULL exception message.");
        RETURN_ON_JSON_FAILURE(json_object_set_string(crash_object, kExceptionMsgKey, kDefaultNULLFallbackString));
    } else {
        EMB_LOGDEV("Serializing exception message %s", exception->message);
        RETURN_ON_JSON_FAILURE(json_object_set_string(crash_object, kExceptionMsgKey, exception->message));
    }

    EMB_LOGDEV("Serializing signal information. sig_code=%d, sig_errno=%d, sig_no=%d",
               crash->sig_code, crash->sig_errno, crash->sig_no);

    RETURN_ON_JSON_FAILURE(json_object_set_number(crash_object, kExceptionCodeKey, crash->sig_code));
    RETURN_ON_JSON_FAILURE(json_object_set_number(crash_object, kExceptionErrnoKey, crash->sig_errno));
    RETURN_ON_JSON_FAILURE(json_object_set_number(crash_object, kExceptionSignoKey, crash->sig_no));
    RETURN_ON_JSON_FAILURE(json_object_set_number(crash_object, kExceptionFaultAddr, crash->fault_addr));
    return true;
}

bool emb_add_exc_to_json(const emb_exception *exception, JSON_Array *frames_object) {
    EMB_LOGDEV("About to serialize %d stack frames.", (int) exception->num_sframes);

    for (int i = 0; i < exception->num_sframes; ++i) {
        JSON_Value *frame_value = json_value_init_object();
        if (frame_value == NULL) {
            return false;
        }
        JSON_Object *frame_object = json_value_get_object(frame_value);
        if (frame_object == NULL) {
            return false;
        }

        emb_sframe frame = exception->stacktrace[i];
        if (!emb_add_frame_info_to_json(frame_object, &frame)) {
            return false;
        }

        // extra debug info
        if (!emb_add_frame_dbg_to_json(frame_object, &frame)) {
            return false;
        };
        RETURN_ON_JSON_FAILURE(json_array_append_value(frames_object, frame_value));
    }
    EMB_LOGDEV("Finished serializing stackframes.");
    return true;
}

bool emb_add_frame_info_to_json(JSON_Object *frame_object, emb_sframe *frame) {// module name
    if (strlen(frame->filename) == 0) {
        RETURN_ON_JSON_FAILURE(json_object_set_string(frame_object, kFilenameKey, kDefaultNULLFallbackString));
    } else {
        RETURN_ON_JSON_FAILURE(json_object_set_string(frame_object, kFilenameKey, frame->filename));
    }
    // symbol name
    if (strlen(frame->method) == 0) {
        RETURN_ON_JSON_FAILURE(json_object_set_string(frame_object, kMethodKey, kDefaultNULLFallbackString));
    } else {
        RETURN_ON_JSON_FAILURE(json_object_set_string(frame_object, kMethodKey, frame->method));
    }
    // TODO: lu vs u?
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kFrameAddrKey, frame->frame_addr));
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kOffsetAddrKey, frame->offset_addr));
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kModuleAddrKey, frame->module_addr));
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kLineNumKey, frame->line_num));
    return true;
}

bool emb_add_frame_dbg_to_json(JSON_Object *frame_object, emb_sframe *frame) {
    RETURN_ON_JSON_FAILURE(json_object_set_string(frame_object, kFullNameKey, frame->full_name));
    RETURN_ON_JSON_FAILURE(json_object_set_string(frame_object, kFunctionNameKey, frame->function_name));
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kRelPcKey, (unsigned long) frame->rel_pc));
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kPcKey, (unsigned long) frame->pc));
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kSpKey, (unsigned long) frame->sp));
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kLrKey, (unsigned long) frame->lr));
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kStartKey, (unsigned long) frame->start));
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kEndKey, (unsigned long) frame->end));
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kOffsetKey, (unsigned long) frame->offset));
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kFunctionOffsetKey,
                                                  (unsigned long) frame->function_offset));
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kFlagsKey, frame->flags));
    RETURN_ON_JSON_FAILURE(json_object_set_number(frame_object, kElfFileNotReadableKey,
                                                  frame->elf_file_not_readable));
    return true;
}

bool emb_build_crash_json_tree(emb_crash *crash, JSON_Object *root_object,
                               JSON_Object *crash_object) {
    if (crash_object == NULL) {
        return false;
    }
    RETURN_ON_JSON_FAILURE(json_object_set_number(root_object, kUnwinderErrorCode, crash->unwinder_error));

    emb_exception *exception = &crash->capture;
    if (!emb_add_exc_info_to_json(crash, crash_object, exception)) {
        return false;
    }

    JSON_Value *frames_value = json_value_init_array();
    if (frames_value == NULL) {
        return false;
    }
    JSON_Array *frames_object = json_value_get_array(frames_value);
    if (frames_object == NULL) {
        return false;
    }
    if (!emb_add_exc_to_json(exception, frames_object)) {
        return false;
    }
    if (json_object_set_value(crash_object, kFramesKey, frames_value) != JSONSuccess) {
        return false;
    }
    return true;
}

bool emb_add_b64_value_to_json(JSON_Object *root_object, const JSON_Value *crash_value) {
    EMB_LOGDEV("Converting tree to JSON string.");
    char *serialized_crash = json_serialize_to_string_pretty(crash_value);
    if (serialized_crash == NULL) {
        return false;
    }

    EMB_LOGDEV("Starting Base64 encoding.");
    char *base64_crash = b64_encode(serialized_crash, strlen(serialized_crash));

    if (base64_crash == NULL) {
        return false;
    }
    json_free_serialized_string(serialized_crash);

    EMB_LOGDEV("Altering JSON tree root.");
    if (json_object_set_string(root_object, kCrashKey, base64_crash) != JSONSuccess) {
        return false;
    }
    free(base64_crash);
    return true;
}

char *emb_errors_to_json(emb_error *errors) {
    EMB_LOGDEV("Starting serialization of emb_error struct to JSON string.");
    char *serialized_string = NULL;
    emb_error *cur_error = errors;
    int count = 0;

    JSON_Value *errors_value = json_value_init_array();
    if (errors_value == NULL) {
        return NULL;
    }
    JSON_Array *errors_object = json_value_get_array(errors_value);
    if (errors_object == NULL) {
        return NULL;
    }

    while (count < EMB_MAX_ERRORS) {
        // errors is calloc'd so we know that once we hit a value with zero, we are done.
        if (cur_error->num == 0) {
            break;
        }

        JSON_Value *error_value = json_value_init_object();
        if (error_value == NULL) {
            return NULL;
        }
        JSON_Object *error_object = json_value_get_object(error_value);
        if (error_object == NULL) {
            return NULL;
        }

        if (json_object_set_number(error_object, kErrNum, cur_error->num) != JSONSuccess) {
            return NULL;
        }
        if (json_object_set_number(error_object, kErrContext, cur_error->context) != JSONSuccess) {
            return NULL;
        }

        if (json_array_append_value(errors_object, error_value) != JSONSuccess) {
            return NULL;
        }

        cur_error++;
        count++;
    }
    EMB_LOGDEV("Converted %d errors.", count);
    EMB_LOGDEV("Serializing final JSON string.");
    serialized_string = json_serialize_to_string_pretty(errors_value);
    json_value_free(errors_value);
    return serialized_string;
}
