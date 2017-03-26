package com.fi.uba.ar.controllers;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

import android.app.Activity;
import android.app.Instrumentation;
import android.graphics.Point;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.MotionEvent.PointerProperties;
import android.view.MotionEvent.PointerCoords;

import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.MessageUtils;
import com.fi.uba.ar.utils.MessageUtils.ToastType;

import org.apache.commons.collections4.queue.CircularFifoQueue;

/*
 * La funcion principal de esta clase es recibir la informacion
 * de todos los dedos detectados y a partir de esto 
 * poder manejar la deteccion de gestos
 *  
*/

//XXX: La idea es usar los GestureDetector que ya provee android
// y crear instancias de MotionEvent a partir de la informacion de los dedos
// con esto intentamos lograr que la posicion de la mano 
// funcionara como si eso mismo estuviera sucediendo realmente
// en le pantalla de la aplicacion y los dedos estuvieran tocando 
// el dispositivo

public class HandGestureController {
	private static final String TAG = "HandGestureController";	
	
	// Cantidad de puntos que se guardan en una cola circular
	private static final int AMOUNT_HISTORY = 10;	
	
	// Cantidad con la cual cortamos la deteccion y enviamos un ACTION_UP para generar el gesto
	private static final int GESTURE_FINISH_COUNT = 50; //XXX: y si lo hacemos configurable?
	
	// Contador de puntos detectados con el posible gesto actual que comparamos contra GESTURE_FINISH_COUNT
	private static int CURRENT_GESTURE_COUNT = 0; 
	
	private static final long EVENT_MIN_INTERVAL = 1;
	private static HandGestureController instance = null;
	
	// mantenemos un historial de los ultimos AMOUNT_HISTORY conjuntos de dedos detectados
	private CircularFifoQueue<List<Point>> previous_points;
	
	private List<List<Point>> stable_detection_points;
	
	private boolean down_event_sent = false;
	private int current_gesture_finger_count = 0;
	
	private boolean handDetectionStable = false;
	
	private boolean analyzeHandDetection = true;
	private boolean analyzeHandDetectionFinished = false;
	
	//XXX: deberiamos pasar este flag a que sea un valor global de la app? (no config, sino algo que no sea solo de este controlador)
	private boolean gestureDetectionEnabled = false;
	
	private final int STABLE_DETECTION_COUNT = 10; //XXX: 20 resulto ser demasiado y lo pasamos a 10
	
	//TODO: setear un valor logico para el delta!
	private final int POINTS_CLOSE_DELTA_THREASHOLD = 20;
	
	public static HandGestureController getInstance() {
		if (instance == null)
			new HandGestureController();
		return instance;
	}
	
	public HandGestureController() {
		instance = this;
		previous_points = new CircularFifoQueue<List<Point>>(AMOUNT_HISTORY);
		stable_detection_points = new ArrayList<List<Point>>();
	}
	
	
	public boolean isHandDetectionStable() {
		return handDetectionStable;
	}
	
	public void toggleGestureDetection() {
		gestureDetectionEnabled = !gestureDetectionEnabled;
		// Si se activo la deteccion de gesto entonces forzamos a que se haga deteccion de estabilidad
		if (gestureDetectionEnabled) {
			analyzeHandDetection = true;
			analyzeHandDetectionFinished = false;
			stable_detection_points.clear();			
		}
	}
	
	
	/*
	 * Este metodo calcula y devuelve la cantidad promedio
	 * de dedos que existen en el historial de puntos guardados
	 */
	private int getAverageFingerCount(List<List<Point>> list) {
		int avg = 0;
		
		if (!list.isEmpty()) {
			for (List<Point> points: list) {
				avg += points.size();			
			}
			avg = avg / list.size();
		}
		
		return avg;
	}
	
	/*
	 * Busca en el historial de puntos de atras hacia adelante
	 * hasta encontrar el elemento que tiene la cantidad de
	 * puntos que se indica por parametro
	 * Devuelve null si no se encuentra lo deseado
	 */
	private List<Point> getLastEventWithAmount(int count) {
		// https://stackoverflow.com/questions/2102499/iterating-through-a-list-in-reverse-order-in-java
		ArrayList<List<Point>> lp = new ArrayList<List<Point>>(previous_points);
		ListIterator it = lp.listIterator(lp.size());
		List<Point> ps = null;
		while(it.hasPrevious()) {
		     ps = (List<Point>) it.previous();
		     if (ps.size() == count) {
		    	 return ps;
		     }
		}
		return null;		
	}
	
