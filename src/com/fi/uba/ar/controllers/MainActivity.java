package com.fi.uba.ar.controllers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.core.Mat;
import org.opencv.highgui.Highgui;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.R;
import com.fi.uba.ar.bus.events.DisplayImageDebugEvent;
import com.fi.uba.ar.engine3d.LoadModelFragment;
import com.fi.uba.ar.services.FrameService;
//import com.fi.uba.ar.services.FrameService.LocalBinder;
import com.fi.uba.ar.services.FrameStreamingService;

import com.fi.uba.ar.services.detectors.HandTrackingDetector;
import com.fi.uba.ar.services.detectors.MarkerDetector;
import com.fi.uba.ar.services.detectors.NativeHandTrackingDetector;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.FileUtils;
import com.fi.uba.ar.views.DebugFragment;
import com.fi.uba.ar.views.HandDebugFragment;
import com.fi.uba.ar.views.SettingsFragment;
import com.fi.uba.ar.views.SimpleFileDialog;
import com.fi.uba.ar.views.VuforiaFragment;
import com.fi.uba.ar.views.VuforiaGLView;

import de.greenrobot.event.EventBus;

public class MainActivity extends Activity {

	private class OpenCVLoaderCallback extends BaseLoaderCallback {
		// ver de manejar todos los casos por ejemplo que no tiene la version
		// requerida y el usuario no acepta la instalacion
		// http://docs.opencv.org/platforms/android/service/doc/UseCases.html

		private boolean openCVLoaded = false;

		public OpenCVLoaderCallback(Context AppContext) {
			super(AppContext);
		}

		@Override
		public void onManagerConnected(int status) {
			CustomLog.i(TAG, "onManagerConnected called");

			switch (status) {
			case LoaderCallbackInterface.SUCCESS: {
				CustomLog.i(TAG, "OpenCV loaded successfully");

				// Cargamos esta libreria nativa aca porque hace uso de OpenCV
				System.loadLibrary("fiubaar_native");

				NativeHandTrackingDetector.initialize();

				// hacemos la creacion del MarkerDetector aca porque depende de
				// que opencv haya sido cargado
				((MainApplication) getApplication()).getMainController()
						.setMarkerDetector(new MarkerDetector());

				// recien cuando se haya cargado opencv podemos crear el
				// detector
				((MainApplication) getApplication()).getMainController()
						.createHandTrackerDetector();

				this.openCVLoaded = true;
			}
				break;
			default: {
				super.onManagerConnected(status);
			}
				break;
			}
		}

		public boolean isOpenCVLoaded() {
			return this.openCVLoaded;
		}

	}

