#include <jni.h>
#include <opencv2/opencv.hpp>
#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/features2d/features2d.hpp>
#include <iostream>
#include <fstream>
#include <sstream>

/* Header for class com_fi_uba_ar_detectors_NativeHandTrackingDetector */

#ifndef _Included_com_fi_uba_ar_detectors_NativeHandTrackingDetector
#define _Included_com_fi_uba_ar_detectors_NativeHandTrackingDetector
#ifdef __cplusplus
extern "C" {
#endif

using namespace cv;

JNIEXPORT jintArray JNICALL Java_com_fi_uba_ar_detectors_NativeHandTrackingDetector_detect(JNIEnv *, jobject, jlong);
JNIEXPORT jboolean JNICALL Java_com_fi_uba_ar_detectors_NativeHandTrackingDetector_init(JNIEnv *, jobject);
JNIEXPORT void JNICALL Java_com_fi_uba_ar_detectors_NativeHandTrackingDetector_sampleHandColor(JNIEnv *, jobject, jlong);

#ifdef __cplusplus
}
#endif
#endif
