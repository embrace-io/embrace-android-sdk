# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.

cmake_minimum_required(VERSION 3.4.1)

set (CMAKE_CXX_STANDARD 17)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.

add_library(# Sets the name of the library.
        embrace-native

        # Sets the library as a shared library.
        SHARED

        # Provides a relative path to your source file(s).
        emb_ndk_crash_samples.cpp
        utils/emb_log.c
        jnibridge/emb_ndk_manager.c
        safejni/jni_util.c
        signals/signal_utils.c
        signals/signals_c.c
        signals/signals_cpp.cpp
        sampler/sampler_unwinder_unwind.c
        sampler/sampler_unwinder_stack.cpp
        sampler/stacktrace_sampler.c
        sampler/stacktrace_sampler_jni.c
        sampler/unwinder_dlinfo.c
        sampler/emb_timer.c
        unwinders/unwinder.c
        unwinders/stack_unwinder.cpp
        safejni/safe_jni.c
        utils/system_clock.c
        utils/string_utils.c
        utils/utilities.c
        crashmarker/file_marker.c
        serializer/file_writer.c
        3rdparty/base64/base_64_encoder.c
        3rdparty/parson/parson.c
        utils/cpuinfo.c
        )

include_directories(
        3rdparty/libunwind/include
        3rdparty/libunwindstack-ndk/include
        3rdparty/parson
)

# Searches for a specified prebuilt library and stores the path as a
# variable. Because CMake includes system libraries in the search path by
# default, you only need to specify the name of the public NDK library
# you want to add. CMake verifies that the library exists before
# completing its build.

find_library( # Sets the name of the path variable.
        log-lib

        # Specifies the name of the NDK library that
        # you want CMake to locate.
        log)

# Specifies libraries CMake should link to your target library. You
# can link multiple libraries, such as libraries you define in this
# build script, prebuilt third-party libraries, or system libraries.

target_link_libraries( # Specifies the target library.
        embrace-native

        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})

set_target_properties(embrace-native
        PROPERTIES
        COMPILE_OPTIONS
        -Werror -pedantic -Wall)

add_subdirectory(3rdparty/libunwindstack-ndk/cmake)
target_link_libraries(embrace-native unwindstack)
target_link_options(embrace-native PRIVATE "-Wl,-z,max-page-size=16384")
if (${ANDROID_ABI} STREQUAL "armeabi" OR ${ANDROID_ABI} STREQUAL "armeabi-v7a")
    add_library(libunwind STATIC IMPORTED)
    set_target_properties(libunwind PROPERTIES IMPORTED_LOCATION
            ${ANDROID_NDK}/sources/cxx-stl/llvm-libc++/libs/${ANDROID_ABI}/libunwind.a)
    target_link_libraries(embrace-native libunwind)
endif ()