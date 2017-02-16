/*===============================================================================
Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of QUALCOMM Incorporated, registered in the United States 
and other countries. Trademarks of QUALCOMM Incorporated are used with permission.
===============================================================================*/

package com.fi.uba.ar.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.imgproc.Imgproc;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;

import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.services.FrameService;
import com.fi.uba.ar.services.detectors.HandTrackingDetector;
import com.fi.uba.ar.services.detectors.NativeHandTrackingDetector;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.MatUtils;
import com.fi.uba.ar.views.HandDebugFragment;
import com.fi.uba.ar.views.VuforiaFragment;
import com.qualcomm.vuforia.Frame;
import com.qualcomm.vuforia.Matrix44F;
import com.qualcomm.vuforia.PIXEL_FORMAT;
import com.qualcomm.vuforia.Renderer;
import com.qualcomm.vuforia.State;
import com.qualcomm.vuforia.Tool;
import com.qualcomm.vuforia.TrackableResult;
import com.qualcomm.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.qualcomm.vuforia.Vuforia;

// The renderer class for the ImageTargetsBuilder sample. 
public class VuforiaRenderer implements GLSurfaceView.Renderer {
	private static final String LOGTAG = "VuforiaRenderer";

	VuforiaSession vuforiaAppSession;

	public boolean mIsActive = false;

	private int shaderProgramID;

	private int vertexHandle;

	private int normalHandle;

	private int textureCoordHandle;

	private int mvpMatrixHandle;

	private int texSampler2DHandle;

	private long _frameCount = 0;
	private long _timeLastSample = 0;
	private long FRAMERATE_SAMPLEINTERVAL_MS = 1000;
	private float _fps = 0;

	// Constants:
	static final float kObjectScale = 3.f;

	private static final String TAG = "VuforiaRenderer";

	// Reference to main activity
	private MainActivity mActivity;

	private VuforiaFragment mFragment;

	private boolean doMarkerScan = false;
	private boolean doColorSampling = false;
	private HandlerThread framesHandlerThread;
	private FrameService.FrameConsumerHandler framesHandler;

	// private Mat m;

	public VuforiaRenderer(VuforiaFragment fragment,
			VuforiaSession session) {
		mActivity = (MainActivity) fragment.getActivity();
		mFragment = fragment;
		vuforiaAppSession = session;

		// taken from
		// https://guides.codepath.com/android/Managing-Threads-and-Custom-Services
		// Create a new background thread for processing messages or runnables
		// sequentially
		// http://codetheory.in/android-handlers-runnables-loopers-messagequeue-handlerthread/
		// TODO: tenemos que revisar como terminar de forma correcta los threads
		framesHandlerThread = new HandlerThread("FramesHandlerThread");
		// Starts the background thread
		framesHandlerThread.start();
		// Create a handler attached to the HandlerThread's Looper
		framesHandler = new FrameService.FrameConsumerHandler(
				framesHandlerThread.getLooper());
	}

	// Called when the surface is created or recreated.
	/** Called when the surface is created or recreated.
	 * 
	 * We call initRendering() and also vuforiaAppSession.onSurfaceCreated()
	 * from here to (re)initialize rendering in its first use and after
	 * OpenGL context was lost (e.g. after onPause/onResume).
	 * 
	 * @see android.opengl.GLSurfaceView.Renderer#onSurfaceCreated(javax.microedition.khronos.opengles.GL10, javax.microedition.khronos.egl.EGLConfig)
	 */
	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");

		// Call function to initialize rendering:
		initRendering();

