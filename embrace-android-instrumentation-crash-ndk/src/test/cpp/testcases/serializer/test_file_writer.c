#include <stdlib.h>
#include <unistd.h>
#include <asm-generic/fcntl.h>
#include <fcntl.h>
#include <stdio.h>
#include <string.h>
#include <sys/stat.h>
#include "greatest/greatest.h"
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
        .user_session_id = "dummy_user_session_id",
        .report_id = "dummy_report_id",
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

TEST read_unterminated_emb_crash(void) {
    int fd = open(temp_file_path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    ASSERT(fd != -1);
    char *junk = malloc(sizeof(emb_crash));
    ASSERT_NEQ(NULL, junk);
    memset(junk, 0x41, sizeof(emb_crash));
    ssize_t written = write(fd, junk, sizeof(emb_crash));
    close(fd);
    free(junk);
    ASSERT_EQ(sizeof(emb_crash), written);

    emb_crash *result = emb_read_crash_from_file(temp_file_path);
    ASSERT_NEQ(NULL, result);
    ASSERT_EQ(sizeof(result->session_id) - 1, strlen(result->session_id));
    ASSERT_EQ(sizeof(result->user_session_id) - 1, strlen(result->user_session_id));
    ASSERT_EQ(sizeof(result->report_id) - 1, strlen(result->report_id));
    ASSERT_EQ(sizeof(result->capture.name) - 1, strlen(result->capture.name));
    ASSERT_EQ(sizeof(result->capture.message) - 1, strlen(result->capture.message));
    for (int i = 0; i < kEMBMaxSFrames; ++i) {
        emb_sframe *frame = &result->capture.stacktrace[i];
        ASSERT_EQ(sizeof(frame->filename) - 1, strlen(frame->filename));
        ASSERT_EQ(sizeof(frame->method) - 1, strlen(frame->method));
        ASSERT_EQ(sizeof(frame->build_id) - 1, strlen(frame->build_id));
        ASSERT_EQ(sizeof(frame->function_name) - 1, strlen(frame->function_name));
        ASSERT_EQ(sizeof(frame->full_name) - 1, strlen(frame->full_name));
    }

    char *json = emb_crash_to_json(result);
    ASSERT_NEQ(NULL, json);
    free(json);
    free(result);
    PASS();
}

TEST write_truncates_existing_file(void) {
    int fd = open(temp_file_path, O_WRONLY | O_CREAT | O_TRUNC, 0644);
    ASSERT(fd != -1);
    size_t oversized = sizeof(emb_crash) * 2;
    char *junk = calloc(1, oversized);
    ASSERT_NEQ(NULL, junk);
    ssize_t written = write(fd, junk, oversized);
    close(fd);
    free(junk);
    ASSERT_EQ(oversized, written);

    emb_env env = {0};
    memcpy(&env.crash, &crash, sizeof(emb_crash));
    strncpy(env.report_path, temp_file_path, sizeof(env.report_path) - 1);
    ASSERT(emb_write_crash_to_file(&env));

    struct stat st;
    ASSERT_EQ(0, stat(temp_file_path, &st));
    ASSERT_EQ(sizeof(emb_crash), st.st_size);
    PASS();
}

TEST read_rejects_symlink(void) {
    emb_env env = {0};
    memcpy(&env.crash, &crash, sizeof(emb_crash));
    strncpy(env.report_path, temp_file_path, sizeof(env.report_path) - 1);
    ASSERT(emb_write_crash_to_file(&env));

    emb_crash *direct = emb_read_crash_from_file(temp_file_path);
    ASSERT_NEQ(NULL, direct);
    free(direct);

    char link_path[sizeof(temp_file_path) + 8];
    snprintf(link_path, sizeof(link_path), "%s.link", temp_file_path);
    unlink(link_path);
    ASSERT_EQ(0, symlink(temp_file_path, link_path));

    emb_crash *result = emb_read_crash_from_file(link_path);
    unlink(link_path);
    ASSERT_EQ(NULL, result);
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
    RUN_TEST(read_unterminated_emb_crash);
    RUN_TEST(write_truncates_existing_file);
    RUN_TEST(read_rejects_symlink);
}
