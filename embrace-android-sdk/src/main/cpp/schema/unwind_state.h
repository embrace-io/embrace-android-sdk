#include "stack_frames.h"

typedef struct {
    uint64_t stack[kEMBSampleUnwindLimit]; // unwind up to 256, then copy everything to other struct.
    uint16_t num_sframes;
    uint8_t result;
} emb_unwind_state;
