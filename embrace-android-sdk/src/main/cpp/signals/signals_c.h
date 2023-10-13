//
// Created by Eric Lanz on 5/5/20.
//

#ifndef EMBRACE_NATIVE_CRASHES_SIGNALS_C_H
#define EMBRACE_NATIVE_CRASHES_SIGNALS_C_H

#ifdef __cplusplus
extern "C" {
#endif

#include <stdbool.h>
#include "../emb_ndk_manager.h"

bool emb_setup_c_signal_handlers(emb_env* env);
void emb_remove_c_sig_handlers();

/**
 * This function should only be called _after_ signal handlers have been installed by Embrace.
 * It copies to the string buffer if any signal handler does not match the embrace signal handler
 * (i.e it has been overwritten by some other SDK). It will also return true in this case.
 */
bool emb_check_for_overwritten_handlers(char *buffer, const size_t buffer_size);

#ifdef __cplusplus
}
#endif

#endif //EMBRACE_NATIVE_CRASHES_SIGNALS_C_H
