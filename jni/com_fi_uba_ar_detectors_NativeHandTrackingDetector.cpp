#include <jni.h>
#include <stdio.h>
#include "com_fi_uba_ar_detectors_NativeHandTrackingDetector.hpp"
#include "native_logcat.h"
//#include "coffeecatch/coffeecatch.h"
//#include "coffeecatch/coffeejni.h"
#include "main.hpp"

// dato util: http://answers.opencv.org/question/11021/confusion-between-opencv4android-and-c-data-types/

// http://stackoverflow.com/questions/1610045/how-to-return-an-array-from-jni-to-java
JNIEXPORT jintArray JNICALL Java_com_fi_uba_ar_detectors_NativeHandTrackingDetector_detect(
		JNIEnv *env, jobject thiz, jlong matAddr) {

	std::stringstream ss;
	jintArray result = NULL;
	jclass clazz;
	try {
		Mat& image = *(Mat*) matAddr;

		/** Protected function stub. **/
		/* Try to call 'dangerous function', and raise proper Java Error upon fatal error (SEGV, etc.) */
		//COFFEE_TRY_JNI(env, clazz = (*env).FindClass("demo"));
		bool fingersDetected = false;
		//COFFEE_TRY_JNI(env, fingersDetected = test(image));
		fingersDetected = test(image);
		if (fingersDetected) {
			// una vez detectados los dedos tenemos un vector y tenemos que pasarselos a java

			vector<Point> fingerTips = getFingetTipPoints();
			Point p;
			int size = fingerTips.size() * 2;

			if (size == 0) // algunas veces se dio este caso
				return NULL;

			if (size == 2) { // 1 unico dedo
				/*
					Por algun motivo parece que a veces se detecta un unico dedo
					con coordenadas (0, 0) lo que no tiene sentido...
					si vemos eso, entonces simplemente lo ignoramos como si no
					hubiera detectado ninguno
				*/
				p = fingerTips[0];
				if (p.x == 0 && p.y == 0) {
					ss.clear();
					ss.str("");
					ss << "Se detecto un dedo en (0, 0), ignorandolo..." << std::endl;
					const std::string tmpex = ss.str();
					LOGD("NativeHandTrackingDetector", tmpex.c_str());
					return NULL;
				}
			}

			result = (*env).NewIntArray(size);
			if (result == NULL) {
			     return NULL;
			}

			ss.clear();
			ss.str("");
			ss << "Se detectaron " << fingerTips.size() << " dedos y se van a meter en un jintarray" << std::endl;

			// fill a temp structure to use to populate the java int array
			jint finger_points[10]; // 10 porque son 2 coordenadas por cada dedo, maximo 5 dedos


			for (int i = 0; i < fingerTips.size(); i++) {
				p = fingerTips[i];
				ss << "Point [" << i << "] = (" << p.x <<", " << p.y << ")" << std::endl;
				// guardamos coordenadas del dedo en un array que nos pasaron
				finger_points[2*i] = p.x;
				finger_points[(2*i)+1] = p.y;
			}

			const std::string tmpex = ss.str();
			LOGD("NativeHandTrackingDetector", tmpex.c_str());

			// move from the temp structure to the java structure
			(*env).SetIntArrayRegion(result, 0, size, finger_points);
			return result;
		}
		//XXX: por algun motivo si tratamos de hacer el FindClass de algo que sabemos que no existe, entonces
		// sin importar si usamos coffee para capturar la excepcion o si usamos solo el try/catch
		// la app crashea con un AndroidRuntime exception al no encontrar la clase!

	} catch(Exception ex) {
		LOGD("NativeHandTrackingDetector", "hubo una excepcion al detectar una mano con NativeHandTrackingDetector_detect");
		ss.clear();
		ss.str("");
		ss << "Exception = " << ex.msg;
		const std::string tmpex = ss.str();
		LOGE("NativeHandTrackingDetector", tmpex.c_str());
		return NULL;
	}

	//TODO: pasar los markers detectados a Java de alguna manera
	// procesamos cada marker detectado y lo agregamos al array list
	// previamente convirtiendo de la clase Marker en C de aruco a nuestra
	// clase Marker de java
	// Idea basica tomada de http://stackoverflow.com/questions/7776800/convert-vector-to-jobject-in-c-jni
	// http://thebreakfastpost.com/2012/03/06/wrapping-a-c-library-with-jni-part-4/
	// http://pickerwengs.blogspot.com.ar/2011/12/android-programming-objects-between.html
	//jclass array_list_clazz = (*env).FindClass("java/util/ArrayList");
	return NULL;
}

JNIEXPORT jboolean JNICALL Java_com_fi_uba_ar_detectors_NativeHandTrackingDetector_init(JNIEnv *env, jobject thiz) {
	init();
	return true;
}

JNIEXPORT void JNICALL Java_com_fi_uba_ar_detectors_NativeHandTrackingDetector_sampleHandColor(JNIEnv *env, jobject thiz, jlong matAddr) {
	Mat& image = *(Mat*) matAddr;
	//COFFEE_TRY_JNI(env, doHandColorSample(image));
	doHandColorSample(image);
	int values[7][3];
	getHandAverageColorValues(values);
	std::stringstream ss;
	ss.str("");

	ss << "Average Hand Color values = [ " ;
	for (int i = 0; i < 7 ; i++) {
		ss << "( ";
		for (int j = 0; j < 3 ; j++)
			ss << values[i][j] << ", ";
		ss << " ), ";
	}
	ss << " ]";

	const std::string tmpex = ss.str();
	LOGD("NativeHandTrackingDetector", tmpex.c_str());

}
