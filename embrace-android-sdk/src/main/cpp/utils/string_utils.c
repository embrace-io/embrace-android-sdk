#include "string_utils.h"

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