	private class FrameServiceConnection implements ServiceConnection {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			CustomLog.d("FrameServiceConnection", "onServiceConnected llamado");
			// We've bound to LocalService, cast the IBinder and get
			// LocalService instance
			FrameService.LocalBinder binder = (FrameService.LocalBinder) service;
			mFrameService = (FrameService) binder.getService();
			mBoundFrameService = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mBoundFrameService = false;
		}

	}

	// XXX: a mi gusto esto es repetir mucho codigo :/
	private class FrameStreamingServiceConnection implements ServiceConnection {

		@Override
		public void onServiceConnected(ComponentName className, IBinder service) {
			CustomLog.e("FrameStreamingServiceConnection",
					"onServiceConnected llamado");
			// We've bound to LocalService, cast the IBinder and get
			// LocalService instance
			FrameStreamingService.LocalBinder binder = (FrameStreamingService.LocalBinder) service;
			mFrameStreamingService = (FrameStreamingService) binder
					.getService();
			mBoundFrameStreamingService = true;
		}

		@Override
		public void onServiceDisconnected(ComponentName arg0) {
			mBoundFrameStreamingService = false;
		}

	}

	static enum Fragment_action {
		ADD, REPLACE
	}

	protected static final String TAG = "MainActivity";

	public FrameService mFrameService;
	public boolean mBoundFrameService;

	public FrameStreamingService mFrameStreamingService;
	public boolean mBoundFrameStreamingService;

	private Fragment mainFragment = null;
	private Fragment debugFragment = null;

	private boolean calibrationRun = false;

	// XXX: revisar... nos falta pasar esto aca? o lo dejamos como etsa ahora
	// funcionando
	private boolean vuforiaStarted = false;

	private VuforiaSession vuforiaAppSession;

	// Our OpenGL view:
	private VuforiaGLView mGlView;

	// Our renderer:
	private VuforiaRenderer mRenderer;

	private OpenCVLoaderCallback mLoaderCallback = new OpenCVLoaderCallback(
			this);

	private FrameServiceConnection mFrameServiceConnection = new FrameServiceConnection();

	private FrameStreamingServiceConnection mFrameStreamingServiceConnection = new FrameStreamingServiceConnection();

	private boolean mIsDroidDevice;

	private MainActivity sInstance;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		CustomLog.i(TAG, "called onCreate");

		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.activity_main);

		((MainApplication) getApplication()).registerMainActivity(this);
		//XXX: en el refactor por el momento  no necesitamos usar el bus
		//EventBus.getDefault().register(this);
		sInstance = this;

		CustomLog.i(
				TAG,
				"openCV Manager loaded: "
						+ this.mLoaderCallback.isOpenCVLoaded());
		// cargamos librerias externas necesarias primero
		loadExternalLibraries();
		// vuforiaAppSession = new VuforiaSession(this);
		// vuforiaAppSession.initAR(this,
		// ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		// mIsDroidDevice =
		// android.os.Build.MODEL.toLowerCase().startsWith("droid");

		// setupInternalFiles();
		// this.mainFragment = new NativeCameraFragment();
		// setMainContainerContent(new NativeCameraFragment(),
		// Fragment_action.REPLACE);
		this.mainFragment = new VuforiaFragment();
		// setMainContainerContent(this.mainFragment, Fragment_action.ADD);
		setMainContainerContent(this.mainFragment, Fragment_action.REPLACE);

		startServices();

		// TODO: probablemente sea una buena idea tener un LoadModelFragment ya
		// activo que cargue el objeto de loading
		// y lo agregue en la escena pero en hide y al detectar un marker lo
		// hacemos visible
		// Probablemente sea mas performante largarlo desde el inicio y tenerlo
		// listo asi se muestra bien rapido el loading
		// Despues hay que contemplar la logica de mostrar y ocultar el loading
		// y ver que
		// cuando hacemos "atras" no nos borre este fragment del stack! sino se
		// rompe todo!
		
		CustomLog.i(TAG, "onCreate - screen orientation = " + getScreenOrientation());

	}

	public void startServices() {

		Thread t = new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				CustomLog.i(TAG, "por hacer bind a los servicios");
				// Arrancamos los servicios como "Bound"

				// CustomLog.i(TAG, "FrameService binding...(BEFORE)");
				bindService(new Intent(sInstance, FrameService.class),
						mFrameServiceConnection, Context.BIND_AUTO_CREATE);
				// CustomLog.i(TAG, "FrameService binding...(AFTER)");

				// CustomLog.i(TAG, "FrameStreamingService binding...(BEFORE)");
				//XXX: por el momento no necesitamos el FrameStreamingService ya
				// que fue implementado para facilitar el debugging de la deteccion de mano
				/*
				bindService(new Intent(sInstance, FrameStreamingService.class),
						mFrameStreamingServiceConnection,
						Context.BIND_AUTO_CREATE);
				*/
				// CustomLog.i(TAG, "FrameStreamingService binding...(AFTER)");
			}
		});

		t.start();

	}

	public void stopServices() {
		// Unbind from the FrameService
		if (mBoundFrameService) {
			unbindService(mFrameServiceConnection);
			mBoundFrameService = false;
		}
		// unbind frame streaming service
		if (mBoundFrameStreamingService) {
			unbindService(mFrameStreamingServiceConnection);
			mBoundFrameStreamingService = false;
		}
	}

	public boolean isOpenCVLoaded() {
		return this.mLoaderCallback.isOpenCVLoaded();
	}

	@Override
	protected void onDestroy() {
		MainApplication.getInstance().unregisterMainActivity(this);
		//XXX: en el refactor por el momento  no necesitamos usar el bus
		//EventBus.getDefault().unregister(this);
		super.onDestroy();
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	@Override
	protected void onStop() {
		super.onStop();
		stopServices();
	}

	@Override
	public void onResume() {
		// CustomLog.w(TAG, "onResume llamado!!!!!!!");
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this,
				mLoaderCallback);
		// launchMainFragment();
		// if (!vuforiaStarted) {
		// Intent intent = new Intent(this, UserDefinedTargets.class);
		// startActivity(intent);
		// vuforiaStarted = true;
		// }

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			// display the SettingsFragment and let the user configure things
			try {
				// TODO: aun cuando ponemos el fragment por arriba y no tiene
				// fondo transparente
				// si hay un objeto 3D activo, este se ve y queda la imagen por
				// arriba de los settings
				// Quizas deberiamos hacer algun tipo de hide del objeto y
				// despues cuando nos vamos del Fragment
				// de settings volver a mostrar el objeto 3D....
				// El tema es donde lo hacemos??
				final Fragment fragment = new SettingsFragment();
				setMainContainerContent(fragment, Fragment_action.ADD);
			} catch (Exception e) {
				e.printStackTrace();
			}
			// MainApplication.getInstance().getConfigManager().dumpSettings();
			return true;
		} else if (id == R.id.action_dump_debug_info) {
			MainApplication mApp = (MainApplication) getApplication();
			// XXX: este disenio es muy feo y no desacoplado porqeu la actividad
			// usa al objeto directamente
			// Esto se supone que es solo a modo de testing y debug
			if (mApp.getMainController().getActiveObjectAR() != null)
				mApp.getMainController().getActiveObjectAR().dumpObject3DDebugInfo();
			
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/*
	@Deprecated
	// XXX: este metodo lo usabamos antes cuando haciamos todo con opencv y no
	// con vuforia
	private void launchMainFragment() {
		MainApplication mApp = (MainApplication) getApplication();
		// hay que verificar si la camara fue calibrada o no
		if (!mApp.getMainController().isCalibrated() && !calibrationRun) {
			launchCalibrationActivity(null);
		} else {
			if (!mApp.getMainController().isCalibrated() && calibrationRun) {
				Toast toast = Toast.makeText(
						getApplicationContext(),
						getResources().getString(
								R.string.calibration_run_failed),
						Toast.LENGTH_LONG);
				toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
				toast.show();
			}
			// por defecto creamos el fragment de la camara con puro OpenCV
			// setMainContainerContent(new OpenCVCameraFragment(),
			// Fragment_action.REPLACE);

			// Implementacion personalizada usando camara nativa y port
			// modificado de
			// clases de opencv modificadas con lo minimo necesario para mejorar
			// FPS
			if (this.mainFragment == null)
				this.mainFragment = new NativeCameraFragment();
			setMainContainerContent(this.mainFragment, Fragment_action.REPLACE);
		}

		mApp.registerMainActivity(this);

	}
	*/

	public void launch3DFragment(View v) {
		// setTitle("Loading OBJ with 3D engine");
		//
		// try {
		// final Fragment fragment = new LoadModelFragment();
		// setMainContainerContent(fragment, Fragment_action.ADD);
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		MainApplication.getInstance().getMainController()
				.createObjectAR("TEST");
	}

	public void launchDebugFragment(View v) {

		try {
			if (debugFragment == null) {
				setTitle("Debug View");
				// debugFragment = new DebugFragment();
				// debugFragment = new ImageFragment();
				debugFragment = new HandDebugFragment();
				setMainContainerContent(debugFragment, Fragment_action.ADD);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	public void launchNativeCamera(View v) {
		setTitle("Launching Native Camera Fragment Test");

		try {
			final Fragment fragment = new NativeCameraFragment();
			setMainContainerContent(fragment, Fragment_action.REPLACE);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	*/

	// Esto se llama cuando se aprieta el boton para escanear el codigo marker
	public void captureMarker(View v) {
		((VuforiaFragment) this.mainFragment).scanForMarker();
	}

	public void sampleHandColor(View v) {
		// setear flag indicando que hay que hacer sample de la mano en el
		// proximo frame que se le pase al servicio
		// mFrameService.setDoSampleHandColor();

		// XXX: Esto es solo para testing
		// siempre que hacemos el sampling nos aseguramos que el debug view este
		// activo
		launchDebugFragment(v); // TODO: hay que comentar o sacar esta linea
								// para release

		HandTrackingDetector htd = ((MainApplication) getApplication())
				.getMainController().getHandTrackingDetector();
		if (htd != null) {
			if (htd.isColorSamplingDone())
				htd.nextState();
			else
				((VuforiaFragment) this.mainFragment)
						.doColorSampling();
		}
	}

	String m_chosen;

	// XXX: para testear la deteccion de la mano con una imagen estatica
	// y poder debuggear si lo necesitamos
	public void testHandDetection(View v) {
		// TODO: agregamos un dialogo para poder elegir un archivo de la tarjeta
		// sd?
		// esta es otra alternativa simple
		// http://www.scorchworks.com/Blog/simple-file-dialog-for-android-applications/
		CustomLog.e("TestManos",
				"Testing de deteccion de mano con imagen estatica");
		SimpleFileDialog FileOpenDialog = new SimpleFileDialog(
				MainActivity.this, "FileOpen",
				new SimpleFileDialog.SimpleFileDialogListener() {
					@Override
					public void onChosenDir(String chosenDir) {
						// The code in this function will be executed when the
						// dialog OK button is pushed
						m_chosen = chosenDir;
						Toast.makeText(MainActivity.this,
								"Chosen FileOpenDialog File: " + m_chosen,
								Toast.LENGTH_LONG).show();

						// Si se eligio un archivo podemos hacer el pasaje de
						// esa imagen a un objeto Mat
						// y llamar al hand detector que querramos
						try {
							Mat m = Highgui.imread(m_chosen);
							HandTrackingDetector htd = ((MainApplication) getApplication())
									.getMainController()
									.getHandTrackingDetector();
							// XXX: por algun motivo esto tarda muchiiiisimo
							// tiempo
							m = htd.detect(m);
							if (mBoundFrameStreamingService) {
								mFrameStreamingService.enqueueFrame(m); // enviamos
																		// al
																		// streaming
																		// para
																		// verla
							}
						} catch (Exception e) {
							CustomLog.e("TestManos",
									"Error al detectar mano con imagen estatica\n"
											+ "archivo = " + m_chosen + "\n"
											+ e.toString());
						}

					}
				});

		// You can change the default filename using the public variable
		// "Default_File_Name"
		FileOpenDialog.Default_File_Name = "";
		FileOpenDialog.chooseFile_or_Dir();

	}
	
	public void testScale(View v) {
		com.fi.uba.ar.controllers.HandGestureController.generateZoomGesture(
				SystemClock.uptimeMillis(), // startTime
				true, // ifMove
				new Point(291, 758), // start point 1
				new Point(482, 564), // start point 2
				new Point(249, 766), // end point 1
				new Point(502, 555), // end point 2
				10); // duration
	}

	public void addFragment(Fragment f) {
		setMainContainerContent(f, Fragment_action.ADD);
	}

	private void setMainContainerContent(Fragment f, Fragment_action type) {
		final FragmentTransaction transaction = getFragmentManager()
				.beginTransaction();
		switch (type) {
		case ADD: { // con esto el fragmento esta encima del anterior de forma
					// superpuesta
			transaction.add(R.id.main_container, f, TAG);
		}
			break;
		case REPLACE: {
			transaction.replace(R.id.main_container, f, TAG);
		}
			break;
		}
		// agrega el fragment al stack asi si el usuario navega hacia atras lo
		// ve
		transaction.addToBackStack(null);
		transaction.commit();
	}

	public void removeFragment(Fragment f) {
		final FragmentTransaction transaction = getFragmentManager()
				.beginTransaction();
		transaction.remove(f);
		transaction.commit();
	}

	/*
	 * Este metodo se encarga de cargar cualquier libreria externa que sea
	 * nativa o no. Devuelve true si todas las librerias cargaron correctamente
	 * y falso si algo fallo
	 */
	private boolean loadExternalLibraries() {

		// Si compilamos con stl shared library tenemos que cargar esa primero
		// antes
		// que todo el resto de nuestras libs
		// System.loadLibrary("gnustl_shared");

		// XXX: se supone que no deberia ser necesario forzar a cargar OpenCV
		// pero ha pasado que a veces se registraba el NativeCameraFragment
		// cuando aun opencv
		// no esta listo y da excepciones.
		// El problema con esto es que a veces llamar este initAsync causa que
		// se haga un load
		// de opencv y no estoy seguro si eso puede causar memory leaks por
		// cargar muchas cosas en memoria al pedo

		// quizas no esta mal llamarlo varias veces...este ejemplo muestra que
		// el onResume trata de cargar de neuvo
		// http://docs.opencv.org/platforms/android/service/doc/BaseLoaderCallback.html
		// y nuestra clase tambien lo llama en onResume todas las veces

		if (!this.mLoaderCallback.isOpenCVLoaded()) {
			CustomLog
					.i(TAG,
							"openCV Manager no fue cargado aun. Iniciando carga de OpenCV Async");
			// OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_8,
			// getBaseContext(), this.mLoaderCallback);
			OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this,
					this.mLoaderCallback);
		}

		// si alguna lib necesita de opencv entonces NO podemos hacer load aca
		// hay que hacer load en el callback del manager de opencv

		return true;
	}

	/*
	 * TODO: mover al MainController?? Configuramos archivos necesarios para la
	 * app como: - archivo de calibracion de camara por defecto
	 */
	private void setupInternalFiles() {
		// TODO: esto deberia intentar levantar el archivo de calibracion
		// salvado si es que existe

		try {
			// XXX: copiamos el archivo con calibracion por defecto desde
			// res/raw al cache
			InputStream is = getResources().openRawResource(
					R.raw.intrinsics_yml);
			FileUtils.writeFileToCache("/fiubaar_intrinsics.yml",
					IOUtils.toByteArray(is));
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/*
	// TODO: quizas la calibracion es necesaria para cuando agreguemos el
	// tracking de la mano
	@Deprecated
	public void launchCalibrationActivity(View v) {
		// XXX: hay que asegurarse de que cualquier fragment que tenga abierta
		// la camara se borre
		if (this.mainFragment != null) {
			final FragmentTransaction transaction = getFragmentManager()
					.beginTransaction();
			transaction.remove(this.mainFragment).commit();
			// this.main_fragment.onDestroy(); //XXX: no deberia llamar al
			// destroy directamente
			this.mainFragment = null;
		}

		Intent intent = new Intent(this, CameraCalibrationActivity.class);
		startActivity(intent);
		calibrationRun = true;
	}
	*/

	// XXX: obtenido de
	// http://stackoverflow.com/questions/10380989/how-do-i-get-the-current-orientation-activityinfo-screen-orientation-of-an-a
	public int getScreenOrientation() {
		int rotation = getWindowManager().getDefaultDisplay().getRotation();
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		int width = dm.widthPixels;
		int height = dm.heightPixels;
		int orientation;
		// if the device's natural orientation is portrait:
		if ((rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180)
				&& height > width
				|| (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270)
				&& width > height) {
			switch (rotation) {
			case Surface.ROTATION_0:
				orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
				break;
			case Surface.ROTATION_90:
				orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
				break;
			case Surface.ROTATION_180:
				orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
				break;
			case Surface.ROTATION_270:
				orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
				break;
			default:
				Log.e(TAG, "Unknown screen orientation. Defaulting to "
						+ "portrait.");
				orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
				break;
			}
		}
		// if the device's natural orientation is landscape or if the device
		// is square:
		else {
			switch (rotation) {
			case Surface.ROTATION_0:
				orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
				break;
			case Surface.ROTATION_90:
				orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
				break;
			case Surface.ROTATION_180:
				orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
				break;
			case Surface.ROTATION_270:
				orientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
				break;
			default:
				Log.e(TAG, "Unknown screen orientation. Defaulting to "
						+ "landscape.");
				orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
				break;
			}
		}

		return orientation;
	}

	/*
	//XXX: ImageFragment fue borrado
	public void onEvent(DisplayImageDebugEvent event) {
		if (this.debugFragment != null)
			((ImageFragment) debugFragment).addImage(event.bitmap);
	}
	*/

}