	/*
	 * Determina si los ultimos 3 elementos de la lista
	 * tiene el mismo valor que el pasado por parametro
	 */
	private boolean checkCountChanged(int value) {
		int history_size = previous_points.size();
		if (history_size < 3)
			return false;
		
		if ((previous_points.get(history_size-1).size() == value) &&
			(previous_points.get(history_size-2).size() == value) &&
			(previous_points.get(history_size-3).size() == value))
			return true;
		else
			return false;		
	}
	
	
	/*
	 * Este metodo es llamado cada vez que se detectan dedos
	 * y se le pasa una lista de puntos que corresponden a 
	 * las coordenadas donde se encuentra la punta de cada dedo
	 * Efectivamente se deberia llamar a este metodo por cada frame
	 * de video.
	 */	
	public void addFingerTipPoints(List<Point> points) {
		// Si no esta habilitada la deteccion cortamos inmediatamente
		if (! gestureDetectionEnabled)
			return;
		
		// Antes de detectar gestos analizamos si la estabilidad es buena
		if (analyzeHandDetection) {
			analyzeStability(points);
			return;
		} else {
			if (analyzeHandDetectionFinished) {
				analyzeHandDetectionFinished = false;
				// si ya realizamos la deteccion vemos si fue estable o no
				CustomLog.d(TAG, "Se realizo el analisis de estabilidad de deteccion - resultado Estable = " + handDetectionStable);
				if (handDetectionStable) {
					MessageUtils.showToast(ToastType.SUCCESS, "La deteccion de mano es estable");					
				}
				else {
					//TODO: probar si este comportamiento no nos complica todo!!!
					
					// Si la deteccion no fue estable lo informamos y no activamos la deteccion de gestos
					// Ademas deshabilitamos el switch de la UI para que el usuario tenga que hacer la deteccion de estabilidad otra vez 
					CustomLog.d(TAG, "La deteccion de manos aun no es estable, no se recomienda detectar gestos en tal estado. Por favor habilite nuevamente la deteccion de gestos");		
					MessageUtils.showToast(ToastType.WARNING, "La deteccion de manos no es estable. Rehabilite la deteccion de gestos");
					// No seteamos este flaga aca porque al hacer el toggle de la UI ese listener
					// va a hacer un toggle de este flag interno. Si no lo hacemos asi queda en un loop
					// infinito activando y desactivando esta deteccion
					//gestureDetectionEnabled = false;
					MainApplication.getInstance().getMainActivity().toggleGestureSwitch();
					return;
				}		
			}
		}
		
		
		
		boolean save_points = true;
		
		// hay que adaptar este metodo para que genere eventos para todos los dedos 
		// como si fuera algo multitouch
		int current_finger_count = points.size();
		CustomLog.d(TAG, "addFingerTipPoints - current_finger_count = " +  current_finger_count +
				" - current_gesture_finger_count = " + current_gesture_finger_count +
				" -  down_event_sent = " + down_event_sent);
		
		if (down_event_sent) {
			// Ya se envio un ACTION_DOWN y puede ser que sigamos en un movimiento
			// Para esto determinamos si se siguen detectando la misma cantidad de
			// dedos que en los eventos anteriores.
			// Ya que la deteccion de dedos con la imagen puede no ser 100% precisa
			// entre frame y frame lo que hacemos es tomar el promedio del historial
			// y consideramos que si la cantidad actual que se nos pasa es la misma
			// estamos en el medio de un movimiento
			
			
			//if (current_finger_count == getAverageFingerCount()) {
			//XXX: usamos un valor current... es necesario tener el promedio?
			if (current_finger_count == current_gesture_finger_count) { 
				dispatchMoveEvents(points);
			}
			else {
				
				// En el caso en el que la cantidad sea diferente, NO asumismos
				// inmediatamente que el "movimiento" termino, ya que se puede 
				// tratar de que justo en este frame no fue detectada correctamente
				// la cantidad real de dedos
				// Para determinar que realmente esta cantidad es correcta y que 
				// el movimiento termino y hay que enviar los ACTION_UP
				// nos fijamos que al menos los ultimos 3 elementos del
				// historial de dedos sean iguales a la cantidad que recibimos
				// Asi entonces asumimos que efectivamente la candidad de dedos
				// que el usuario muestra cambio
				//XXX: esta logica nunca cambiaria el gesto si por ejemplo se detectara siempre alternando valores.
				// Por ejemplo si detectamos 4 dedos y despues detectamso siempre 2, 3 ,2 ,3 , etc
				// asi nunca tendriamos 3 consecutivos iguales pero es claro que ya no es el mismo gesto
				if (checkCountChanged(current_finger_count)) {
					// Tenemos que enviar eventos ACTION_UP para indicar
					// que el gesto termino y que los detectors correspondiente 
					// lo puedan procesar
					
					// seteamos este flag para poder empezar de nuevo a detectar
					// un posible gesto nuevo a partir de la proxima deteccion de dedos
					down_event_sent = false; 
					
					// los eventos de ACTION_UP deberian ser los correspondientes 
					// a aquellos ultimos puntos en los que todavia teniamos todavia
					// current_gesture_finger_count de dedos
					// Entonces buscamos en nuestro historial esos
					List<Point> up_points = getLastEventWithAmount(current_gesture_finger_count);
					//XXX: puede ser que se nos haya ido de la cola el ultimo caso en el que tuvimos
					// la cantidad de dedos del gesto actual.
					// Un caso que vimos en la practica fue:
					// inicia el gesto con 4 dedos y despues se detecta la siquiente secuencia: 2 3 2 2 2 2
					// Para cuando llegamos a tener 3 valores con 2 dedos consecutivos el de 4 se nos fue de la cola!
					if (up_points != null)
						dispatchUpEvents(up_points);
					else
						//XXX: que hacemos? mandamos un ACTION_CANCEL?
						//TODO: tenemos que definir que hacer cuando se de esta situacion
						CustomLog.d(TAG, "NO ENCONTRAMOS ULTIMOS PUNTOS CON " + current_gesture_finger_count + " DEDOS");
					
				} else {
					//XXX: aca podriamos usar save_points = false y evitariamos guardar
					// en el historial los puntos que no usamos para nada... Tendriamos que analizarlo mejor
					
					//XXX: este else se podria evitar agregando un OR en un if anterior
					// pero lo dejamos asi para que sea mas clara la logica
					
					// La cantidad de dedos actual es diferente pero no lo
					// consideramos realmente cambio y asumimos que seguimos
					// con current_gesture_finger_count cantidad de dedos
					// y enviamos ACTION_MOVE

					//XXX: EN ESTE CASO TENDRIAMOS UNA CANTIDAD DIFERENTE
					// y si mandamos eventos de move, como hacemos con los pointer ids que nos 
					// sobran o faltan??
					// repetimos un move con el ultimo evento que tenia la misma cantidad???
					// Por ahora no hacemos nada! pero hay que determinar que hacer
					//dispatchMoveEvents(points);
				}
				
			}
			
			//XXX: esto causa que apenas tengamos una mano en pantalla el objeto 3D
			// se ponga loco a cambiar de tamanio y girar porque la deteccion de
			// dedos parece no ser tan estable en cuanto a las posiciones
			
			
			// Agregamos esto para generar ACTION_UP cada cierta cantidad fija como para que
			// se vaya detectando un gesto a medida que sucede... sino puede ser que no detectemos
			// que se cambiaron las cantidades de dedos...
			if ((CURRENT_GESTURE_COUNT == GESTURE_FINISH_COUNT) && down_event_sent) {
				down_event_sent = false; 
				List<Point> up_points = getLastEventWithAmount(current_gesture_finger_count);		
				if (up_points != null)
					dispatchUpEvents(up_points);				
			}
		
		}
		else { 
			// quiere decir que tenemos que "iniciar" un posible gesto
			// y para eso todos los dedos en este caso tienen que ser
			// creados con MotionEvent.ACTION_DOWN (y pointer si hay mas de un dedo)
			down_event_sent = true;
			 // limpiamos el historial ya que eso es usado para analizar si 
			// "seguimos en un movimiento" siempre y cuando se hayan detectado la misma cantidad de dedos
			previous_points.clear(); 
			CURRENT_GESTURE_COUNT = 0;
			
			current_gesture_finger_count = current_finger_count ;
			dispatchDownEvents(points);
			
		}

		if (save_points) {
			// guardamos estos puntos en el historial
			previous_points.add(points);
			CURRENT_GESTURE_COUNT++;
		}
	}
	
	
	
