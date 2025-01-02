#include <stdlib.h>
#include "greatest/greatest.h"
#include "utilities.h"

TEST string_copy_success(void) {
    char dst[16];
    char src[] = "Hello, World!";
    emb_strncpy(dst, src, sizeof(src));
    ASSERT_STR_EQ(dst, "Hello, World!");
    PASS();
}

TEST string_null_src(void) {
    char dst[16];
    emb_strncpy(dst, NULL, sizeof(12));
    PASS(); // don't crash on null
}

TEST string_null_dst(void) {
    char src[] = "Hello, World!";
    emb_strncpy(NULL, src, sizeof(src));
    PASS(); // don't crash on null
}

TEST convert_to_hex(void) {
    char a[kEMBSampleAddrLen] = {0};
    emb_convert_to_hex_addr(0x12345678, a);
    ASSERT_STR_EQ(a, "0x12345678");
    PASS();
}

TEST test_string_copy_larger_source(void) {
    char dst[2];
    char src[] = "123456";
    emb_strncpy(dst, src, sizeof(dst));
    ASSERT_STR_EQ("1", dst);    // the rest of the source is truncated to size 2 (1 and a null terminator)
    PASS();
}

TEST test_string_copy_larger_destination(void) {
    char dst[6] = {0};
    char src[] = "12";
    emb_strncpy(dst, src, sizeof(dst));
    ASSERT_STR_EQ("12", dst);
    PASS();
}

SUITE(suite_utilities) {
    RUN_TEST(string_copy_success);
    RUN_TEST(string_null_src);
    RUN_TEST(string_null_dst);
    RUN_TEST(convert_to_hex);
    RUN_TEST(test_string_copy_larger_source);
    RUN_TEST(test_string_copy_larger_destination);
}
