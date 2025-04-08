cmake_minimum_required(VERSION 3.18.1)

project("SocketClientJNI")

add_library(socketclient
        SHARED
        socket_jni.c)

target_include_directories(socketclient PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/include
)

find_library(log-lib
        log)

target_link_libraries(socketclient
        ${log-lib}
)