	/**
	 * Este metodo intenta realizar una deteccion de cuan estable
	 * es la deteccion de mano y dedos a partir de una secuencia
	 * (hitorial) de puntos en donde se han detectado dedos
	 * 
	 * @param points
	 */
	private void analyzeStability(List<Point> points) {
		// La idea es ir guardando los puntos que se detectan
		// y una vez que lleguemos a tener unos X puntos guardados
		// analizamos si en la secuencia:
		// 1 - predomina la misma cantidad de dedos detectados
		// 2 - los dedos detectados se encuentran en cada caso en posiciones cercanas
		// Esta cercania la tenemos que determinar en cuando a cierto valor limite 
		// entre las coordenadas (x,y) en cada caso
		if (stable_detection_points.isEmpty())
			MessageUtils.showToast(ToastType.INFO, "Iniciando analisis de estabilidad de deteccion de mano");
					
		if (stable_detection_points.size() < STABLE_DETECTION_COUNT) {
			stable_detection_points.add(points);
		} else {
			CustomLog.d(TAG, "analyzeStability - analizando puntos para determinar si es estable...");
			// Tenemos ya 20 puntos guardados y podemos intentar hacer la deteccion
			int avgFingerCount = getAverageFingerCount(stable_detection_points);
			CustomLog.d(TAG, "analyzeStability - avgFingerCount = " + avgFingerCount);
			int countWithAvg = 0;
			List<Point> ps1 = null, ps2 = null;
			boolean[] close_flags_array = new boolean[STABLE_DETECTION_COUNT]; // como maximo podemos tener todos los puntos con la misma cantidad de dedos

			// cuantos de los 20 que tenemos tienen realmente ese promedio
			// y a los casos que tengan ese valor los comparamos con el 
			// siguiente grupo con misma cantidad para saber si "estan cercanos"
			for (List<Point> ps: stable_detection_points) {
				if (ps.size() == avgFingerCount) {
					
					if (ps1 == null) 
						ps1 = ps;
					else 
						ps2 = ps;
					
					if (ps1 != null && ps2 != null) {
						close_flags_array[countWithAvg] = areFingersClose(ps1, ps2);
						ps1 = ps2; // guardamos el segundo para comparar con aquel que siga si es que hay otro
						ps2 = null; 						
					}
					
					countWithAvg++;					
				}
			}
			
			//XXX: cuantos de los 20 deberiamos tener para considerar que es estable?
			// Usamos 75% por el momento
			if (countWithAvg < (STABLE_DETECTION_COUNT * 0.75)) { 
				handDetectionStable = false;
			} else {
				// Ahora determinamos si de esos dedos que tienen avgFingerCount tienen coordenadas cercanas
				// Para esto analizamos lo que tenemos guardado en close_flags_array
				// y vemos si hay un buen porcentaje de ellos que si dieron cercanos
				
				//XXX: tenemos que definir bien el porcentaje de aceptacion
				int proximityCount = 0;
				for (int i=0; i < countWithAvg; i++) {
					if (close_flags_array[i])
						proximityCount++;
				}
				float proximityPercentage = proximityCount * 100 / countWithAvg;
				
				handDetectionStable = (proximityPercentage >= 75.0);
				
				CustomLog.d(TAG, "analyzeStability - todo los dedos procesados"+
						" - handDetectionStable = " + handDetectionStable +
						" - countWithAvg = " + countWithAvg +
						" - close_flags_array = " + close_flags_array + 
						" - proximityCount = " + proximityCount +
						" - proximityPercentage = " + proximityPercentage);
				
			}
			
			// Ya finalizamos la deteccion asi que lo seteamos para poder comenzar a detectar gestos
			analyzeHandDetection = false;
			analyzeHandDetectionFinished = true;
		}
		
	}
	
