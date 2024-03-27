#include <stdio.h>
#include <string.h>

#ifdef __cplusplus
extern "C" {
#endif

void emb_strncpy(char *dst, const char *src, size_t len);
void emb_convert_to_hex_addr(uint64_t addr, char *buffer);

#ifdef __cplusplus
}
#endif
