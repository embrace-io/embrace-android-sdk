#include "string_utils.h"
#include "../schema/stack_frames.h"

/**
 * Copy a string from source to destination. If the source is larger than the destination, it will be truncated.
 * @param destination The destination buffer.
 * @param source The source buffer.
 * @param destination_len The length of the destination buffer.
 */
void emb_strncpy(char *destination, const char *source, size_t destination_len) {
    if (destination == NULL || source == NULL) {
        return;
    }
    int i = 0;
    while (i < destination_len && source[i] != '\0') {
        char current = source[i];
        destination[i] = current;
        if (current == '\0') {
            break;
        }
        i++;
    }
}

void emb_convert_to_hex_addr(uint64_t addr, char *buffer) {
    snprintf(buffer, kEMBSampleAddrLen, "0x%lx", (unsigned long) addr);
}
