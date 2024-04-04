#include <stdlib.h>
#include <string.h>
#include "stacktrace_sampler.h"

void emb_add_fake_sample(emb_sample *sample, int pc) {
    sample->num_sframes = 1;
    sample->timestamp_ms = 1500000000;
    sample->duration_ms = 9;

    emb_sample_stackframe *frame = &(sample->stack[0]);
    frame->pc = pc;
    frame->so_load_addr = 0x87654321;
    frame->result = 0;
    strncpy((char *) frame->so_path, "libtest.so", kEMBSamplePathMaxLen);
}

void emb_setup_fake_intervals() {
    emb_interval *interval = emb_current_interval();
    emb_add_fake_sample(&(interval->samples[0]), 0x12345678);
    emb_add_fake_sample(&(interval->samples[1]), 0x12300000);
    emb_add_fake_sample(&(interval->samples[2]), 0x10090000);
    interval->num_samples = 3;
}

