package com.fi.uba.ar.services;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.Notification;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;

import com.qualcomm.vuforia.Frame;

import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.R;
import com.fi.uba.ar.controllers.MainActivity;
import com.fi.uba.ar.detectors.HandTrackingDetector;
import com.fi.uba.ar.detectors.NativeHandTrackingDetector;
import com.fi.uba.ar.services.tasks.DetectHandTask;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.MatUtils;
import com.fi.uba.ar.views.HandDebugFragment;

// Esto esta implementado de manera que el servicio trabaja con varios threads
// utilizando la clase ThreadPoolExecutor
// Es importante leer https://developer.android.com/training/multiple-threads/index.html
// y mirar el proyecto de ejemplo que muestra claramente como tener varios
// threads que ejecutan tasks

// Ademas es importante entender como funcionan los Services en Android
// http://developer.android.com/guide/components/services.html
// http://developer.android.com/guide/components/bound-services.html

// Aun cuando podemos ejecutar Services en diferentes procesos, toda la comunicacion
// entre procesos via IPC es costosa y probablemente no ayude a mejorar la performance
// de nuestra aplicacion. Entonces vamos a usar LocalServices que se comunican via Intents
// http://developer.android.com/reference/android/app/Service.html#LocalServiceSample

// http://www.vogella.com/tutorials/AndroidServices/article.html

// de acuerdo a varias respuestas de stackoverflow, parece ser que hacer binding a un servicio
// local no es la mejor idea porque puede haber problemas cuando el dispositivo se rota
// que causa que a veces la actividad se cree de nuevo y se haga un "re-bind" al servicio
// causando memory leaks
// http://stackoverflow.com/questions/2282359/how-do-i-bind-this-service-in-android
// http://stackoverflow.com/questions/4908267/communicate-with-activity-from-service-localservice-android-best-practices
// http://stackoverflow.com/questions/4111398/android-notify-activity-from-service

// si se necesita comunicacion entre servicios se pueden usar broadcasts
// http://developer.android.com/reference/android/support/v4/content/LocalBroadcastManager.html
// http://stackoverflow.com/questions/8802157/how-to-use-localbroadcastmanager

// Basamos la parte del uso de ThreadPoolExecutor de la aplicacion de la guia oficial:
// https://developer.android.com/training/multiple-threads/create-threadpool.html

// Este servicio es una implementacion del patron Producer-Consumer en donde
// el Producer (que seria Vuforia) va a agregar frames de video para procesar
// y el Consumer (loop principal de este servicio) va a tomar los frames y 
// enviarlos a procesar por el codigo nativo

// hacemos uso de:
// https://developer.android.com/reference/java/util/concurrent/BlockingDeque.html
// https://developer.android.com/reference/java/util/concurrent/BlockingQueue.html

// Una de las cosas que notamos es que quizas se necesite darle algo de prioridad al servicio
// y de acuerdo a lo investigado parece que esto se podria lograr con un servicio en "foreground"
// https://developer.android.com/guide/components/services.html#Foreground
// https://stackoverflow.com/questions/7408212/high-priority-android-services
// http://www.codeproject.com/Articles/822717/Beginner-s-Guide-to-Android-Services

public class FrameService extends Service {
	// Binding stuff...
	public class LocalBinder extends Binder {
		public FrameService getService() {            
            return FrameService.this;
        }
    }
	
	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(Intent intent) {
		CustomLog.d(TAG, "FrameService.LocalBinder.onBind llamado");
		return mBinder;
	}
	
	/*
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
	    return START_STICKY;
	}
	*/
	//---------------------------------------------
	
	private class FrameConsumerLoop implements Runnable {

		private boolean keepRunning = true;

