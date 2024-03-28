//
// Created by Eric Lanz on 5/5/20.
//

#include "../utils/string_utils.h"
#include "signals_cpp.h"
#include <cxxabi.h>
#include <exception>
#include <pthread.h>
#include <stdexcept>
#include <string>
#include <unistd.h>
#include "../utilities.h"
#include "../unwinders/unwinder.h"
#include "../file_marker.h"
#include "../file_writer.h"
#include "../emb_log.h"

void emb_termination_handler();

std::terminate_handler emb_prev_handler = nullptr;
static emb_env *_emb_env = nullptr;

void emb_fake_crash() {
    throw std::overflow_error("fake Embrace crash");
}

void install_cpp_signal_handler() {
    static pthread_mutex_t _emb_signal_mutex = PTHREAD_MUTEX_INITIALIZER;
    pthread_mutex_lock(&_emb_signal_mutex);

    // avoid setting the same termination handler twice in a row
    if (std::get_terminate() != emb_termination_handler) {
        std::terminate_handler old_handler = std::set_terminate(emb_termination_handler);

        // only store the original handler from when we were first installed.
        // this avoids the possibility of the overwritten handler calling us back
        // and triggering a hang
        if (emb_prev_handler == nullptr) {
            emb_prev_handler = old_handler;
        }
    }

    pthread_mutex_unlock(&_emb_signal_mutex);
}

bool emb_setup_cpp_sig_handler(emb_env *env) {
    _emb_env = env;
    install_cpp_signal_handler();
    return true;
}

void emb_remove_cpp_sig_handler() {
    if (_emb_env == nullptr) {
        return;
    }
    std::set_terminate(emb_prev_handler);
    _emb_env = nullptr;
}

// This is a way to pre-populate actual exception messages from the runtime
void emb_parse_exception_message(char *exc_msg, size_t length) {
    try {
        throw;
    } catch (std::exception &exc) {
        emb_strncpy(exc_msg, (char *) exc.what(), length);
    } catch (std::exception *exc) {
        emb_strncpy(exc_msg, (char *) exc->what(), length);
    } catch (std::string obj) {
        emb_strncpy(exc_msg, (char *) obj.c_str(), length);
    } catch (char *obj) {
        snprintf(exc_msg, length, "%s", obj);
    } catch (char obj) {
        snprintf(exc_msg, length, "%c", obj);
    } catch (short obj) {
        snprintf(exc_msg, length, "%d", obj);
    } catch (int obj) {
        snprintf(exc_msg, length, "%d", obj);
    } catch (long obj) {
        snprintf(exc_msg, length, "%ld", obj);
    } catch (long long obj) {
        snprintf(exc_msg, length, "%lld", obj);
    } catch (long double obj) {
        snprintf(exc_msg, length, "%Lf", obj);
    } catch (double obj) {
        snprintf(exc_msg, length, "%f", obj);
    } catch (float obj) {
        snprintf(exc_msg, length, "%f", obj);
    } catch (unsigned char obj) {
        snprintf(exc_msg, length, "%u", obj);
    } catch (unsigned short obj) {
        snprintf(exc_msg, length, "%u", obj);
    } catch (unsigned int obj) {
        snprintf(exc_msg, length, "%u", obj);
    } catch (unsigned long obj) {
        snprintf(exc_msg, length, "%lu", obj);
    } catch (unsigned long long obj) {
        snprintf(exc_msg, length, "%llu", obj);
    } catch (...) {
        // unknown
    }
}

void emb_termination_handler() {
    if (_emb_env == nullptr || _emb_env->currently_handling) {
        return;
    }

    emb_set_crash_time(_emb_env);

    _emb_env->currently_handling = true;
    _emb_env->crash.unhandled = true;
    _emb_env->crash.unhandled_count++;
    _emb_env->crash.capture.num_sframes = emb_process_capture(_emb_env, nullptr, nullptr);

    std::type_info *type_info = __cxxabiv1::__cxa_current_exception_type();
    if (type_info != nullptr) {
        emb_strncpy(_emb_env->crash.capture.name,
                    (char *) type_info->name(),
                    sizeof(_emb_env->crash.capture.name));
    }
    size_t msg_len = sizeof(_emb_env->crash.capture.message);
    char msg[msg_len];
    emb_parse_exception_message(msg, msg_len);
    emb_strncpy(_emb_env->crash.capture.message, (char *) msg,
                sizeof(_emb_env->crash.capture.message));

    emb_write_crash_to_file(_emb_env);
    _emb_env->already_handled_crash = true;

    // Used to determine during the next launch if we crashed on the previous launch.
    emb_write_crash_marker_file(_emb_env, CRASH_MARKER_SOURCE_CPP_EXCEPTION);

    if (_emb_env->err_fd > 0) {
        close(_emb_env->err_fd);
    }

    emb_remove_cpp_sig_handler();
    if (emb_prev_handler != nullptr) {
        emb_prev_handler();
    }
}
