//
// Created by Eric Lanz on 5/18/20.
//

#ifndef EMBRACE_NATIVE_CRASHES_BASE_64_ENCODER_H
#define EMBRACE_NATIVE_CRASHES_BASE_64_ENCODER_H

#include <sys/types.h>

char *b64_encode(const char *in, size_t len);

#endif //EMBRACE_NATIVE_CRASHES_BASE_64_ENCODER_H
