#include <stdio.h>
#include <jni.h>
#include "stacktrace_sampler.h"
#include "../utils/utilities.h"
#include "../safejni/safe_jni.h"
#include "../utils/emb_log.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef struct {
    bool initialized;
    jclass clz_native_thread_anr_sample;
    jclass clz_native_thread_anr_stackframe;
    jclass clz_integer;
    jclass clz_long;
    jclass clz_arraylist;

    jmethodID ctor_native_thread_anr_sample;
    jmethodID ctor_native_thread_anr_stackframe;
    jmethodID ctor_integer;
    jmethodID ctor_long;
    jmethodID ctor_arraylist;
    jmethodID mthd_arraylist_add;
} volatile emb_sampler_jni_cache;

/* Caches global JNI refs to avoid expensive lookups */
static emb_sampler_jni_cache impl_cache = {0};
static emb_sampler_jni_cache *cache = &impl_cache;

/**
 * Populates the JNI cache, storing the classes as global refs in the struct.
 */
static bool populate_jni_cache(JNIEnv *env) {
    EMB_LOGDEV("Populating the JNI cache.");

    // load class ref for NativeThreadAnrSample
    cache->clz_native_thread_anr_sample = emb_jni_find_class_global_ref(env,
                                                                        "io/embrace/android/embracesdk/payload/NativeThreadAnrSample");
    if (cache->clz_native_thread_anr_sample == NULL) {
        EMB_LOGDEV("Failed to initialize clz_native_thread_anr_sample");
        return false;
    }

    // load class ref for NativeThreadAnrStackframe
    cache->clz_native_thread_anr_stackframe = emb_jni_find_class_global_ref(env,
                                                                            "io/embrace/android/embracesdk/payload/NativeThreadAnrStackframe");
    if (cache->clz_native_thread_anr_stackframe == NULL) {
        EMB_LOGDEV("Failed to initialize clz_native_thread_anr_stackframe");
        return false;
    }

    // load class ref for Integer
    cache->clz_integer = emb_jni_find_class_global_ref(env, "java/lang/Integer");
    if (cache->clz_integer == NULL) {
        EMB_LOGDEV("Failed to initialize clz_integer");
        return false;
    }

    // load class ref for Long
    cache->clz_long = emb_jni_find_class_global_ref(env, "java/lang/Long");
    if (cache->clz_long == NULL) {
        EMB_LOGDEV("Failed to initialize clz_long");
        return false;
    }

    // load class ref for ArrayList
    cache->clz_arraylist = emb_jni_find_class_global_ref(env, "java/util/ArrayList");
    if (cache->clz_arraylist == NULL) {
        EMB_LOGDEV("Failed to initialize clz_arraylist");
        return false;
    }

    // load method ref for NativeThreadAnrSample ctor
    cache->ctor_native_thread_anr_sample = emb_jni_get_method_id(env,
                                                                 cache->clz_native_thread_anr_sample,
                                                                 "<init>",
                                                                 "(Ljava/lang/Integer;Ljava/lang/Long;Ljava/lang/Long;Ljava/util/List;)V");
    if (cache->ctor_native_thread_anr_sample == NULL) {
        EMB_LOGDEV("Failed to initialize ctor_native_thread_anr_sample");
        return false;
    }

    // load method ref for NativeThreadAnrStackframe ctor
    cache->ctor_native_thread_anr_stackframe = emb_jni_get_method_id(env,
                                                                     cache->clz_native_thread_anr_stackframe,
                                                                     "<init>",
                                                                     "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Integer;)V");
    if (cache->ctor_native_thread_anr_stackframe == NULL) {
        EMB_LOGDEV("Failed to initialize ctor_native_thread_anr_stackframe");
        return false;
    }

    // load method ref for Integer ctor
    cache->ctor_integer = emb_jni_get_method_id(env, cache->clz_integer, "<init>", "(I)V");
    if (cache->ctor_integer == NULL) {
        EMB_LOGDEV("Failed to initialize ctor_integer");
        return false;
    }

    // load method ref for Long ctor
    cache->ctor_long = emb_jni_get_method_id(env, cache->clz_long, "<init>", "(J)V");
    if (cache->ctor_long == NULL) {
        EMB_LOGDEV("Failed to initialize ctor_long");
        return false;
    }

    // load method ref for ArrayList ctor
    cache->ctor_arraylist = emb_jni_get_method_id(env, cache->clz_arraylist, "<init>", "(I)V");
    if (cache->ctor_arraylist == NULL) {
        EMB_LOGDEV("Failed to initialize ctor_arraylist");
        return false;
    }

    // load method ref for ArrayList#add
    cache->mthd_arraylist_add = emb_jni_get_method_id(env, cache->clz_arraylist, "add",
                                                      "(Ljava/lang/Object;)Z");
    if (cache->mthd_arraylist_add == NULL) {
        EMB_LOGDEV("Failed to initialize mthd_arraylist_add");
        return false;
    }

    // everything initialized fine.
    EMB_LOGDEV("Populated JNI cache.");
    return true;
}

