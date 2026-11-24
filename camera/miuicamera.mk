#
# Copyright (C) 2026 The LineageOS Project
#
# SPDX-License-Identifier: Apache-2.0
#

MIUICAMERA_VENDOR_PATH := vendor/xiaomi/miuicamera-cepheus

ifneq ($(wildcard $(MIUICAMERA_VENDOR_PATH)/Android.bp),)

PRODUCT_SOONG_NAMESPACES += \
    $(MIUICAMERA_VENDOR_PATH)

# Permissions, sysconfig, public libraries
PRODUCT_COPY_FILES += \
    $(MIUICAMERA_VENDOR_PATH)/proprietary/system/etc/default-permissions/miuicamera-permissions.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/default-permissions/miuicamera-permissions.xml \
    $(MIUICAMERA_VENDOR_PATH)/proprietary/system/etc/permissions/privapp-permissions-miuicamera.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/permissions/privapp-permissions-miuicamera.xml \
    $(MIUICAMERA_VENDOR_PATH)/proprietary/system/etc/sysconfig/miuicamera-hiddenapi-package-whitelist.xml:$(TARGET_COPY_OUT_SYSTEM)/etc/sysconfig/miuicamera-hiddenapi-package-whitelist.xml \
    $(MIUICAMERA_VENDOR_PATH)/proprietary/system/etc/public.libraries-xiaomi.txt:$(TARGET_COPY_OUT_SYSTEM)/etc/public.libraries-xiaomi.txt

# System libraries (excluding legacy libhidltransport.so which is obsolete on Android 16)
PRODUCT_COPY_FILES += \
    $(MIUICAMERA_VENDOR_PATH)/proprietary/system/lib64/android.hardware.camera.common_1.0.so:$(TARGET_COPY_OUT_SYSTEM)/lib64/android.hardware.camera.common_1.0.so \
    $(MIUICAMERA_VENDOR_PATH)/proprietary/system/lib64/libSNPE.so:$(TARGET_COPY_OUT_SYSTEM)/lib64/libSNPE.so \
    $(MIUICAMERA_VENDOR_PATH)/proprietary/system/lib64/libarcsoft_single_chart_calibration.so:$(TARGET_COPY_OUT_SYSTEM)/lib64/libarcsoft_single_chart_calibration.so \
    $(MIUICAMERA_VENDOR_PATH)/proprietary/system/lib64/libcamera_algoup_jni.xiaomi.so:$(TARGET_COPY_OUT_SYSTEM)/lib64/libcamera_algoup_jni.xiaomi.so \
    $(MIUICAMERA_VENDOR_PATH)/proprietary/system/lib64/libcamera_mianode_jni.xiaomi.so:$(TARGET_COPY_OUT_SYSTEM)/lib64/libcamera_mianode_jni.xiaomi.so \
    $(MIUICAMERA_VENDOR_PATH)/proprietary/system/lib64/vendor.qti.hardware.camera.device@1.0.so:$(TARGET_COPY_OUT_SYSTEM)/lib64/vendor.qti.hardware.camera.device@1.0.so \
    $(MIUICAMERA_VENDOR_PATH)/proprietary/system/lib64/vendor.qti.hardware.camera.device@3.5.so:$(TARGET_COPY_OUT_SYSTEM)/lib64/vendor.qti.hardware.camera.device@3.5.so \
    $(MIUICAMERA_VENDOR_PATH)/proprietary/system/lib64/vendor.xiaomi.hardware.campostproc@1.0.so:$(TARGET_COPY_OUT_SYSTEM)/lib64/vendor.xiaomi.hardware.campostproc@1.0.so

# App private libraries
PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,*,$(MIUICAMERA_VENDOR_PATH)/proprietary/system/priv-app/MiuiCamera/lib,$(TARGET_COPY_OUT_SYSTEM)/priv-app/MiuiCamera/lib) \
    $(call find-copy-subdir-files,*,$(MIUICAMERA_VENDOR_PATH)/proprietary/system/priv-app/MiuiExtraPhoto/lib,$(TARGET_COPY_OUT_SYSTEM)/priv-app/MiuiExtraPhoto/lib)

# Leica / film filter LUTs and watermark fonts
PRODUCT_COPY_FILES += \
    $(call find-copy-subdir-files,*.png,$(MIUICAMERA_VENDOR_PATH)/proprietary/vendor/etc/camera,$(TARGET_COPY_OUT_VENDOR)/etc/camera) \
    $(call find-copy-subdir-files,*,$(MIUICAMERA_VENDOR_PATH)/proprietary/vendor/camera/fonts,$(TARGET_COPY_OUT_VENDOR)/camera/fonts)

# Apps, overlay, and JNI symlinks
PRODUCT_PACKAGES += \
    MiuiCamera \
    MiuiExtraPhoto \
    MiuiScanner \
    MiuiCameraOverlayMi9 \
    miui_camera_cepheus_libcamera_mianode_symlink \
    miui_camera_cepheus_campostproc_symlink

# Props
PRODUCT_PRODUCT_PROPERTIES += \
    ro.com.google.lens.oem_camera_package=com.android.camera

PRODUCT_SYSTEM_PROPERTIES += \
    persist.vendor.camera.privapp.list=com.android.camera \
    persist.sys.cam.skip_detach_image=true

endif
