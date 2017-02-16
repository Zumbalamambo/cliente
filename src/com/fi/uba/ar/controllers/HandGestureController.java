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
	
	private static final long EVENT_MIN_INTERVAL = 1;
	private static HandGestureController instance = null;
	
	// mantenemos un historial de los ultimos 5 conjuntos de dedos detectados
	private CircularFifoQueue<List<Point>> previous_points;
	
	private boolean down_event_sent = false;
	private int current_gesture_finger_count = 0;
	
	public static HandGestureController getInstance() {
		if (instance == null)
			new HandGestureController();
		return instance;
	}
	
	public HandGestureController() {
		instance = this;
		previous_points = new CircularFifoQueue<List<Point>>(5);
	}
	
	/*
	 * Este metodo calcula y devuelve la cantidad promedio
	 * de dedos que existen en el historial de puntos guardados
	 */
	private int getAverageFingerCount() {
		int avg = 0;
		
		if (!previous_points.isEmpty()) {
			for (List<Point> points: previous_points) {
				avg += points.size();			
			}
			avg = avg / previous_points.size();
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
	 * tiene el mismo valor y corresponden al parametro
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
		
		//XXX: tenemos que implementar esto de otra forma tal como se hace en el 
		// metodo de prueba que funciona bien: generateZoomGesture	
		
		// hay que adaptar este metodo para que genere eventos para todos los dedos 
		// como si fuera algo multitouch
		int current_finger_count = points.size();
		
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
				if (checkCountChanged(current_finger_count)) {
					// Tenemos que enviar eventos ACTION_UP para indicar
					// que el gesto termino y que los detectors correspondiente 
					// lo puedan procesar
					
					// seteamos este flag para poder empezar de nuevo a detectar
					// un posible gesto nuevo a partir de la proxima deteccion de dedos
					down_event_sent = false; 
					dispatchUpEvents(points);					
					
				} else {
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
		}
		else { 
			// quiere decir que tenemos que "iniciar" un posible gesto
			// y para eso todos los dedos en este caso tienen que ser
			// creados con MotionEvent.ACTION_DOWN (y pointer si hay mas de un dedo)
			down_event_sent = true;
			 // limpiamos el historial ya que eso es usado para analizar si 
			// "seguimos en un movimiento" siempre y cuando se hayan detectado la misma cantidad de dedos
			previous_points.clear(); 
			current_gesture_finger_count = current_finger_count ;
			dispatchDownEvents(points);
			
		}


		// guardamos estos puntos en el historial
		previous_points.add(points);
	}
	
	
	
	private void dispatchDownEvents(List<Point> points) {
		int finger_count = points.size();
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
		event = MotionEvent.obtain(
				SystemClock.uptimeMillis(), 
				SystemClock.uptimeMillis(), 
                MotionEvent.ACTION_DOWN, 1, properties, 
                pointerCoords, 0,  0, 1, 1, 0, 0, 0, 0 );
		
		dispatchEventToUI(event);

		// El resto de los dedos va con un pointer_down y un id diferente
		for (int x = 1; x < finger_count; x++) {
			int id = x+1;
			event = MotionEvent.obtain(
					SystemClock.uptimeMillis(), 
					SystemClock.uptimeMillis(), 
		    		MotionEvent.ACTION_POINTER_DOWN + (id << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 
		    		id, properties, pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0);
			dispatchEventToUI(event);
		}
		
	}
	
	private void dispatchUpEvents(List<Point> points) {
		
		int finger_count = points.size();
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
		for (int x = finger_count; x > 0; x--) {
			int id = x+1;
			event = MotionEvent.obtain(
				 	SystemClock.uptimeMillis(), 
					SystemClock.uptimeMillis(), 
		    		MotionEvent.ACTION_POINTER_UP + (id << MotionEvent.ACTION_POINTER_INDEX_SHIFT), 
		    		id, properties, pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0);		    
		    dispatchEventToUI(event);
		}

		// El primer punto de la lista lo enviamos como ACTION_UP
	    event = MotionEvent.obtain(SystemClock.uptimeMillis(), 
					SystemClock.uptimeMillis(), 
	                MotionEvent.ACTION_UP, 1, properties, 
	                pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0 );
	    dispatchEventToUI(event);
	}
	
	private void dispatchMoveEvents(List<Point> points) {
		
		// Para el caso del move enviamos un unico evento con todos los points
		
		int finger_count = points.size();
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
		/*
		 * Estamos obteniendo una excepcion aca
		 * 
02-12 20:37:24.429: E/AndroidRuntime(12299): java.lang.IllegalArgumentException: pointerProperties array must be large enough to hold all pointers
02-12 20:37:24.429: E/AndroidRuntime(12299): 	at android.view.MotionEvent.nativeInitialize(Native Method)
02-12 20:37:24.429: E/AndroidRuntime(12299): 	at android.view.MotionEvent.obtain(MotionEvent.java:1497)
02-12 20:37:24.429: E/AndroidRuntime(12299): 	at com.fi.uba.ar.controllers.HandGestureController.dispatchMoveEvents(HandGestureController.java:311)
		 */
		
		event = MotionEvent.obtain(
				SystemClock.uptimeMillis(), 
				SystemClock.uptimeMillis(), 
	    		MotionEvent.ACTION_MOVE, 
	    		finger_count+1, properties, pointerCoords, 0, 0, 1, 1, 0, 0, 0, 0);
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
					main_activity.dispatchTouchEvent(e);
			}
		});
	}
	/*
	 * Esteamos obteniendo una excepcion en el run UI
02-12 20:37:24.128: W/dalvikvm(12299): threadid=1: thread exiting with uncaught exception (group=0x4169ec08)
02-12 20:37:24.128: W/System.err(12299): java.lang.IllegalArgumentException: pointerIndex out of range
02-12 20:37:24.138: W/System.err(12299): 	at android.view.MotionEvent.nativeGetPointerId(Native Method)
02-12 20:37:24.138: W/System.err(12299): 	at android.view.MotionEvent.getPointerId(MotionEvent.java:2144)
02-12 20:37:24.138: W/System.err(12299): 	at android.view.ViewGroup.dispatchTouchEvent(ViewGroup.java:2078)
02-12 20:37:24.138: W/System.err(12299): 	at com.android.internal.policy.impl.PhoneWindow$DecorView.superDispatchTouchEvent(PhoneWindow.java:2295)
02-12 20:37:24.138: W/System.err(12299): 	at com.android.internal.policy.impl.PhoneWindow.superDispatchTouchEvent(PhoneWindow.java:1622)
02-12 20:37:24.138: W/System.err(12299): 	at android.app.Activity.dispatchTouchEvent(Activity.java:2565)
02-12 20:37:24.138: W/System.err(12299): 	at com.fi.uba.ar.controllers.HandGestureController$1.run(HandGestureController.java:331) 
	 */
	
	
	
	
	//XXX: metodo de prueba obtenido de:
	// https://stackoverflow.com/questions/11523423/how-to-generate-zoom-pinch-gesture-for-testing-for-android
	// Comprobamos que en efecto funciona correctamente y causa que se haga un zoom en un obj3D tal
	// como si lo hubieramos hecho real y fisicamente sobre la pantalla
	
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
