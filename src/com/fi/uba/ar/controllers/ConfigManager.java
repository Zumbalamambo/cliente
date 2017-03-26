package com.fi.uba.ar.controllers;

import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.fi.uba.ar.R;
import com.fi.uba.ar.model.Configuration;
import com.fi.uba.ar.utils.CustomLog;

/*
 * Mantiene las configuraciones globales de la application.
 * Basicamente actua como un wrapper alrededor de SharedPreferences
 */

// Estamos usando esto https://developer.android.com/guide/topics/ui/settings.html
// que ya tiene toda la logica para manejar las preferencias y como hacer las vistas con fragments

//TODO: agregar el parametro de frameGroupSize para la cantidad de frames a procesar por vez para la mano

public class ConfigManager {
	
	private static final String TAG = "ConfigManager";
	private SharedPreferences preferences ;
			
	public ConfigManager(Context ctx) {
		//XXX: hay que ver si realmente esta guardando los settings en el archivo que yo le digo o si usa algo por defecto
		PreferenceManager.setDefaultValues(ctx, Configuration.CONFIG_FILENAME, Context.MODE_PRIVATE, R.xml.preferences, false);
		//preferences = ctx.getSharedPreferences(Configuration.CONFIG_FILENAME, Context.MODE_PRIVATE);	
		preferences = PreferenceManager.getDefaultSharedPreferences(ctx);
		if (! preferences.getBoolean("settings_initialized", false) )
			saveDefaultValues();
	}
	
	private void saveDefaultValues() {
		SharedPreferences.Editor editor = preferences.edit();
		editor.putBoolean("settings_initialized", true);				
		for (Map.Entry<String, String> entry : Configuration.defaultValues.entrySet()) {
			editor.putString(entry.getKey(), entry.getValue());			
		}		
		editor.commit();
	}
	
	//TODO: estamos asumiendo que todos los settings son strings y no hay ints, booleans, etc
	public String getValue(String key) {
		return preferences.getString(key, "NULL");
	}
	
	public void setValue(String key, String value) {
		SharedPreferences.Editor editor = preferences.edit();
		editor.putString(key, value);
		editor.commit();		
	}
	
	// solo para debug
	public void dumpSettings() {
		CustomLog.d(TAG, "----- SETTINGS -----" );
		for (Map.Entry<String, String> entry : Configuration.defaultValues.entrySet()) {
			String value = preferences.getString(entry.getKey(), "NOT FOUND");
			CustomLog.d(TAG, entry.getKey() + " = " + value );
		}	
	}
}