/**
 * Inits a cache of JNI references. This MUST only be called from Embrace's ANR monitor
 * thread - it's not safe to share JNI refs across threads.
 *
 * The cache may fail to initialize in rare cases - e.g. if there isn't enough memory to
 * find the class/methods. We attempt to handle failure by returning early in this case,
 * and on the next call we make another attempt to initialize.
 */
static bool init_jni_cache(JNIEnv *env) {
    if (!cache->initialized) {
        cache->initialized = populate_jni_cache(env);
    }
    return cache->initialized;
}

void convert_to_hex_addr(uint64_t addr, char *buffer) {
    snprintf(buffer, kEMBSampleAddrLen, "0x%lx", (unsigned long) addr);
}

static bool add_element_to_sample_list(JNIEnv *env, emb_sample *sample,
                                       jobject frames, int index) {
    bool success = false;
    emb_sample_stackframe *frame = &(sample->stack[index]);

    // convert pointers to hex addresses.
    char pc_buf[kEMBSampleAddrLen] = {0};
    char load_buf[kEMBSampleAddrLen] = {0};
    convert_to_hex_addr(frame->pc, pc_buf);
    convert_to_hex_addr(frame->so_load_addr, load_buf);

    jstring pc = NULL;
    jstring so_load_addr = NULL;
    jstring so_path = NULL;
    jobject result = NULL;
    jobject obj = NULL;

    // create string for NativeThreadAnrStackframe#pc
    pc = emb_jni_new_string_utf(env, pc_buf);
    if (pc == NULL) {
        EMB_LOGDEV("Failed to instantiate NativeThreadAnrStackframe#pc");
        goto exit;
    }

    // create string for load addr
    so_load_addr = emb_jni_new_string_utf(env, load_buf);
    if (so_load_addr == NULL) {
        EMB_LOGDEV("Failed to instantiate NativeThreadAnrStackframe#soLoadAddr");
        goto exit;
    }

    // create string for SO path
    so_path = emb_jni_new_string_utf(env, (char *) frame->so_path);
    if (so_path == NULL) {
        EMB_LOGDEV("Failed to instantiate NativeThreadAnrStackframe#soPath");
        goto exit;
    }

    // create Integer for NativeThreadAnrStackframe#result
    result = emb_jni_new_object(env, cache->clz_integer, cache->ctor_integer, (jint) frame->result);
    if (result == NULL) {
        EMB_LOGDEV("Failed to instantiate NativeThreadAnrStackframe#result");
        goto exit;
    }

    // create NativeThreadAnrStackframe object
    obj = emb_jni_new_object(env, cache->clz_native_thread_anr_stackframe,
                             cache->ctor_native_thread_anr_stackframe,
                             pc, so_load_addr, so_path, result);

    // add NativeStackframe to List
    success = emb_jni_call_boolean_method(env, frames, cache->mthd_arraylist_add, obj);

    // perform explicit cleanup of JNI refs.
    exit:
    if (pc != NULL) {
        emb_jni_delete_local_ref(env, pc);
    }
    if (so_load_addr != NULL) {
        emb_jni_delete_local_ref(env, so_load_addr);
    }
    if (so_path != NULL) {
        emb_jni_delete_local_ref(env, so_path);
    }
    if (result != NULL) {
        emb_jni_delete_local_ref(env, result);
    }
    if (obj != NULL) {
        emb_jni_delete_local_ref(env, obj);
    }
    return success;
}

