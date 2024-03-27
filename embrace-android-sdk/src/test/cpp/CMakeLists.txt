include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ../../main/cpp
        ../../main/cpp/3rdparty
        ../../main/cpp/anr
        ../../main/cpp/crashmarker
        ../../main/cpp/jnibridge
        ../../main/cpp/safejni
        ../../main/cpp/sampler
        ../../main/cpp/schema
        ../../main/cpp/serializer
        ../../main/cpp/signals
        ../../main/cpp/unwinders
        ../../main/cpp/utils
)
add_library(embrace-native-test SHARED
        main.c
        testcases/utilities/test_string_utils.c
        testcases/sampler/test_unwinder_dlinfo.c
)
target_link_libraries(embrace-native-test embrace-native)
