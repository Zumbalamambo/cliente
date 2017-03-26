package com.fi.uba.ar.model;

import java.util.HashMap;

/*
 * Esta clase simplemente contiene una lista de los valores
 * de configuracion para la aplicacion.
 * Todos estos valores seran luego guardados utilizando un setting framgment y el ConfigManager 
 */

@SuppressWarnings("unchecked")
public class Configuration {
	// constantes
	public static final String CONFIG_FILENAME = "FiubaAR_settings.conf";
	public static final String SERVER_URL = "server_url";
	
	// valores por defecto para inicializas las preferencias
	public static HashMap<String, String> defaultValues;
	static {
		defaultValues = new HashMap<String, String>();		
		//XXX: esta URL en la version final deberia apuntar a algun dominio en internet.
		// Quizas podriamos registrar una github page que sea algo como fiubaar.github.io 
		// donde tengamos una pagina que haga un redirect a algun VPS gratuito donde hosteamos
		// una version de la aplicacion server
		defaultValues.put(SERVER_URL, "http://192.168.0.14:9000/"); 
		
	}
}
