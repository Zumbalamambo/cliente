package com.fi.uba.ar;

import com.fi.uba.ar.controllers.MainActivity;
import com.fi.uba.ar.controllers.MainController;
import com.fi.uba.ar.controllers.ConfigManager;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.HeapDumpingUncaughtExceptionHandler;

import de.greenrobot.event.EventBus;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.support.v4.content.LocalBroadcastManager;

import com.facebook.stetho.Stetho;

/*
 * Esta clase MainApplication puede ser utilizada en el caso 
 * en el que sea necesario mantener un estado global.
 * Solo va a ser creada si agregamos un nombre al tag <application> del manifest
 * 
 * http://stackoverflow.com/questions/987072/using-application-context-everywhere
 * http://android-developers.blogspot.com.ar/2009/01/avoiding-memory-leaks.html
 * */
public class MainApplication extends Application {
	private static MainApplication sInstance;
	
	private MainActivity mainActivity = null;
	private Context appContext = null;
	private EventBus eventBus = null;
	private MainController mainController = null;
	private ConfigManager configManager = null;
	private Instrumentation instrumentation = null;
	
	//XXX: tendriamos que tener referencias a los servicios aca para que 
	// cualquiera pueda obtenerlos? habra problema de threading?
	//XXX: finalmente no se esta haciendo uso de los servicios asi que el comentario anterior no aplica 
	
	// Flags globales
	private boolean handDetectionEnabled = true; //TODO: implementar switch para activar/desactivar esto
	private boolean gestureDetectionEnabled = false;
	
	
	@Override
	public void onCreate() {
		super.onCreate();
		sInstance = this;
		
		 Stetho.initializeWithDefaults(this);
		
		// EventBus 2.3 tiene builder.. pero 2.2 no..
		//this.eventBus = EventBus.builder().logNoSubscriberMessages(false).sendNoSubscriberEvent(false).build();
		this.eventBus = EventBus.getDefault();
		this.mainController = new MainController();
		// https://www.novoda.com/blog/debugging-memory-leaks-on-android-for-beginners-programmatic-heap-dumping-part-1/
        Thread.currentThread().setUncaughtExceptionHandler(
                new HeapDumpingUncaughtExceptionHandler(getApplicationInfo().dataDir));
		
        this.instrumentation = new Instrumentation();
		//TODO: tenemos que hacer algun menu de configuracion donde podamos elegir estas cosas
		CustomLog.enableLogging();
	}
	
	@Override
	public void onTerminate() {
		// hacemos lo que haga falta antes de salir
		
	
		// finalmente llamar al del padre
		super.onTerminate();
	}	
	
	public static MainApplication getInstance() {
		return sInstance;
    }
	
	public EventBus getEventBus() {
		return this.eventBus;
	}
	
	public MainController getMainController() {
		return this.mainController;
	}
	
	public LocalBroadcastManager getLocalBroadcastManager(){
		if (this.mainActivity == null)
			return null;
		else {
			return LocalBroadcastManager.getInstance(this.appContext);
		}		
	}
	
	public void registerMainActivity(MainActivity activity) {
		this.mainActivity = activity;
		// este contexto sera lo mismo que hacer this.getApplicationContext()??
		//XXX: sera esto una causa de memory leak si mantenemos la referencia al contexto?
		this.appContext = activity.getApplicationContext(); 
		this.configManager = new ConfigManager(this.appContext);
		this.mainController.createLoadingObjectAR();
	}
	
	public void unregisterMainActivity(MainActivity activity) {
		if (this.mainActivity.hashCode() == activity.hashCode()) {
			this.mainActivity = null;
			this.appContext = null; 
		}
	}
	
	public Context getAppContext() {
		return this.appContext;
	}
	
	public MainActivity getMainActivity() {
		return this.mainActivity;
	}
	
	public ConfigManager getConfigManager() {
		return this.configManager;
	}
	
	public Instrumentation getInstrumentation() {
		return this.instrumentation;
	}
	
	//XXX: como que quizas tener esto aca es mucha vuelta innecesaria
	public boolean isHandDetectionEnabled() {
		return handDetectionEnabled;
	}
	
	public void setHandDetectionEnabled(boolean value) {		
		handDetectionEnabled = value;
	}
	
	public void toggleHandDetectionEnabled(boolean value) {
		handDetectionEnabled = !handDetectionEnabled;
	}
	
	public boolean isGestureDetectionEnabled() {
		return gestureDetectionEnabled;
	}
	
	public void toggleGestureDetectionEnabled() {
		gestureDetectionEnabled = !gestureDetectionEnabled;
		com.fi.uba.ar.controllers.HandGestureController.getInstance().toggleGestureDetection();
	}
	
	public void closeApplication() {
		getMainActivity().finishAffinity();
	}
}