		@Override
		public void run() {
			
			while (keepRunning) {
				try {
					//XXX: que pasa si no le damos tiempo con un sleep?
					//Thread.sleep(5); 
					
					int cantFrames = framesDeque.size();
					
					//XXX: verificar si esto lo seguimos usando, creo que doSampleHandColor ya no esta siendo seteado en ningun lugar
					// Esto anteriormente se usaba para la implementacion nativa, pero ahora lo hacemos en
					// com.fi.uba.ar.controllers.VuforiaRenderer
					if (doSampleHandColor) {  						
						
		    			// para el sampling de mano necesitamos 50 frames para que tome varias muestras
		    			if (cantFrames < 50) //TODO: este valor deberia ser configurable y tambien habria que setearlo en codigo nativo
		    				continue;
					} else {
						//XXX: a modo de prueba hacemos que no solo se mande al codigo nativo cuando se quiere hacer sample de mano
//						if (matList.size() > 150)
//							matList.clear(); //XXX limpiamos para no acumular frames innecesarios
//						return;
						if (cantFrames < frameGroupSize)
							continue;
					}
					
					// si opencv todavia no fue cargado y recibimos un frame para procesar
			    	// simplemente no hacemos nada porque la app no va a funcionar
			    	if (! MainApplication.getInstance().getMainActivity().isOpenCVLoaded())
			    		continue;
					
					// si llegamos aca es que se dan las condiciones para procesar todos los frames que estan encolados
			    	// - OpenCV ya fue cargado correctamente
			    	// - se pidio sample de la mano y hay 50 o mas frames en cola
			    	// o
					// - hay frameGroupSize o mas frames en cola para procesar
					// recordar que el codigo nativo al momento ignora los frames si es que no se hizo primero el sample de mano

			    	//XXX: puede ser que entre que tomamos la cant de frames hasta aca se hayan agregado mas 
			    	// frames, asi que para no tener problemas lo calculamos de nuevo aca con lo que salga de drainTo
			    	// hago un drain para sacar todo a modo de prueba para ver si es que el poll estaba bloqueando 
			    	ArrayList<Mat> matList = new ArrayList<Mat>();
			    	framesDeque.drainTo(matList);
			    	cantFrames = matList.size(); // nos aseguramos de procesar todo, incluso si se agrego alguna nueva
			    	
			    	//CustomLog.d(TAG, "Hay " + cantFrames + " frames en cola para procesar.");

			    	/*
			    	if (cantFrames != matList.size())
			    		CustomLog.e(TAG, "POR ALGUN MOTIVO LA LISTA DE MATS ES DISTINTA A LA CANTIDAD TOMADA ANTES!\ncantFrames = " + cantFrames + " - matList = " + matList.size());
			    	*/
			    	Mat[] mats = new Mat[cantFrames];
			    	int c = 0;
			    	for (Mat m: matList) {			    		
			    		if (m != null) {
							mats[c] = m;							
							c++;
						}
			    	}
			    	
			    	//TODO: decidir cual de las dos implementaciones usar!! nativa o java?!
			    	
			    	// Deteccion de mano version Java
					processFrame(mats);
					
			    	// Deteccion de mano implementacion C/C++
					//processFrameNative(mats);
					
					matList.clear();
					
				} catch (Exception e) {
					CustomLog.e(TAG, "FrameConsumerLoop capturo una InterruptedException\n" + e.toString());
					keepRunning = false;
				}
			}
		}

		public void stop() {
			keepRunning = false;
		}

	}
	
	
	public static class FrameConsumerHandler extends Handler {
		
		public FrameConsumerHandler(Looper looper) {
			super(looper);
		}
		
		@Override
	    public void handleMessage(Message msg) {
			CustomLog.d("FrameConsumerHandler", "handleMessage fue llamado");
	        Bundle b = msg.getData();
	        byte[] matBytes = b.getByteArray("mat");
	        int mFrameWidth = b.getInt("width", 600);
			int mFrameHeight = b.getInt("height", 800);
			CustomLog.d("FrameConsumerHandler", "mat height = " + mFrameHeight);
			CustomLog.d("FrameConsumerHandler", "mat width = " + mFrameWidth);
			
			Mat m = MatUtils.matFromBytes(mFrameWidth, mFrameHeight, matBytes, false, CvType.CV_8UC3);
	        
			// si opencv todavia no fue cargado y recibimos un frame para procesar
	    	// simplemente no hacemos nada porque la app no va a funcionar
	    	if (! MainApplication.getInstance().getMainActivity().isOpenCVLoaded())
	    		return;
	    	
	    	Mat[] mats = new Mat[1];
	    	mats[0] = m;
	    	// Deteccion de mano version Java
			processFrame(mats);
			
	    	// Deteccion de mano implementacion C/C++
			//processFrameNative(mats);
			m.release();
	    }
		
	}
	//---------------------------------------------
	// A partir de aca estan las cosas de la clase
	protected static final String TAG = "FrameService";	
	
