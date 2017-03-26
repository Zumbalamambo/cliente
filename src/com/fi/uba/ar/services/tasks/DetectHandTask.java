package com.fi.uba.ar.services.tasks;

import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.controllers.MainActivity;
import com.fi.uba.ar.detectors.NativeHandTrackingDetector;
import com.fi.uba.ar.model.JavaCameraFrame;
import com.fi.uba.ar.utils.CommonUtils;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.MatUtils;
import com.fi.uba.ar.utils.Constants;
import de.greenrobot.event.EventBus;
import org.opencv.core.Mat;

public class DetectHandTask implements Runnable {
	private static final String TAG = "DetectHandTask";

	private Mat[] mats;
	private byte[] matBytes;
	private int matWidth;
	private int matHeigth;
	private boolean sampleHandColor;

	public DetectHandTask(Mat[] mats, boolean doSampleHandColor) {
		this.mats = mats;
		this.sampleHandColor = doSampleHandColor;
		//this.matWidth = mat.width();
		//this.matHeigth = mat.height();
		// this.matBytes = MatUtils.matToBytes(mat);
		CustomLog.d(TAG, "task creado con mats size = " + mats.length);
	}

	public DetectHandTask(byte[] matBytes, int width, int height) {
		this.matBytes = matBytes;
		this.matWidth = width;
		this.matHeigth = height;
		this.mats = new Mat[0];
		// Log.d(TAG, "task creado con matBytes");
	}

	@Override
	public void run() {		
		MainActivity mActivity = MainApplication.getInstance().getMainActivity();
		//TODO: implementar la logica necesaria para hacer la deteccion de manos con codigo nativo
		for (Mat mat: mats) {			
			// los mat que recibimos ya tienen formato BGR, es decir que son a color
			// no creo que sea necesario usar la clase JavaCameraFrame que hace una transformacion yuv a rgba
			boolean found = false;
			//JavaCameraFrame frame = new JavaCameraFrame(mat, mat.width(), mat.height());			
			//NativeHandTrackingDetector.detect(frame.rgba()); // parece ser que si no le pasamos un mat en RGBA hay errores en llamadas a cvtColor
			Mat resMat = NativeHandTrackingDetector.detect(mat); 
			
			//TODO: hay que saber cuando se haya detectado algo y hacer algo al respecto!
			// que se detecto? un gesto? un movimiento? y procesar la accion acorde
			if (found)
				sendDrawFrame();
			
			//XXX: es necesario hacer release de todos los mats creados?
			//frame.release();
			//mat.release();
			
			// enviamos el frame al servicio de streaming ya que si fue modificada y 
			// se dibujo un contorno a la mano lo mostramos
			if (mActivity.mBoundFrameStreamingService) {
				mActivity.mFrameStreamingService.enqueueFrame(resMat);
			}
		}
	}

	public void sendDrawFrame() {

		//EventBus.getDefault().post(new MarkerDetectedEvent(m));
	}

}