		// Call Vuforia function to (re)initialize rendering after first use
		// or after OpenGL ES context was lost (e.g. after onPause/onResume):
		vuforiaAppSession.onSurfaceCreated();
	}

	// Called when the surface changed size.
	/** Called when the surface changed size.
	 * We call the Vuforia function that handle surface size changes from here. 
	 * @see android.opengl.GLSurfaceView.Renderer#onSurfaceChanged(javax.microedition.khronos.opengles.GL10, int, int)
	 */
	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");

		// Call function to update rendering when render surface
		// parameters have changed:
		// mActivity.updateRendering();

		// Call Vuforia function to handle render surface size changes:
		vuforiaAppSession.onSurfaceChanged(width, height);
	}

	// Called to draw the current frame.
	@Override
	public void onDrawFrame(GL10 gl) {
		if (!mIsActive)
			return;
		// https://developer.vuforia.com/forum/qcar-api/frame-rate-my-application
		// Code to calculate fps
		_frameCount++;
		long now = System.currentTimeMillis();
		long delta = now - _timeLastSample;

		if (delta >= FRAMERATE_SAMPLEINTERVAL_MS) // FRAMERATE_SAMPLEINTERVAL_MS=1000;
		{
			_fps = _frameCount / (delta / 1000f);
			CustomLog.d(TAG, "FPS: " + Math.round(_fps));
			_timeLastSample = now;
			_frameCount = 0;
		}

		// Call our function to render content
		renderFrame();
	}

	public void doMarkerScanOnNextFrame() {
		doMarkerScan = true;
	}

	public void doColorSamplingOnNextFrame() {
		doColorSampling = true;
	}
	
	
	/*
	 * Este metodo es el que procesa cada frame de video
	 * que nos provee Vuforia
	 * XXX: DETECCION DE MANO!
	 */
	
	/** 
	 * M�todo principal para procesar cada frame de video provisto por Vuforia.
	 * 
	 * Toda operaci�n relizada aqu� repercute sobre la fluidez del video
	 * que el usuario observe. 
	 * Es llamado desde +onDrawFrame(), unicamente.
	 */
	private void renderFrame() {
		// Este es el metodo principal en el que vuforia nos permite procesar
		// cada frame de video
		// Algo que es muy importante es que cuanto mas operaciones realicemos
		// mas delay habra en el video que ve el usuario
		// Como ejemplo, sin ningun procesamiento pesado de frames obtenemos un
		// promedio
		// ~ 14/15 FPS pero si por ejemplo hacemos la deteccion de dedos en
		// forma nativa
		// el valor baja drasticamente a ~ 3 FPS lo que hace que el video se vea
		// muy lento

		// Clear color and depth buffer
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

		// Get the state from Vuforia and mark the beginning of a rendering
		// section
		State state = Renderer.getInstance().begin();

		// Explicitly render the Video Background
		Renderer.getInstance().drawVideoBackground();

		GLES20.glEnable(GLES20.GL_DEPTH_TEST);
		GLES20.glEnable(GLES20.GL_CULL_FACE);
		if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
			GLES20.glFrontFace(GLES20.GL_CW); // Front camera
		else
			GLES20.glFrontFace(GLES20.GL_CCW); // Back camera

		// XXX: resulta que no podemos trabajar con el Frame de vuforia fuera de
		// este bloque
		// Renderer.getInstance().begin(); y Renderer.getInstance().end()
		// ya que el uso del frame hace llamados a codigo nativo y nuestra app
		// crashea
		// Asi que estamos obligados a convertir de frame a Mat aca.
		Frame frame = null;
		Mat m = null;

		if (((MainActivity) mActivity).mBoundFrameService || doMarkerScan
				|| doColorSampling) {
			frame = state.getFrame();
			m = MatUtils.vuforiaFrameToMat(frame, false);
			// Mat m = MatUtils.vuforiaFrameToMat(frame, true); // grey
		}

		if (doColorSampling) {
			doColorSampling = false;

			// TODO: elegir que implementacion usar!! nativa o java?!

			// native implementation color sampling
			NativeHandTrackingDetector.sampleHandColor(m);

			// Java implementation color sampling
			HandTrackingDetector htd = MainApplication.getInstance()
					.getMainController().getHandTrackingDetector();
			if (htd != null) {
				htd.doColorSampling(m);
			}

		} else if (((MainActivity) mActivity).mBoundFrameService) {
			// tenemos que hacer tracking de la mano tambien enviando el frame
			// al servicio
			// procesamos los frames de manera asincronica en un servicio con un
			// pool de threads

			// TODO: pensar si no conviene para mejorar la performance
			// enviar el objeto Frame al servicio y no transformar a Mat y hacer
			// resize
			// y despues llamar a MatUtils.vuforiaFrameToMat en el servicio para
			// evitar cargar este loop que tiene que ser rapido
			// XXX: esto NO se puede! Ya lo probamos y crashea la app

			if (m != null) {
				// XXX: comentamos esto por el momento para hacer pruebas con
				// procesamiendo de imagen estatica
				// Para esto la idea era agregar un open file dialog para elegir
				// una imagen

				// para hacer testing con una imagen estatica lo implementamos
				// en el MainActivity.testHandDetection
				// Esto abre un file dialog para elegir una imagen
				// XXX: ESTO NO DEBE ESTAR COMENTADO PARA USO NORMAL
				// ((MainActivity) mActivity).mFrameService.enqueueFrame(m);

				// XXX: quizas la alternativa a usar el FrameService que tiene
				// mucho delay es implementar esto con un
				// HandlerThread
				// https://guides.codepath.com/android/Managing-Threads-and-Custom-Services
				// https://blog.nikitaog.me/2014/10/11/android-looper-handler-handlerthread-i/
				// https://blog.nikitaog.me/2014/10/18/android-looper-handler-handlerthread-ii/

				// XXX: estamos probando que pasa si usamos un HandlerThread en
				// lugar de un servicio en backgroud
				// TODO: mandamos un runnable al handler
				// Por algun motivo si usamos esto en lugar del servicio,
				// eventualmente la app muere por
				// OutOfMemory exception cuando se quiere convertir un frame de
				// vuforia a un Mat
				// El tema es que no se porque.. agregue unos mat.release() para
				// ver si con eso lo evitamos pero no
				// y quizas sea algo de los mensajes o los bundle que usamos con
				// byte arrays?
				// http://nemanjakovacevic.net/blog/english/2015/03/24/yet-another-post-on-serializable-vs-parcelable/
				// https://github.com/johncarl81/parceler

				// Tenemos 2 maneras posibles de ejecutar algo con el
				// HandlerThread (vamos a probar cada una)
				// 1 - Usando messages y el metodo handleMessage del Handler
				/*
				 * Message message = framesHandler.obtainMessage(); 
				 * Bundle b = new Bundle(); 
				 * int mat_height = m.height(); 
				 * b.putInt("height", mat_height); 
				 * int mat_width = m.width(); 
				 * b.putInt("width", mat_width); 
				 * b.putByteArray("mat", MatUtils.matToBytes(m));
				 * message.setData(b); 
				 * framesHandler.sendMessage(message);
				 */

				// 2 - haciendo un post de un task (runnable)
				// XXX: esta forma de procesar los frames en el runnable no
				// genera el crash por out of memory
				/*
				 * class FrameRunnable implements Runnable { 
				 * 	private Mat mat;
				 * 
				 * 	public FrameRunnable(Mat m) { mat = m.clone(); }
				 * 
				 * 	@Override public void run() { 
				 * 		Mat[] mats = new Mat[1];
				 * 		mats[0] = mat; // Deteccion de mano version Java
				 * 		FrameService.processFrame(mats); 
				 * 	} 
				 * }				 
				 * framesHandler.post(new FrameRunnable(m));
				 */

				// XXX: Despues de hacer pruebas, incluso con el HandleThread
				// desde aca
				// hay un delay gigante al detectar los dedos
				// parece que el thread en background se ejecuta mucho despues y
				// probablemente
				// son demasiados tasks en el looper porque cada frame crea un
				// task

				// XXX: en ambos casos tanto si se usa codigo nativo como java
				// en este loop
				// si hacemos la deteccion de mano aca, el video se corta mucho
				// y existe un delay que hace molesto el uso de la app
				// todo esto se debe a la carga de procesamiento que se le
				// agrega entre frame y frame!
				// Es demasiado lento como para ser usable. Definitivamente aca
				// no lo podemos hacer

				// XXX: para testear si hay mucho delay en el video haciendo la
				// deteccion de mano con codigo nativo
				// Despues de modificar el codigo para eliminar todas las
				// operaciones que dibujan
				// sobre la mat o dibujan en pantalla la performance mejoro
				// notablemente
				// Ahora el delay en el video se ve disminuido
				// Pero la deteccion de la mano no es buena y los dedos que se
				// muestran no son correctos
				// XXX: se hizo un fix en la forma en la que se pasaban las
				// coordenadas de los dedos de nativo a java
				// pero aun parece que las posiciones en la imagen no son
				// correctas
				
				// XXX: DETECCION DE MANO IMPLEMENTACION NATIVA
				Mat resMat = NativeHandTrackingDetector.detect(m);

				// XXX para testear que pasa si hacemos la deteccion de mano aca
				// con codigo java
				// XXX: DETECCION DE MANO IMPLEMENTACION JAVA
				/*
				 Mat[] mats = new Mat[1]; 
				 mats[0] = m;
				 FrameService.processFrame(mats);
				*/

				// XXX: Despues del testing a simple vista no logro determinar
				// si
				// alguna de las 2 implementaciones produce un delay menor
				// Parece ser que el delay es el mismo
				// El video se ve bastante cortado pero quizas es la unica forma
				// de poder tener
				// deteccion de mano en "tiempo real" y todavia falta la
				// deteccion de gestos
				// que eso probablemente tenga que ser con algun servicio y ya
				// nuevamente
				// entramos en el mundo de los delays :(

				// XXX: a modo de prueba lo mandamos ahora aca al streaming !!!
				// XXX: esto en realidad no lo tenemos que hacer aca sino luego
				// de que el se haya procesado cada frame en el codigo nativo

				// si el servicio de streaming esta activo tambien enviamos el
				// frame ahi
				/*
				 * 
				 * if (((MainActivity) mActivity).mBoundFrameStreamingService) {
				 * ((MainActivity)
				 * mActivity).mFrameStreamingService.enqueueFrame(m);
				 * 
				 * 
				 * //XXX: a modo de prueba vemos que pasa si mando bitmaps
				 * //((MainActivity)
				 * mActivity).mFrameStreamingService.sendFrame(
				 * MatUtils.vuforiaFrameToBitmap(frame)); }
				 */
			} else
				CustomLog.v(TAG,
						"no pude obtener un mat desde un vuforia frame");
		} else
			CustomLog.v(TAG, "mFrameService todavia no hizo el bind");

		if (doMarkerScan) {
			doMarkerScan = false;
			// Imgproc.cvtColor(m, m, Imgproc.COLOR_BGR5652GRAY);
			m = MatUtils.vuforiaFrameToMat(frame, true); // necesitamos la
															// imagen en gris
			if (m != null) {
				if (MainApplication.getInstance().getMainController().detectMarkers(m))
					mFragment.startTrackerMarker();
			}
		}

		// Render the RefFree UI elements depending on the current state
		// mActivity.refFreeFrame.render();
		mFragment.checkForNewTrackable();
		if (mFragment.isTrackingStarted()) {
			// CustomLog.d(LOGTAG,
			// "renderFrame() - state.getNumTrackableResults() = " +
			// state.getNumTrackableResults());
			// Did we find any trackables this frame?
			for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++) {
				// Get the trackable:
				TrackableResult trackableResult = state
						.getTrackableResult(tIdx);
				Matrix44F modelViewMatrix_Vuforia = Tool
						.convertPose2GLMatrix(trackableResult.getPose());
				float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();

				// TODO: verificar si no es que vuforia por algun motivo marca
				// como que hay un trackableResult cuando no hay marker en la
				// pantall
				// Creo que se esta llegando aca aun cuando no hay un marker en
				// la imagen que toma vuforia
				// Esto solo me paso en el emulador!

				// XXX: esto toma un objeto AR y lo actualiza con la info de los
				// trackers
				MainApplication.getInstance().getMainController()
						.updateObjectARPos(modelViewMatrix);

				// float[] modelViewProjection = new float[16];
				// Matrix.translateM(modelViewMatrix, 0, 0.0f, 0.0f,
				// kObjectScale);
				// Matrix.scaleM(modelViewMatrix, 0, kObjectScale, kObjectScale,
				// kObjectScale);
				// Matrix.multiplyMM(modelViewProjection, 0, vuforiaAppSession
				// .getProjectionMatrix().getData(), 0, modelViewMatrix, 0);
				//
				// GLES20.glUseProgram(shaderProgramID);
				//
				// // GLES20.glVertexAttribPointer(vertexHandle, 3,
				// GLES20.GL_FLOAT,
				// // false, 0, mTeapot.getVertices());
				// // GLES20.glVertexAttribPointer(normalHandle, 3,
				// GLES20.GL_FLOAT,
				// // false, 0, mTeapot.getNormals());
				// // GLES20.glVertexAttribPointer(textureCoordHandle, 2,
				// // GLES20.GL_FLOAT, false, 0, mTeapot.getTexCoords());
				//
				// GLES20.glEnableVertexAttribArray(vertexHandle);
				// GLES20.glEnableVertexAttribArray(normalHandle);
				// GLES20.glEnableVertexAttribArray(textureCoordHandle);
				//
				// GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
				// // GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
				// // mTextures.get(0).mTextureID[0]);
				// GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
				// modelViewProjection, 0);
				// GLES20.glUniform1i(texSampler2DHandle, 0);
				// // GLES20.glDrawElements(GLES20.GL_TRIANGLES,
				// // mTeapot.getNumObjectIndex(), GLES20.GL_UNSIGNED_SHORT,
				// // mTeapot.getIndices());
				//
				// GLES20.glDisableVertexAttribArray(vertexHandle);
				// GLES20.glDisableVertexAttribArray(normalHandle);
				// GLES20.glDisableVertexAttribArray(textureCoordHandle);
				//
				// //
				// SampleUtils.checkGLError("UserDefinedTargets renderFrame");
			}
		}
		GLES20.glDisable(GLES20.GL_DEPTH_TEST);

		Renderer.getInstance().end();

		if (m != null)
			m.release(); // XXX: es necesario esto para librerar memoria?

	}

	private void initRendering() {
		Log.d(LOGTAG, "initRendering");

		// mTeapot = new Teapot();

		// Define clear color
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
				: 1.0f);

		// Now generate the OpenGL texture objects and add settings
		// for (Texture t : mTextures)
		// {
		// GLES20.glGenTextures(1, t.mTextureID, 0);
		// GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
		// GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
		// GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
		// GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
		// GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
		// GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
		// t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
		// GLES20.GL_UNSIGNED_BYTE, t.mData);
		// }

		// shaderProgramID = SampleUtils.createProgramFromShaderSrc(
		// CubeShaders.CUBE_MESH_VERTEX_SHADER,
		// CubeShaders.CUBE_MESH_FRAGMENT_SHADER);

		vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
				"vertexPosition");
		normalHandle = GLES20.glGetAttribLocation(shaderProgramID,
				"vertexNormal");
		textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
				"vertexTexCoord");
		mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
				"modelViewProjectionMatrix");
		texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID,
				"texSampler2D");
	}

	// public void setTextures(Vector<Texture> textures)
	// {
	// mTextures = textures;
	//
	// }

}
