#include <stdbool.h>

static volatile bool g_emb_dev_logging = false;

void emb_enable_dev_logging() {
    g_emb_dev_logging = true;
}

bool emb_dev_logging_enabled() {
    return g_emb_dev_logging;
}