	/**
     * NOTE: This is the number of total available cores. On current versions of
     * Android, with devices that use plug-and-play cores, this will return less
     * than the total number of cores. The total number of cores is not
     * available in current Android implementations.
     */
    private static int NUMBER_OF_CORES = Runtime.getRuntime().availableProcessors();
    // Sets the amount of time an idle thread will wait for a task before terminating
    private static final int KEEP_ALIVE_TIME = 1;

    // Sets the Time Unit to seconds
    //private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.SECONDS;
    private static final TimeUnit KEEP_ALIVE_TIME_UNIT = TimeUnit.MILLISECONDS;

    // Sets the initial threadpool size to 8
    private static final int CORE_POOL_SIZE = 8;

    // Sets the maximum threadpool size to 8
    private static final int MAXIMUM_POOL_SIZE = 8;
	
	// pool para threads en background que hagan las tareas necesarias
    private ThreadPoolExecutor mFramesThreadPool;
    
    // A queue of Runnables for the image download pool
    private BlockingQueue<Runnable> mFramesdWorkQueue;
    
    // para mantener una referencia al thread que ejecuta el loop principal
    private FrameConsumerLoop mainLoop ;
    private Thread mainLoopThread;
    
    private LinkedBlockingDeque<Mat> framesDeque;
    
    //TODO: hacer este valor configurable
    // indica la cantidad de frames en conjunto que se procesan
    private int frameGroupSize = 2; //XXX: tenemos que ver cual seria un buen valor por defecto para esto
    
    private boolean doSampleHandColor = false;
    
    @Override
    public void onCreate() {
    	
    	mFramesdWorkQueue = new LinkedBlockingQueue<Runnable>();
    	
//    	mFramesThreadPool = new ThreadPoolExecutor(NUMBER_OF_CORES, NUMBER_OF_CORES,
//                KEEP_ALIVE_TIME, KEEP_ALIVE_TIME_UNIT, mFramesdWorkQueue);
    	
    	//XXX: a modo de testing hacemos que en el pool haya 1 unico thread 
    	// y asi probamos como es la performance si no usaramos concurrencia con el codigo nativo
    	/*
    	mFramesThreadPool = new ThreadPoolExecutor(	CORE_POOL_SIZE, 
    												MAXIMUM_POOL_SIZE,
    												KEEP_ALIVE_TIME, 
    												KEEP_ALIVE_TIME_UNIT, 
    												mFramesdWorkQueue);
    	*/
    	mFramesThreadPool = new ThreadPoolExecutor( 1, 
    												1,
													KEEP_ALIVE_TIME, 
													KEEP_ALIVE_TIME_UNIT, 
													mFramesdWorkQueue);
    	
    	framesDeque = new LinkedBlockingDeque<Mat>();
    	
    	mainLoop = new FrameConsumerLoop();    	
    	mainLoopThread = new Thread(mainLoop);
    	mainLoopThread.start();
    	
    	//XXX: probamos iniciar al servicio en foreground a ver si ganamos prioridad
    	final Notification.Builder builder =
                new Notification.Builder(this)
                        .setSmallIcon(R.drawable.ic_launcher)
                        .setContentTitle("FiubaAR-FrameService")
                        .setContentText("Processing frames...");

        final Notification notification = builder.build();

        startForeground(1, notification);
    	
        CustomLog.d(TAG, "FrameService creado");
    }
    