	private boolean areFingersClose(List<Point> fs1, List<Point> fs2) {
		// ambas listas deben tener la misma cantidad de elementos
		int count = fs1.size();
		boolean[] close_flags_array = new boolean[count];
		if (fs1.size() != fs2.size())
			return false;
		
		for (int x=0; x < count; x++) {
			Point p1 = fs1.get(x);
			Point p2 = fs2.get(x);
			close_flags_array[x] = arePointsClose(p1, p2);
		}
		
		int proximityCount = 0;
		for (int i=0; i < count; i++) {
			if (close_flags_array[i])
				proximityCount++;
		}
		float proximityPercentage = proximityCount * 100 / count;
		
		CustomLog.d(TAG, "areFingersClose - count = " + count + " - proximityCount = " + proximityCount + " - proximityPercentage = " + proximityPercentage);
		
		// Si tenemos solo 2 dedos, los dos tienen que estar cerca
		// Si tenemos 3 o mas dedos entonces al menos un 60% deben estar cerca
		return (proximityPercentage >= 60.0);	
	}
	
	private boolean arePointsClose(Point p1, Point p2) {
		int x_delta = Math.abs(p1.x - p2.x);
		int y_delta = Math.abs(p1.y - p2.y);
		CustomLog.d(TAG, "arePointsClose - p1 = " + p1 + " -  p2 = " + p2 + " <<<>>> x_delta = " + x_delta + " - y_delta = " + y_delta);
		
		return ( (x_delta <= POINTS_CLOSE_DELTA_THREASHOLD) && 
				 (y_delta <= POINTS_CLOSE_DELTA_THREASHOLD) );		
	}
	
