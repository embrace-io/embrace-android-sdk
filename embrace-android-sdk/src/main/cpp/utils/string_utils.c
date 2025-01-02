#include "string_utils.h"
#include "../schema/stack_frames.h"

/**
 * Copy a string from source to destination. If the source is larger than the destination, it will be truncated.
 * The destination buffer will always be null-terminated.
 * @param destination The destination buffer.
 * @param source The source buffer.
 * @param destination_len The length of the destination buffer.
 */
void emb_strncpy(char *destination, const char *source, size_t destination_len) {
    if (destination == NULL || source == NULL || destination_len == 0) {
        return;
    }

    size_t i;
    for (i = 0; i < destination_len - 1 && source[i] != '\0'; i++) {
        destination[i] = source[i];
    }

    destination[i] = '\0'; // Null-terminate the destination buffer.
}

void emb_convert_to_hex_addr(uint64_t addr, char *buffer) {
    snprintf(buffer, kEMBSampleAddrLen, "0x%lx", (unsigned long) addr);
}