    @Override
    public void onDestroy () {
    	mainLoop.stop();
    	try {
			mainLoopThread.join();
		} catch (InterruptedException e) {
			CustomLog.e(TAG, "error esperando a mainLoopThread a que termine\n" + e.toString());
		}
    }
    
    public void enqueueFrame(Mat m) {
    	try {
    		
    		if (m.size().area() > 0 ) {
    			framesDeque.add(m);
    		} else
        		CustomLog.d(TAG, "enqueueFrame recibio mat con area 0 - descartandola... - m = " + m);
    	} catch (Exception ex) {
    		CustomLog.e(TAG, "Error adding frame to framesDeqeue");
    	}
    }
    
    /**
     * Procesa un array de Mat(s) de OpenCV generadas a partir de los frames de Vuforia.
     * 
     * Utiliza la implementaci�n nativa para detectar la mano.
     * 
     * Inicializa el NativeHandTrackingDetector (si no se hizo a�n) y verifica que se 
     * haya muestreado el color de la mano. Si se hizo, entonces se crea una tarea
     * "DetectHandTask" con estas matrices y se pone entrega al ThreadPoolExecutor para
     * su procesamiento.
     *  
     * @param mats
     */
    public void processFrameNative(Mat[] mats) {
    	
    	if (! NativeHandTrackingDetector.initialized) // no es la mejor idea verificar esto por cada frame que nos lleg
    		NativeHandTrackingDetector.initialize();
    	// si todavia no se hizo el color sample de la mano entonces descartamos todo
    	if (NativeHandTrackingDetector.handColorSampled) {
	    	// con esto se hace deteccion con codigo nativo    	
			mFramesThreadPool.execute(new DetectHandTask(mats, false));
    	}
    	
    }
    
    //TODO: en este momento estamos procesando un grupo de frames (cuando hay una cant >= frameGroupSize)
    // pero solo se procesa 1 por una y mandamos al streaming service para ver si dibujo y detecto las cosas
    // pero no estamos haciendo nada con el resultado.
    // Ademas como hacemos 1 x 1 no tenemos contexto sobre si hubo un movimiento o no para ver los gestos
    
