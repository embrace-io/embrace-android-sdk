//
// Created by Eric Lanz on 5/5/20.
//

#ifndef EMBRACE_NATIVE_CRASHES_SIGNALS_CPP_H
#define EMBRACE_NATIVE_CRASHES_SIGNALS_CPP_H

#ifdef __cplusplus
extern "C" {
#endif

#include "../jnibridge/emb_ndk_manager.h"

bool emb_setup_cpp_sig_handler(emb_env *env);
void emb_remove_cpp_sig_handler(void);

#ifdef __cplusplus
}
#endif

#endif //EMBRACE_NATIVE_CRASHES_SIGNALS_CPP_H
