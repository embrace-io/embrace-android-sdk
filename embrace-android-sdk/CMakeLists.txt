# Add main CMake files as subdirectory - this allows creation of a test target.

cmake_minimum_required(VERSION 3.4.1)
project(TEST)
add_subdirectory(src/main/cpp)

if(${CMAKE_BUILD_TYPE} STREQUAL Debug)
    enable_testing()
    add_subdirectory(src/test/cpp)
endif()
