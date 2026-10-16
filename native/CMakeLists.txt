cmake_minimum_required(VERSION 3.22)
project(osrsrl CXX ASM)

set(CMAKE_CXX_STANDARD 20)
set(IMGUI_DIR ${CMAKE_SOURCE_DIR}/third_party/imgui)

add_library(osrsrl SHARED
    src/main.cpp
    src/agent.cpp
    src/client_thread.cpp
    src/game.cpp
    src/gl_trace.cpp
    src/got_hook.cpp
    src/inline_hook.cpp
    src/host_blob.S
    src/jni_bridge.cpp
    src/net_trace.cpp
    src/overlay.cpp
    src/script_hooks.cpp
    src/npc_probe.cpp
    src/frame_capture.cpp
    src/crash_log.cpp
    src/script_stubs.S
    ${IMGUI_DIR}/imgui.cpp
    ${IMGUI_DIR}/imgui_draw.cpp
    ${IMGUI_DIR}/imgui_tables.cpp
    ${IMGUI_DIR}/imgui_widgets.cpp
    ${IMGUI_DIR}/backends/imgui_impl_opengl3.cpp
)
# Java plugin host, built by host/build.sh.
set(HOST_JAR ${CMAKE_SOURCE_DIR}/../re/host/host.jar CACHE FILEPATH "Plugin host dex jar to embed")
set_source_files_properties(src/host_blob.S PROPERTIES COMPILE_DEFINITIONS "HOST_JAR=\"${HOST_JAR}\"" OBJECT_DEPENDS ${HOST_JAR})
target_include_directories(osrsrl PRIVATE ${IMGUI_DIR} ${IMGUI_DIR}/backends)
target_compile_definitions(osrsrl PRIVATE IMGUI_IMPL_OPENGL_ES3 IMGUI_DISABLE_DEMO_WINDOWS)
target_compile_options(osrsrl PRIVATE -fvisibility=hidden -Wall)
# 16 KB page alignment (required on Android 15+ devices with 16K pages).
target_link_options(osrsrl PRIVATE -Wl,-z,max-page-size=16384 -Wl,--gc-sections -s)
target_link_libraries(osrsrl PRIVATE EGL GLESv3 log)
