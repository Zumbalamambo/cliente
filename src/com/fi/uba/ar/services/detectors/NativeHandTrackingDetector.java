package com.fi.uba.ar.services.detectors;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import com.fi.uba.ar.bus.events.DisplayImageDebugEvent;
import com.fi.uba.ar.controllers.HandGestureController;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.MatUtils;
import com.fi.uba.ar.views.HandDebugFragment;

import de.greenrobot.event.EventBus;

//TODO: implementar con JNI el tracking de mano y dedos!
public class NativeHandTrackingDetector {
	private static final String TAG = "NativeHandTrackingDetector";
	
	public static boolean initialized = false;
	
	public static boolean handColorSampled = false;
	
	public static boolean initialize() {
		if (!initialized)
			initialized = init();
		return initialized;
	}
		
	public native static boolean init();
	
	private static Mat convertMat(Mat m) {
		// XXX: la mat que nos llega desde vuforia esta en formato BGR
		// para que todo el codigo este acorde al original transformamos a RGBA
		Mat rgbaMat = new Mat();
		Imgproc.cvtColor(m, rgbaMat, Imgproc.COLOR_BGR2RGBA);
		return rgbaMat;
	}
	
	public static Mat detect(Mat mat) {
		//XXX: importante! si la mat no es "gray" y llega algo RGBA
		// entonces el la aplicacion crashea al usar el MDetector.detect de aruco
		//Mat rgbaMat = convertMat(mat);
		Mat rgbaMat = mat;
		CustomLog.d(TAG, "por realizar deteccion de mano con mat = " + rgbaMat);
		int[] result = detect(rgbaMat.getNativeObjAddr()); // devuelve un array con las coordenadas x,y
		// cada coordenada es un elemento por separado
		if (result != null) {
			HandDebugFragment hdf = HandDebugFragment.getInstance();
			if (hdf != null && result.length > 0) {
				CustomLog.d(TAG, "HandDebugFragment no es null");
				CustomLog.d(TAG, "se detectaron " + result.length / 2 + " dedos");
				List<android.graphics.Point> points = new ArrayList<android.graphics.Point>();
				
				StringBuilder sb = new StringBuilder("Dedos detectados - int arrays = ");
				for (int x=0; x < result.length; x++) {
					sb.append(result[x] + ", ");
				}
				CustomLog.d(TAG, sb.toString());
				
				for (int x=0; x < result.length; x+=2) {
					
					double width_factor = 1.77; // 2.2; // 1280 / 720 = 1.77
					double heigth_factor = 1.5; // 1.8; // 720 / 480 = 1.5
					
					android.graphics.Point pt = new android.graphics.Point(
													// esto es (x,y)
													// (int)Math.round(result[x] * width_factor), 
													// (int)Math.round(result[x+1]  * heigth_factor));
													// esto es invertido
													// como esta rotada vamos a probar usar las coordenadas x,y invertidas
													// x,y invertidos - ESTO ES LO CORRECTO! 
													(int)Math.round(result[x+1] * width_factor), 
													(int)Math.round(result[x]  * heigth_factor));
					
					// Original - parece que vuforia nos pasara la imagen como si estuviese en portrait mode y no vertical asi que 
					// no usamos x,y sino que la version invertida
					//android.graphics.Point pt = new android.graphics.Point((int)Math.round(p.x), (int)Math.round(p.y));
					CustomLog.d(TAG, "Dedo p = " + pt.x + ", " + pt.y);
					points.add(pt);
				}
				
				//XXX: determinar si no seria mejor usar el EventBus para despachar un evento
				// como algo FingersDetectedEvent con todos los puntos y hacer algo asi 
				//EventBus.getDefault().post(new FingersDetectedEvent(points));
				// quizas de esta forma logramos algo que sea mas asincronico y no se hace llamado 
				// directo desde este detector a la vista...
				hdf.addFingerTipPoints(points);
				//XXX: a modo de prueba mandamos todos los dedos como un touch event
				HandGestureController.getInstance().addFingerTipPoints(points);
			}
		}
		
//		Log.d(TAG, "despues de realizar deteccion - result = " + result);
//		Log.d(TAG, "detected_markers.size() = " + detected_markers.size());		
		//EventBus.getDefault().post(new DisplayImageDebugEvent(MatUtils.matToBitmap(rgbaMat)));
		return rgbaMat;
	}
	
	public native static int[] detect(long matAddr);
	
	public static boolean sampleHandColor(Mat mat) {
		//Mat rgbaMat = convertMat(mat);
		Mat rgbaMat = mat;
		CustomLog.d(TAG, "por realizar sample de color de la mano con mat = " + rgbaMat);
		sampleHandColor(rgbaMat.getNativeObjAddr());
		CustomLog.d(TAG, "sample de color de la mano LISTO con mat = " + rgbaMat);
		handColorSampled = true;
		return true;
	}

	public native static void sampleHandColor(long matAddr);
}
