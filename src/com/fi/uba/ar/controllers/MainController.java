package com.fi.uba.ar.controllers;

import java.io.File;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.ConcurrentHashMap;

import org.opencv.core.Mat;

import rajawali.Object3D;

import android.app.Fragment;

import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.bus.events.MarkerDetectedEvent;
import com.fi.uba.ar.detectors.HandTrackingDetector;
import com.fi.uba.ar.detectors.MarkerDetector;

import com.fi.uba.ar.model.Marker;
import com.fi.uba.ar.model.ObjectAR;
import com.fi.uba.ar.rest.Download3DModelAsyncTask;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.MatUtils;
import com.fi.uba.ar.utils.MessageUtils;
import com.fi.uba.ar.utils.MessageUtils.ToastType;
import com.fi.uba.ar.views.engine3d.LoadModelFragment;

import de.greenrobot.event.EventBus;

/*
 * Controller princpial que se encarga de mantener registro de los markers
 * ya detectados y los objetos 3D correspondientes orquestando
 * las variadas acciones de caracter asincronico que suceden en cada uno de
 * los objetos.
 * Hacemos uso de esto ya que las actividades pueden ser destruidas y creadas
 * nuevamente si el dispositivo es rotado por ejemplo.
 */

//TODO: dado que vuforia confunde los codigos QR al crear el UserDefined tracker, todos
// los detecta como si fuera el mismo. Esto entonces nos limita a que solo vamos a poder
// tener un unico marker por vez y no varios.
// Entonces tenemos que cambiar a que solo haya un unico AR Object en lugar de un hashmap
// Hay que ver que hacer con el tema de los IDs (objectAR, Marker, vuforia Tracker)


//XXX: tendriamos que tener las referencias de los servicios aca y no en la MainActivity?

public class MainController {
	private static final String TAG = "MainController";	
	//XXX: vale la pena tener una lista de objetos? sera util para algo?
	private ConcurrentHashMap<Integer, ObjectAR> ar_objects;
	//private CameraParameters cameraParams = null;
	private MarkerDetector markerDetector;
	private ObjectAR activeObjAR;
	private ObjectAR loadingObjAR;
	private LoadModelFragment fragmentAR;
	private boolean showLoadingObj = false;
	private HandTrackingDetector htd;
		
	public MainController() {
		//XXX: en el refactor por el momento no necesitamos usar el bus
		//EventBus.getDefault().register(this);
		ar_objects = new ConcurrentHashMap<Integer, ObjectAR>();
		activeObjAR = null;
		loadingObjAR = null; // este se crea cuando se registra main activity
		htd = null;
	}
	
	@Override
	protected void finalize ()  {
		//XXX: en el refactor por el momento  no necesitamos usar el bus
		//EventBus.getDefault().unregister(this);
    }
	
	/*
	@Deprecated //XXX: revisar en refactor
	public void setCameraParameters(CameraParameters cp) {
		CustomLog.d(TAG, "CameraParameters set = " + cp);
		this.cameraParams = cp;
	}
	*/

	/*
	@Deprecated //XXX: revisar en refactor
	public boolean isCalibrated() {
		return (this.cameraParams != null);
	}
	*/

	/*
	// Called in a separate thread
	@Deprecated //XXX: borrar en refactor
    public void onEventAsync(MarkerDetectedEvent event) {
        int id = event.marker.getMarkerId();
        CustomLog.d(TAG, "Marker ID = " + id);
        if (ar_objects.containsKey(id)) {
        	CustomLog.d(TAG, "actualizando posicion del marker");
        	//TODO: hay que asegurarnos de que esto no tenga problemas de sincronizacion
        	ar_objects.get(id).updateMarkerPosition(event.marker.getRvec(), event.marker.getTvec());
        } else {
        	CustomLog.d(TAG, "creando y agregando nuevo ObjectAR para este nuevo marker");
        	//XXX: a modo de testing creamos para cada marker un fragment 3D que carga el mismo objeto
        	// y lo asociamos al ObjectAR. Esto es SOLO para ir probando ahora!
        	ObjectAR obj_ar = new ObjectAR(event.marker);
        	Fragment fragment = new LoadModelFragment();
        	obj_ar.setFragment(fragment);
        	//XXX: como aun el fragment no se agrego a la vista y el render no se creo la llamada de aca abajo falla
        	//obj_ar.load3DObjectDEBUG(); // esto va a tomar el objeto 3d cargado por el renderer dentro del fragmet
        	ar_objects.put(event.marker.getMarkerId(), obj_ar);
        	MainApplication.getInstance().getMainActivity().addFragment(fragment);
        }
    }
    */
	
	public void createHandTrackerDetector() {
		htd = new HandTrackingDetector();
		htd.init();
	}
    
	public HandTrackingDetector getHandTrackingDetector() {
		return htd;
	}
	
	public void createLoadingObjectAR() {
		CustomLog.d(TAG, "createLoadingObjectAR");
		ObjectAR obj_ar = new ObjectAR(0, "loading");
//    	Fragment fragment = new LoadModelFragment(true);
		fragmentAR = new LoadModelFragment(true);
    	obj_ar.setFragment(fragmentAR); 
    	loadingObjAR = obj_ar;
    	// agregamos el fragment pero no en el stack asi el boton "atras" nunca lo saca
    	MainApplication.getInstance().getMainActivity().addFragment(fragmentAR, "fragmentAR", false);    	
	}
	
