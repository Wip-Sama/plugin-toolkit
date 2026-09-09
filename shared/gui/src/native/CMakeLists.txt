cmake_minimum_required(VERSION 3.20)

set(LIB_NAME "toolkit-win-drag")
project(${LIB_NAME})

set(CMAKE_BUILD_TYPE Release)
set(CMAKE_CXX_STANDARD 17)

# ── JNI headers ───────────────────────────────────────────────────────────────
# Priority: -DJAVA_HOME from Gradle (preferred: uses the same JDK as Gradle),
# then JAVA_HOME env var, then CMake's find_package(JNI).
if(DEFINED JAVA_HOME)
    message(STATUS "JNI: Using JAVA_HOME from CMake argument: ${JAVA_HOME}")
    include_directories("${JAVA_HOME}/include")
    include_directories("${JAVA_HOME}/include/win32")
elseif(DEFINED ENV{JAVA_HOME})
    message(STATUS "JNI: Using JAVA_HOME from environment: $ENV{JAVA_HOME}")
    include_directories("$ENV{JAVA_HOME}/include")
    include_directories("$ENV{JAVA_HOME}/include/win32")
else()
    find_package(JNI REQUIRED)
    include_directories(${JNI_INCLUDE_DIRS})
    message(STATUS "JNI: Using find_package result: ${JNI_INCLUDE_DIRS}")
endif()

# ── Library target ────────────────────────────────────────────────────────────
add_library(${LIB_NAME} SHARED src/toolkit_drag.cpp)

# No "lib" prefix on Windows (we want toolkit-win-drag.dll, not libtoolkit-win-drag.dll)
set_target_properties(${LIB_NAME} PROPERTIES PREFIX "")

target_link_libraries(${LIB_NAME} user32 dwmapi)

message(STATUS "Will build: ${LIB_NAME}.dll")
