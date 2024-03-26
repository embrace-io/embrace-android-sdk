include_directories(
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ../../main/cpp
        ../../main/cpp/3rdparty
        ../../main/cpp/safejni
        ../../main/cpp/sampler
        ../../main/cpp/signals
        ../../main/cpp/unwinders
        ../../main/cpp/utils
)
add_library(embrace-native-test SHARED
        main.c
)
target_link_libraries(embrace-native-test embrace-native)
