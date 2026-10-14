#pragma once
#include <android/log.h>

#define OSRSRL_TAG "osrsrl"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, OSRSRL_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, OSRSRL_TAG, __VA_ARGS__)
