# http://www.netmite.com/android/mydroid/1.6/development/ndk/docs/ANDROID-MK.TXT
LOCAL_PATH := $(call my-dir)
MY_LOCAL_PATH := $(call my-dir)
ABSOLUTE_LOCAL_PATH := $(abspath $(call my-dir))

# -------------------------------------------
# incluimos 3rd party native libs

include $(CLEAR_VARS)
LOCAL_MODULE := libiconv
LOCAL_SRC_FILES := prebuilt/libiconv.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libzbarjni
LOCAL_SRC_FILES := $(ABSOLUTE_LOCAL_PATH)/prebuilt/libzbarjni.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := libVuforia
LOCAL_SRC_FILES := $(ABSOLUTE_LOCAL_PATH)/prebuilt/libVuforia.so
include $(PREBUILT_SHARED_LIBRARY)


#-------------------------------------------
# compila iconv como shared lib
#include $(ABSOLUTE_LOCAL_PATH)/iconv/Android.mk

# Creo que podriamos hacer esta llamada y asi compila todos los .mk que tengamos en subdirs
#include $(call all-subdir-makefiles) # esto no me funciono..

# -------------------------------------------
# compilamos nuestras libs nativas
# limpiamos las variables que otro .mk haya definido
include $(CLEAR_VARS) # esto no limpia LOCAL_PATH

#OPENCV_CAMERA_MODULES:=off
#OPENCV_INSTALL_MODULES:=off
#OPENCV_LIB_TYPE:=SHARED
include $(OPENCV_HOME)/sdk/native/jni/OpenCV.mk
#include /home/esteban/Facultad/TP_Profesional/AR/OpenCV-2.4.9-android-sdk/sdk/native/jni/OpenCV.mk

# http://stackoverflow.com/questions/8980284/android-mk-include-all-cpp-files
#FILE_LIST := $(wildcard $(LOCAL_PATH)/*.cpp)
#LOCAL_SRC_FILES := $(FILE_LIST:$(LOCAL_PATH)/%=%)
# Otro truco similar para incluir muchos archivos se puede ver en:
# https://android.googlesource.com/platform/dalvik/+/eclair-release/libcore/Android.mk

# http://stackoverflow.com/questions/6942730/android-ndk-how-to-include-android-mk-into-another-android-mk
# http://stackoverflow.com/questions/11722777/how-to-include-android-mk-in-another-makefile

# Hay muchos problemas con la variable LOCAL_PATH que termina apuntando a cualqueir lado
# entonces nos aseguramos de referenciar con full path asi evitamos problemas

# Includes
LOCAL_C_INCLUDES += $(LOCAL_PATH) # importante usar full path y += asi no le quitamos los headers agregados por OpenCV.mk
LOCAL_C_INCLUDES += coffeecatch/

# coffeecatch
#LOCAL_SRC_FILES += coffeecatch/coffeecatch.c 
#LOCAL_SRC_FILES += coffeecatch/coffeejni.c

# FiubaAR
LOCAL_SRC_FILES  += fiubaar_globals.cpp
LOCAL_SRC_FILES  += com_fi_uba_ar_services_detectors_NativeHandTrackingDetector.cpp
LOCAL_SRC_FILES  += handGesture.cpp
LOCAL_SRC_FILES  += myImage.cpp
LOCAL_SRC_FILES  += main.cpp
LOCAL_SRC_FILES  += roi.cpp
LOCAL_SRC_FILES  += jni_process.cpp

LOCAL_SRC_FILES  += onload.cpp
LOCAL_SRC_FILES  += com_fi_uba_ar_services_detectors_HandTrackingDetector.cpp
LOCAL_SRC_FILES  += train.cpp
LOCAL_SRC_FILES  += predict.cpp
LOCAL_SRC_FILES  += svm/svm-train.cpp
LOCAL_SRC_FILES  += svm/svm-predict.cpp
LOCAL_SRC_FILES  += svm/svm.cpp

LOCAL_LDLIBS     += -llog -ldl
LOCAL_CFLAGS 	 := -funwind-tables -Wl,--no-merge-exidx-entries -g3
LOCAL_MODULE     := fiubaar_native

# incluimos las otras shared libs que sean necesarias asi no las borra al hacer un clean y compile
#LOCAL_SHARED_LIBRARIES += libiconv

include $(BUILD_SHARED_LIBRARY)
# -------------------------------------------

