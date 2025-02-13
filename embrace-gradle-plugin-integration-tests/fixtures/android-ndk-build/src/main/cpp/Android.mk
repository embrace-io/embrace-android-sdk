LOCAL_PATH := $(call my-dir)

# Configuration for emb-crisps library
include $(CLEAR_VARS)
LOCAL_MODULE := emb-crisps
LOCAL_SRC_FILES := crisps.c
include $(BUILD_SHARED_LIBRARY)

# Configuration for emb-donuts library
include $(CLEAR_VARS)
LOCAL_MODULE := emb-donuts
LOCAL_SRC_FILES := donuts.c
include $(BUILD_SHARED_LIBRARY)
