cmake_minimum_required(VERSION 3.6)

# dummy target to bring libc++_shared.so into packages
add_library(opencv_jni_shared STATIC src/main/cpp/dummy.cpp)

# Specifies a path to native header files.
include_directories(src/main/cpp/include/)
include(ndk-stl-config.cmake)