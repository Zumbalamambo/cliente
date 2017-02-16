package com.fi.uba.ar.utils;

import android.util.Log;
import rajawali.util.RajLog;

/*
 * Logger custom de FiubaAR para poder habilitar y deshabilitar
 * a pedido y manejar niveles
 * La idea es tomada de rajawali.util.RajLog
 * 
 * Logging deshabilitdado por defecto
 */

//XXX: considerar integrar esta libreria https://github.com/orhanobut/logger

public class CustomLog {
	private final static String TAG = "FIUBAAR_";
	
	private static boolean _logEnabled = false;
	
	public static final void enableLogging() {
		_logEnabled = true;
		RajLog.enableLogging(true);
		Log.d(TAG + "CustomLog", "Logging habilitado...");
		RajLog.systemInformation();
	}
	
	public static final void disableLogging() {
		_logEnabled = false;
		RajLog.enableLogging(false);
		Log.d(TAG + "CustomLog", "Logging deshabilitado...");
	}
	
	public static void d(String tag, String msg) {
		if(_logEnabled)
			Log.d(TAG + tag, msg);
	}
	
	public static void e(String tag, String msg) {
		if(_logEnabled)
			Log.e(TAG + tag, msg);
	}
	
	public static void i(String tag, String msg) {
		if(_logEnabled)
			Log.i(TAG + tag, msg);
	}
	
	public static void v(String tag, String msg) {
		if(_logEnabled)
			Log.v(TAG + tag, msg);
	}
	
	public static void w(String tag, String msg) {
		if(_logEnabled)
			Log.w(TAG + tag, msg);
	}
	
}
