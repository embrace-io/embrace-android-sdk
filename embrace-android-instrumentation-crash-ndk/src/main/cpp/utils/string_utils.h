#include <stdio.h>
#include <string.h>

#ifdef __cplusplus
extern "C" {
#endif

void emb_strncpy(char *destination, const char *source, size_t destination_len);
void emb_convert_to_hex_addr(uint64_t addr, char *buffer);

#ifdef __cplusplus
}
#endif
