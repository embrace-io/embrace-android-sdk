#include "string_utils.h"
#include "../schema/stack_frames.h"

void emb_strncpy(char *dst, const char *src, size_t len) {
    if (dst == NULL || src == NULL) {
        return;
    }
    int i = 0;
    while (i <= len) {
        char current = src[i];
        dst[i] = current;
        if (current == '\0') {
            break;
        }
        i++;
    }
}

void emb_convert_to_hex_addr(uint64_t addr, char *buffer) {
    snprintf(buffer, kEMBSampleAddrLen, "0x%lx", (unsigned long) addr);
}
