#include <stdlib.h>
#include <unistd.h>
#include <asm-generic/fcntl.h>
#include <fcntl.h>
#include "greatest/greatest.h"
#include "unwinder_dlinfo.h"
#include "utilities.h"
#include "file_writer.h"
#include "emb_log.h"

static char temp_file_path[512];
static char expected_json[4096];

static emb_crash crash = {
        .capture = {
                .name = "Dummy Exception",
                .message = "This is a dummy exception.",
                .num_sframes = 2,
                .stacktrace = {
                        {
                                .filename = "dummy_file_1.c",
                                .method = "dummy_function_1",
                                .build_id = "12345678",
                                .frame_addr = 0xabcdef,
                                .offset_addr = 0x123456,
                                .module_addr = 0x789abc,
                                .line_num = 42,
                                .rel_pc = 0x87654321,
                                .pc = 0x12345678,
                                .sp = 0xabcdef,
                                .lr = 0,
                                .function_offset = 0x1234,
                                .function_name = "dummy_function_1",
                                .elf_file_not_readable = false,
                                .start = 0x1000,
                                .end = 0x2000,
                                .offset = 0x100,
                                .flags = 0x1234,
                                .full_name = "dummy_file_1.c"
                        },
                        {
                                .filename = "dummy_file_2.c",
                                .method = "dummy_function_2",
                                .build_id = "87654321",
                                .frame_addr = 0x123456,
                                .offset_addr = 0xabcdef,
                                .module_addr = 0x456789,
                                .line_num = 24,
                                .rel_pc = 0x98765432,
                                .pc = 0xabcdef12,
                                .sp = 0x123456,
                                .lr = 0x345678,
                                .function_offset = 0x5678,
                                .function_name = "dummy_function_2",
                                .elf_file_not_readable = true,
                                .start = 0x2000,
                                .end = 0x3000,
                                .offset = 0x200,
                                .flags = 0x5678,
                                .full_name = "dummy_file_2.c"
                        }
                }
        },
        .unhandled = true,
        .unhandled_count = 1,
        .session_id = "dummy_session_id",
        .report_id = "dummy_report_id",
        .meta_data = "{}",
        .app_state = "foreground",
        .crash_ts = 1234567890,
        .start_ts = 1234567800,
        .sig_code = 11,
        .sig_no = 1,
        .sig_errno = 0,
        .fault_addr = 0x12345678,
        .unwinder_error = 0
};

void emb_setup_file_writer_tests(const char *path, const char *json) {
    strncpy(temp_file_path, path, sizeof(temp_file_path) - 1);
    strncpy(expected_json, json, sizeof(expected_json) - 1);
}

TEST read_invalid_emb_crash(void) {
    emb_crash *result = emb_read_crash_from_file("foo");
    ASSERT_EQ(NULL, result);
    PASS();
}

TEST serialize_null_emb_crash(void) {
    ASSERT_EQ(NULL, emb_crash_to_json(NULL));
    PASS();
}

TEST save_and_load_emb_crash(void) {
    emb_env env = {0};
    memcpy(&env.crash, &crash, sizeof(emb_crash));
    strncpy(env.report_path, temp_file_path, sizeof(env.report_path));
    emb_write_crash_to_file(&env);
    emb_crash *result = emb_read_crash_from_file(env.report_path);
    ASSERT_NEQ(NULL, result);

    // assert contents of struct are exactly same for each struct
    int status = memcmp(&crash, result, sizeof(emb_crash));
    ASSERT_EQ(0, status);
    PASS();
}

TEST serialize_emb_crash(void) {
    char *json = emb_crash_to_json(&crash);
    ASSERT_NEQ(NULL, json);
    ASSERT_STR_EQ(expected_json, json);
    PASS();
}

SUITE (suite_file_writer) {
    RUN_TEST(read_invalid_emb_crash);
    RUN_TEST(serialize_null_emb_crash);
    RUN_TEST(save_and_load_emb_crash);
    RUN_TEST(serialize_emb_crash);
}