static jobject construct_sample_list(JNIEnv *env, emb_sample *sample) {
    jint frame_count = (jint) sample->num_sframes;

    // create ArrayList instance with exact capacity
    jobject frames = emb_jni_new_object(env, cache->clz_arraylist, cache->ctor_arraylist,
                                        frame_count);

    if (frames == NULL) {
        EMB_LOGDEV("Failed to instantiate ArrayList");
        return NULL;
    }

    for (int k = 0; k < frame_count; k++) { // add NativeThreadAnrStackframe element to list
        if (!add_element_to_sample_list(env, sample, frames, k)) {
            EMB_LOGDEV("Failed to instantiate sample list");
            return NULL;
        }
    }
    return frames;
}

static jobject emb_serialize_sample(JNIEnv *env, emb_sample *sample) {
    jobject result = NULL;
    jobject timestamp = NULL;
    jobject duration = NULL;
    jobject frames = NULL;
    jobject response = NULL;

    // create Integer for NativeThreadAnrSample#result
    result = emb_jni_new_object(env, cache->clz_integer, cache->ctor_integer,
                                (jint) sample->result);
    if (result == NULL) {
        EMB_LOGDEV("Failed to instantiate NativeThreadAnrSample#result");
        goto exit;
    }

    // create Long for NativeThreadAnrSample#sampleTimestamp
    timestamp = emb_jni_new_object(env, cache->clz_long, cache->ctor_long,
                                   (jlong) sample->timestamp_ms);
    if (timestamp == NULL) {
        EMB_LOGDEV("Failed to instantiate NativeThreadAnrSample#sampleTimestamp");
        goto exit;
    }

    // create Long for NativeThreadAnrSample#sampleDurationMs
    duration = emb_jni_new_object(env, cache->clz_long, cache->ctor_long, (jlong) sample->duration_ms);
    if (duration == NULL) {
        EMB_LOGDEV("Failed to instantiate NativeThreadAnrSample#sampleDurationMs");
        goto exit;
    }

    // create List<NativeThreadAnrStackframe> for NativeThreadAnrSample#stack
    frames = construct_sample_list(env, sample);
    if (frames == NULL) {
        EMB_LOGDEV("Failed to instantiate NativeThreadAnrSample#stackframes");
        goto exit;
    }

    // create NativeThreadAnrSample instance
    response = emb_jni_new_object(env, cache->clz_native_thread_anr_sample,
                                  cache->ctor_native_thread_anr_sample, result, timestamp,
                                  duration, frames);
    if (response == NULL) {
        EMB_LOGDEV("Failed to instantiate NativeThreadAnrSample");
        goto exit;
    }

    // perform explicit cleanup of JNI refs.
    exit:
    if (result != NULL) {
        emb_jni_delete_local_ref(env, result);
    }
    if (timestamp != NULL) {
        emb_jni_delete_local_ref(env, timestamp);
    }
    if (duration != NULL) {
        emb_jni_delete_local_ref(env, duration);
    }
    if (frames != NULL) {
        emb_jni_delete_local_ref(env, frames);
    }
    return response;
}

static jobject construct_interval_list(JNIEnv *env, emb_interval *interval) {
    jint sample_count = (jint) interval->num_samples;
    EMB_LOGDEV("Serializing %d samples", sample_count);

    // create ArrayList instance with exact capacity
    jobject samples = emb_jni_new_object(env, cache->clz_arraylist, cache->ctor_arraylist,
                                         sample_count);

    if (samples == NULL) {
        EMB_LOGDEV("Failed to instantiate ArrayList");
        return NULL;
    }

    for (int k = 0; k < sample_count; k++) { // add NativeThreadAnrSample elements to list
        jobject sample = emb_serialize_sample(env, &interval->samples[k]);
        bool success = emb_jni_call_boolean_method(env, samples, cache->mthd_arraylist_add, sample);

        if (!success) {
            EMB_LOGDEV("Failed to instantiate sample list");
            return NULL;
        }
    }
    return samples;
}

JNIEXPORT jobject JNICALL
Java_io_embrace_android_embracesdk_anr_ndk_NativeThreadSamplerNdkDelegate_finishSampling(JNIEnv *env,
                                                                                 jobject thiz) {

    // cancel any pending samples here.
    emb_stop_thread_sampler();

    // fetch the sample struct
    emb_interval *interval = emb_current_interval();
    if (interval == NULL) { // nothing collected - return.
        return NULL;
    }
    if (!init_jni_cache(env)) { // initialize cache of JNI refs if needed
        EMB_LOGDEV("JNI cache failed to initialize.");
        return NULL;
    }
    return construct_interval_list(env, interval);
}

#ifdef __cplusplus
}
#endif