    /**
     * Procesa un array de Mat(s) de OpenCV generadas a partir de los frames de Vuforia.
     * 
     * Utiliza la implementaci�n en Java para detectar la mano.
     * 
     * Obtiene la instancia de MainActivity, y luego obtiene de esta al MainController. 
     * Llama al HandTrackingDetector del MainController, invocando a su m�todo "detect()".
     * 
     * @param mats
     */
    public static void processFrame(Mat[] mats) {
    	//CustomLog.d(TAG, "processFrame CALLED - processing " + mats.length + " mats");
    			
    	//TODO: sera mejor obtener esto en algun otro lugar para no repetir tantas veces la llamada?
    	MainActivity mActivity = MainApplication.getInstance().getMainActivity();    	
    	HandTrackingDetector htd = ((MainApplication)mActivity.getApplication()).getMainController().getHandTrackingDetector();
    	//HandTrackingDetector htd = ((MainApplication)getApplication()).getMainController().getHandTrackingDetector();
    	
    	//XXX: aca intentamos usar la implementacion en java para detectar la mano
    	if (htd != null) {
			for (Mat m: mats) {
				try {
					///MatUtils.dumpMatInfo(m);
					// OpenCV suele tener varios lugares que pueden dar excepciones
					// y si no las capturamos la app crashea completamente
					//CustomLog.d(TAG, "ANTES del detect");
					m = htd.detect(m);
					//CustomLog.d(TAG, "DESPUES del detect");
					//XXX: a modo de debug dumpeamos la info del HandGesture para ver que tiene y
					// asi determinar que datos nos van a ser utiles para los gestos
					htd.getHandGesture().dumpData();
					
					//TODO: claramente hay que hacer alguna transformacion de coordenadas porque
					// cuando dibujamos circulos en los puntos x,y de opencv transformados a puntos de android graphics
					// no quedan donde deberian...
					// es como si la imagen fuese mas chica y como si estuviese horizontal y no vertical como lo vemos en la pantalla
					// parece como si la imagen estuviera rotada 90 grados hacia la izquierda
					// ADEMAS: el delay que hay es grande! muy grande!!
					List<Point> fingerTips = htd.getHandGesture().fingerTips;
					HandDebugFragment hdf = HandDebugFragment.getInstance();
					if (hdf != null && !fingerTips.isEmpty()) {
						CustomLog.d(TAG, "HandDebugFragment no es null");
						CustomLog.d(TAG, "se detectaron " + fingerTips.size() + " dedos");
						List<android.graphics.Point> points = new ArrayList<android.graphics.Point>();
						for (Point p: fingerTips) {
							// como esta rotada vamos a probar usar las coordenadas x,y invertidas
							// x,y invertidos - ESTO ES LO CORRECTO! 
							double width_factor = 1.77; // 2.2; // 1280 / 720 = 1.77
							double heigth_factor = 1.5; // 1.8; // 720 / 480 = 1.5
							
							
							android.graphics.Point pt = new android.graphics.Point(
														(int)Math.round(p.y * width_factor), 
														(int)Math.round(p.x * heigth_factor));
							
							// Original - parece que vuforia nos pasara la imagen como si estuviese en portrait mode y no vertical asi que 
							// no usamos x,y sino que la version invertida
							//android.graphics.Point pt = new android.graphics.Point((int)Math.round(p.x), (int)Math.round(p.y));
							CustomLog.d(TAG, "Dedo p = " + pt.x + ", " + pt.y);
							points.add(pt);
						}
						hdf.addFingerTipPoints(points);
					}
				} catch (Exception e) {
					CustomLog.e(TAG,"Error procesando frame para detectar mano");
					CustomLog.e(TAG, e.toString());
					m = null; // para poder seguir procesando el resto de los frames
				}
				
				// m puede ser null si todavia no se hizo el color sampling de fondo y mano
				if (m != null) {					
					// enviamos el frame al servicio de streaming ya que si fue modificada y 
					// se dibujo un contorno a la mano lo mostramos
					if (mActivity.mBoundFrameStreamingService) {
						mActivity.mFrameStreamingService.enqueueFrame(m);
					}
				}
			}
		}
    }

	/**
	 * Permite configurar el ancho de la "ventana" de an�lisis de gestos.
	 * 
	 * @param frameGroupSize
	 */
	public void setFrameGroupSize(int frameGroupSize) {
		this.frameGroupSize = frameGroupSize;
	}

	/**
	 * Fuerza el muestreo del color de la mando dentro de runnable FrameConsumerLoop.
	 * 
	 * Actualmente no se utiliza en la aplicaci�n, pues el muestreo se hace desde 
	 * com.fi.uba.ar.controllers.VuforiaRenderer
	 */
	public void setDoSampleHandColor() {
		this.doSampleHandColor = true;
	}
    
    //TODO: Tendriamos que pensar en que quizas no deberiamos crear un task independiente por cada frame que llega
    // y procesar de manera asincronica cada uno de ellos ya que quizas perdemos la logica del contexto.
    // Con esto me refiero a que quizas debemos tener una cierta cantidad de frames continuos para tener algo de
    // informacion consecutiva como de un movimiento y asi procesar todos esos frames como un grupo
    // Puede ser que esto a nivel performance no nos haga ganar nada, pero quizas ganamos en la "logica"
    // Podriamos llegar a tener incluso un valor configurable que indique "cuantos frames componen un grupo a procesar"
    // y de esa forma lo podriamos poner en 1 si queremos y asi estariamos con la misma logica que ahora.
	
	//XXX: esto es lo que acabamos de implementar y tenemos que poder configurar la cantidad
	
	//XXX: quizas la alternativa a usar el FrameService que tiene mucho delay es implementar esto con un
	// HandlerThread
	// https://guides.codepath.com/android/Managing-Threads-and-Custom-Services
	// https://blog.nikitaog.me/2014/10/11/android-looper-handler-handlerthread-i/
	// https://blog.nikitaog.me/2014/10/18/android-looper-handler-handlerthread-ii/
    

}