    public int createObjectAR(String qr) { 
    	int id = ar_objects.size() + 1;
    	CustomLog.d(TAG, "creando y agregando nuevo ObjectAR ID = " +  id);
    	ObjectAR obj_ar = new ObjectAR(id, qr);
    	//Fragment fragment = new LoadModelFragment(false);
    	obj_ar.setFragment(fragmentAR);
    	//CustomLog.d(TAG, "LoadModelFragment creado para ObjectAR ID = " +  id + " - fragment = " + fragment);
    	ar_objects.put(id, obj_ar);
    	activeObjAR = obj_ar;    	
    	return id;
    }
    
    public ObjectAR getActiveObjectAR() {
    	return activeObjAR;
    	//XXX: quizas debemos usar ObjectAR objAR = (showLoadingObj) ? loadingObjAR : activeObjAR; ??
    }
    
    public void updateObjectARPos(float[] modelViewMatrix) {
    	ObjectAR objAR = (showLoadingObj) ? loadingObjAR : activeObjAR;
    	if (objAR != null) {
    		//CustomLog.d(TAG, "updateObjectARPos - actualizando objeto AR POS...");
    		objAR.updateMarkerPosition(modelViewMatrix);
    	}
    	else
    		CustomLog.d(TAG, "updateObjectARPos - no hay ningun objeto AR para actualizar POS");
    }
    
    // Restaura la posicion, orientacion y field of view del objeto 3D activo
    public void restoreObjectAROriginalStatus() {
    	ObjectAR objAR = (showLoadingObj) ? loadingObjAR : activeObjAR;
    	if (objAR != null) {
    		objAR.restoreOriginalStatus();
    	}    	
    }
    
    public MarkerDetector getMarkerDetector() {
    	return this.markerDetector;
    }
    
    public void setMarkerDetector(MarkerDetector md) {
    	this.markerDetector = md;
    }
    
    private void setupNewObjectAR(String qrValue) {  
    	// quitamos el fragment del objeto AR anterior si es que lo habia
//    	if (activeObjAR != null) {
//    		MainApplication.getInstance().getMainActivity().removeFragment(activeObjAR.getFragment());
//    	}
    	
    	//XXX: Workaround para poder tocar flecha hacia atras y quitar los objetos
    	// agregamos un fragment vacio para que exista en el stack pero que no haga nada
    	// y sirva para handlear el boton atras donde simplemente se lo saque del stack
    	// mientras los objetos 3D son ocultados
    	MainApplication.getInstance().getMainActivity().addFragment(new Fragment(), "object_ar_" + qrValue, true);
    	
    	// mientras todo el procedimiento de descarga del modelo y parsing sucede de forma asincronia
    	// tenemos que mostrar inmediatamente el objeto 3D loading
    	showLoadingObj = true;
    	fragmentAR.clearObject3D();
    	fragmentAR.showObject3D();
    	
		// creamos un objeto AR para este QR detectado
		int id = createObjectAR(qrValue);
		CustomLog.d(TAG, "setting up new object AR - QR value = " + qrValue);
		CustomLog.d(TAG, "Launching new Download3DModelAsyncTask");		
		new Download3DModelAsyncTask().execute(qrValue);
    }
        
    public Object3D objectARLoadObject3D(File f) {
    	if (activeObjAR != null)
    		//XXX: deberiamos setear aca el objeto 3D si es que se cargo bien?
    		// o lo dejamos como esta ahora que se hace desde Download3DModelAsyncTask
    		// una vez que se recibio el objeto?
    		return activeObjAR.load3DObjectFromFile(f);
    	return null;
    }
    
    public void setObjectARObject3D(Object3D obj) {
    	// ocultamos el objeto loading para mostrar el nuevo objeto
    	showLoadingObj = false;
    	((LoadModelFragment)loadingObjAR.getFragment()).hideObject3d();
    	
    	if (activeObjAR != null) {
    		activeObjAR.setObject3D(obj);
    		// agregamos el fragment solo cuando realmente se cargo el nuevo modelo 3D
    		//MainApplication.getInstance().getMainActivity().addFragment(activeObjAR.getFragment(), "object_ar_" + activeObjAR.getID(), true);    		
    	}
    }
    
    public void hideObjectsAR() {
    	fragmentAR.hideObject3d();
    }
    
	public boolean detectMarkers(Mat m) {

		Vector<Marker> detectedMarkers = new Vector<Marker>();
		markerDetector.detect(m, detectedMarkers, 0.100f, null);

		CustomLog.d(TAG, "detectedMarkers size = " + detectedMarkers.size());

		for (Marker marker : detectedMarkers) {
			ArrayList<String> qr_codes = marker.extractQRCode();
			if (qr_codes.size() > 0) {
				String qr_id = qr_codes.get(0);
				MessageUtils.showToast(ToastType.SUCCESS, "FIUBAAR marker found - ID = " + qr_id);
				// debemos verificar si no es que se hizo un scan del mismo QR que ya esta activo				
				if (activeObjAR != null) {
					String currentQR = activeObjAR.getQRCode();
					if (currentQR.equals(qr_id)) {
						return false; // se trata del mismo QR que ya esta activo asi que no seguimos
					}											
				} 
				
				setupNewObjectAR(qr_id);
				// solo devolvemos true si se trata de un marker con un QR nuevo y en 
				// ese caso Vuforia va a iniciar un nuevo trackersource		
				return true;
				//TODO: determinar si seria logico desactivar la deteccion de manos y gestos
				// en caso de que ya estuviera activada
			}
		}
		MessageUtils.showToast(ToastType.WARNING, "FIUBAAR marker not found");
		return false;
	}
}