	private void dispatchDownEvents(List<Point> points) {
		if (points == null) {
			CustomLog.d(TAG, "dispatchDownEvents - points = null");
			return;
		}
		
		int finger_count = points.size();
		CustomLog.d(TAG, "dispatchDownEvents - finger_count = " +  finger_count);
		int count = 0;
		PointerProperties[] properties = new PointerProperties[finger_count];
		PointerCoords[] pointerCoords = new PointerCoords[finger_count];
		MotionEvent event;
		
		for (Point p: points) {
			PointerProperties pp = new PointerProperties();
		    pp.id = count;
		    pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
			properties[count] = pp;
			
			PointerCoords pc = new PointerCoords();
		    pc.x = p.x;
		    pc.y = p.y;
		    pc.pressure = 1;
		    pc.size = 1;
		    pointerCoords[count] = pc;
			
		    count++;
		}
		
		// El primer dedo tiene que ir con ACTION_DOWN
		CustomLog.d(TAG, "dispatchDownEvents - ACTION_DOWN - primer dedo");
		event = MotionEvent.obtain(
				SystemClock.uptimeMillis(), 
				SystemClock.uptimeMillis(), 
                MotionEvent.ACTION_DOWN, 1, properties, 
                pointerCoords, 0,  0, 1, 1, 0, 0, 0, 0 );
		
		dispatchEventToUI(event);

		// El resto de los dedos va con un pointer_down y un id diferente
		for (int id = 1; id < finger_count; id++) {
			//int id = x+1;
			CustomLog.d(TAG, "dispatchDownEvents - ACTION_POINTER_DOWN - id = " +  id);
			event = MotionEvent.obtain(
					SystemClock.uptimeMillis(), 
					SystemClock.uptimeMillis(), 
		    		MotionEvent.ACTION_POINTER_DOWN + (id << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 
		    		id+1, properties, pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0); // el parametro pointerCount es id+1 ya que los id inician en 0
			dispatchEventToUI(event);
		}
		
	}
	
	private void dispatchUpEvents(List<Point> points) {		
		if (points == null) {
			CustomLog.d(TAG, "dispatchUpEvents - points = null");
			return;
		}
		
		int finger_count = points.size();
		CustomLog.d(TAG, "dispatchUpEvents - finger_count = " +  finger_count);
		int count = 0;
		PointerProperties[] properties = new PointerProperties[finger_count];
		PointerCoords[] pointerCoords = new PointerCoords[finger_count];
		MotionEvent event;
		
		for (Point p: points) {
			PointerProperties pp = new PointerProperties();
		    pp.id = count;
		    pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
			properties[count] = pp;
			
			PointerCoords pc = new PointerCoords();
		    pc.x = p.x;
		    pc.y = p.y;
		    pc.pressure = 1;
		    pc.size = 1;
		    pointerCoords[count] = pc;
			
		    count++;
		}
		
		// En el caso de los UP tenemos que enviar los pointers 
		// en el orden inverso con ACTION_POINTER_UP
		for (int id = finger_count-1; id > 0; id--) {
			//int id = x+1;
			CustomLog.d(TAG, "dispatchUpEvents - ACTION_POINTER_UP - id = " +  id);
			event = MotionEvent.obtain(
				 	SystemClock.uptimeMillis(), 
					SystemClock.uptimeMillis(), 
		    		MotionEvent.ACTION_POINTER_UP + (id << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 
		    		id+1, properties, pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0);	// el parametro pointerCount es id+1 ya que los id inician en 0	    
		    dispatchEventToUI(event);
		}

		// El primer punto de la lista lo enviamos como ACTION_UP
		CustomLog.d(TAG, "dispatchUpEvents - ACTION_UP - ultimo dedo");
	    event = MotionEvent.obtain(
	    			SystemClock.uptimeMillis(), 
					SystemClock.uptimeMillis(), 
	                MotionEvent.ACTION_UP, 1, properties, 
	                pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0 );
	    dispatchEventToUI(event);
	}
	
	private void dispatchMoveEvents(List<Point> points) {
		if (points == null) {
			CustomLog.d(TAG, "dispatchMoveEvents - points = null");
			return;
		}
		
		// Para el caso del move enviamos un unico evento con todos los points
		
		int finger_count = points.size();
		CustomLog.d(TAG, "dispatchMoveEvents - finger_count = " +  finger_count);
		int count = 0;
		PointerProperties[] properties = new PointerProperties[finger_count];
		PointerCoords[] pointerCoords = new PointerCoords[finger_count];
		MotionEvent event;
		
		for (Point p: points) {
			PointerProperties pp = new PointerProperties();
		    pp.id = count;
		    pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
			properties[count] = pp;
			
			PointerCoords pc = new PointerCoords();
		    pc.x = p.x;
		    pc.y = p.y;
		    pc.pressure = 1;
		    pc.size = 1;
		    pointerCoords[count] = pc;
			
		    count++;
		}
		
		CustomLog.d(TAG, "dispatchMoveEvents - ACTION_MOVE - todos los dedos");
		event = MotionEvent.obtain(
				SystemClock.uptimeMillis(), 
				SystemClock.uptimeMillis(), 
	    		MotionEvent.ACTION_MOVE, 
	    		finger_count, properties, pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0);
	    		//finger_count+1, properties, pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0); // se estaba enviando mal la cantidad de pointer
		dispatchEventToUI(event);
		
	}
	

	
	// https://stackoverflow.com/questions/5161951/android-only-the-original-thread-that-created-a-view-hierarchy-can-touch-its-vi
	// sugieren usar Activity.runOnUiThread con un Runnable que haga el dispatch ahi
	// https://stackoverflow.com/questions/18656813/android-only-the-original-thread-that-created-a-view-hierarchy-can-touch-its-vi
	// Este metodo asegura que los eventos son despachados en el therad UI correctamente
	private static void dispatchEventToUI(final MotionEvent e) {
		final Activity main_activity = MainApplication.getInstance().getMainActivity();
		main_activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
					CustomLog.d(TAG,"dispatchEventToUI - run - MotionEvent = " + e);
					main_activity.dispatchTouchEvent(e);
			}
		});
	}

	
	//XXX: metodo de prueba obtenido de:
	// https://stackoverflow.com/questions/11523423/how-to-generate-zoom-pinch-gesture-for-testing-for-android
	// Comprobamos que en efecto funciona correctamente y causa que se haga un zoom en un obj3D tal
	// como si lo hubieramos hecho real y fisicamente sobre la pantalla
	// Este metodo funciona correctamente y cuando lo probamos el objeto 3D se achica con el gesto de scale
	public static void generateZoomGesture(
	        long startTime, boolean ifMove, 
	        Point startPoint1, Point startPoint2, 
	        Point endPoint1, Point endPoint2, 
	        int duration) {

	    if (startPoint1 == null || (ifMove && endPoint1 == null)) {
	        return;
	    }

	    //Instrumentation inst = MainApplication.getInstance().getInstrumentation();
	    
	    long eventTime = startTime;
	    long downTime = startTime;
	    MotionEvent event;
	    float eventX1, eventY1, eventX2, eventY2;

	    eventX1 = startPoint1.x;
	    eventY1 = startPoint1.y;
	    eventX2 = startPoint2.x;
	    eventY2 = startPoint2.y;

	    // specify the property for the two touch points
	    PointerProperties[] properties = new PointerProperties[2];
	    PointerProperties pp1 = new PointerProperties();
	    pp1.id = 0;
	    pp1.toolType = MotionEvent.TOOL_TYPE_FINGER;
	    PointerProperties pp2 = new PointerProperties();
	    pp2.id = 1;
	    pp2.toolType = MotionEvent.TOOL_TYPE_FINGER;

	    properties[0] = pp1;
	    properties[1] = pp2;

	    //specify the coordinations of the two touch points
	    //NOTE: you MUST set the pressure and size value, or it doesn't work
	    PointerCoords[] pointerCoords = new PointerCoords[2];
	    PointerCoords pc1 = new PointerCoords();
	    pc1.x = eventX1;
	    pc1.y = eventY1;
	    pc1.pressure = 1;
	    pc1.size = 1;
	    PointerCoords pc2 = new PointerCoords();
	    pc2.x = eventX2;
	    pc2.y = eventY2;
	    pc2.pressure = 1;
	    pc2.size = 1;
	    pointerCoords[0] = pc1;
	    pointerCoords[1] = pc2;

	    //////////////////////////////////////////////////////////////
	    // events sequence of zoom gesture
	    // 1. send ACTION_DOWN event of one start point
	    // 2. send ACTION_POINTER_2_DOWN of two start points
	    // 3. send ACTION_MOVE of two middle points
	    // 4. repeat step 3 with updated middle points (x,y),
	    //      until reach the end points
	    // 5. send ACTION_POINTER_2_UP of two end points
	    // 6. send ACTION_UP of one end point
	    //////////////////////////////////////////////////////////////

	    // step 1
	    event = MotionEvent.obtain(downTime, eventTime, 
	                MotionEvent.ACTION_DOWN, 1, properties, 
	                pointerCoords, 0,  0, 1, 1, 0, 0, 0, 0 );

	    //inst.sendPointerSync(event);
	    dispatchEventToUI(event);

	    //step 2
	    
//	    event = MotionEvent.obtain(downTime, eventTime, 
//	                MotionEvent.ACTION_POINTER_2_DOWN, 2, 
//	                properties, pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0);
	    
	    event = MotionEvent.obtain(downTime, eventTime, 
	    		MotionEvent.ACTION_POINTER_DOWN + (pp2.id << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 
	    		2, properties, pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0);

//	    inst.sendPointerSync(event);
	    dispatchEventToUI(event);

	    //step 3, 4
	    if (ifMove) {
	        int moveEventNumber = 1;
	        moveEventNumber = (int) (duration / EVENT_MIN_INTERVAL);

	        float stepX1, stepY1, stepX2, stepY2;

	        stepX1 = (endPoint1.x - startPoint1.x) / moveEventNumber;
	        stepY1 = (endPoint1.y - startPoint1.y) / moveEventNumber;
	        stepX2 = (endPoint2.x - startPoint2.x) / moveEventNumber;
	        stepY2 = (endPoint2.y - startPoint2.y) / moveEventNumber;

	        for (int i = 0; i < moveEventNumber; i++) {
	            // update the move events
	            eventTime += EVENT_MIN_INTERVAL;
	            eventX1 += stepX1;
	            eventY1 += stepY1;
	            eventX2 += stepX2;
	            eventY2 += stepY2;

	            pc1.x = eventX1;
	            pc1.y = eventY1;
	            pc2.x = eventX2;
	            pc2.y = eventY2;

	            pointerCoords[0] = pc1;
	            pointerCoords[1] = pc2;

	            event = MotionEvent.obtain(downTime, eventTime,
	                        MotionEvent.ACTION_MOVE, 2, properties, 
	                        pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0);

//	            inst.sendPointerSync(event);
	            dispatchEventToUI(event);
	        }
	    }

	    //step 5
	    pc1.x = endPoint1.x;
	    pc1.y = endPoint1.y;
	    pc2.x = endPoint2.x;
	    pc2.y = endPoint2.y;
	    pointerCoords[0] = pc1;
	    pointerCoords[1] = pc2;

	    eventTime += EVENT_MIN_INTERVAL;
	    
//	    event = MotionEvent.obtain(downTime, eventTime,
//	                MotionEvent.ACTION_POINTER_2_UP, 2, properties, 
//	                pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0);
	    
	    event = MotionEvent.obtain(downTime, eventTime,
	    		MotionEvent.ACTION_POINTER_UP + (pp2.id << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 
	    		2, properties, pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0);
	    
//	    inst.sendPointerSync(event);
	    dispatchEventToUI(event);

	    // step 6
	    eventTime += EVENT_MIN_INTERVAL;
	    event = MotionEvent.obtain(downTime, eventTime, 
	                MotionEvent.ACTION_UP, 1, properties, 
	                pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0 );
//	    inst.sendPointerSync(event);
	    dispatchEventToUI(event);
	}

}
