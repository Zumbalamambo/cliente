package com.fi.uba.ar.rest;

import java.io.File;
import java.util.Arrays;

import org.apache.commons.io.FilenameUtils;
import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import rajawali.Object3D;

import android.os.AsyncTask;
import android.util.Log;

import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.utils.Constants;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.FileUtils;
import com.fi.uba.ar.utils.MessageUtils;
import com.fi.uba.ar.utils.MessageUtils.ToastType;
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;

// TODO: https://bitbucket.org/fiubaar/fiubaar/issue/19/definir-formato-json-y-datos-que-va-a
// hay que definir bien que nos va a responder el server y hacer un cliente acorde
// lo mas logico seria bajar un zip siempre y que ahi dentro esten todos los archivos necesarios.
// y en la respuesta json tengamos el listado completo de los archivos que tiene dentro y que se
// deben usar para cargar el modelo 3d completo

//XXX: Esto probablemente se pueda implementar de mucho mejor manera usando reactive programming
// usando RxJava y Retrofit, por ejemplo como se explica en
// http://blog.danlew.net/2014/10/08/grokking-rxjava-part-4/
// pero quedara como posibles mejoras a futuro


public class Download3DModelAsyncTask extends AsyncTask<String, Void, Object3D> {
	private final static String TAG = "Download3DModelAsyncTask"; 
	@Override
	protected Object3D doInBackground(String... data) {
		// mediante un rest client obtenemos el modelo 3D que se nos indica
		// La data que se nos pasa es obtenida de leer el codigo QR de un marker
		// La data es el Project ID cargado en el server y la API definida solo necesita ese dato

		//XXX: un String array para poder modificar algo que sea final
		// http://stackoverflow.com/questions/4732544/why-are-only-final-variables-accessible-in-anonymous-class
		final String[] model_file = new String[1]; 	
		model_file[0] = null;

		// usando la API rest del server obtenemos la informacion necesaria para el modelo 3D que
		// corresponde a este codigo QR
		RestClient rClient = new RestClient(null);
		rClient.get("/3dmodels/info/"+data[0], new RequestParams(), new JsonHttpResponseHandler() {
	        @Override
	        public void onSuccess(int statusCode, Header[] headers, JSONObject response) {	        	
				try {
					model_file[0] = response.getString("3DModelFile");
					 CustomLog.d(TAG, "3DModelFile = " + model_file[0]);
				} catch (JSONException e) {
					CustomLog.e(TAG, "An exception occurred while getting 3D model info from server");
					CustomLog.e(TAG, Log.getStackTraceString(e));
					MessageUtils.showToast(ToastType.ERROR, "Error downloading 3D model from server");
				}
	        }
	        
	        @Override
	        public void onFailure(int statusCode, Header[] headers, Throwable t, JSONObject response) {									
				CustomLog.d(TAG, "Failed to download 3DModelFile = " + model_file[0] + " - Error: " + t.getMessage());
				CustomLog.e(TAG, Log.getStackTraceString(t));
				MessageUtils.showToast(ToastType.ERROR, "Failed to download 3D model from server");
				//TODO: deberiamos de alguna forma notificar al usuario que fallo la descarga
				// Quizas podemso usar los "toast" messages como los que usamos para mostrar el QR
	        }
	    });
		
		//XXX: estamos seguros que el GET siempre lo hace con el cliente sync?
		// Si esto lo hace con el Async quizas no tenemos el model_file cuando llegamos aca
		
		// si pudimos obtener la respuesta del server y tenemos la informacion
		// entonces descargamos el archivo zip del modelo 3D
		
		if (model_file[0] != null) {
			// Tenemos el nombre de archivo del modelo 3D que tenemos que bajar
			
			//String model_filename = FilenameUtils.getBaseName(model_file[0]);
			//String ext = FilenameUtils.getExtension(model_file[0]);
			
			String fileURL = "/3dmodels/file/" + data[0];
			// descargamos el archivo al cache de la app con el filename indicado
			File downloadedFile = rClient.downloadFile(fileURL, model_file[0]);
			
			// si se pudo descargar el archivo zip del server lo procesamos
			if (downloadedFile != null) {
				// Obtenemos un File que apunta al modelo 3D descargado en Cache
				// Como lo que descargamos es un zip, tenemos que descomprimir
				File output_dir = FileUtils.unpackZip(downloadedFile);
				if (output_dir != null) {
					// buscamos en el directorio descomprimido por algun archivo 
					// con extension conocida y soportada de modelo 3D 
					MessageUtils.showToast(ToastType.SUCCESS, "3D model downloaded successfully");
					return loadFromDirectory(output_dir);					
				}
				//TODO: habria que manejar el caso en el que el zip no tiene dentro ningun archivo con extension
				// conocida y soportada y no hay nada que cargar como modelo 3D
				// Es como si fuera un archivo invalido..
			} else {
				MessageUtils.showToast(ToastType.ERROR, "Failed to download 3D model from server");
			}
		}
		return null;
	}

	@Override
    protected void onPostExecute(Object3D obj3d) {
		// Este metodo es llamado con lo que devuelve doInBackground y eso es la llamada a 
		// MainController.objectARLoadObject3D. Ahora entonces
        // hacemos el seteo del objeto aca que es el thread UI y eso hace el addChild al renderer
		
		if (obj3d != null) {
			CustomLog.d(TAG, "onPostExecute - recibimos obj3d y lo cargamos como el objeto AR activo en el controller");
			//MessageUtils.showToast(ToastType.INFO, "3D model loaded and now setting to marker");
			MainApplication.getInstance().getMainController().setObjectARObject3D(obj3d);
		}	
		else
			// esto seria algun caso en el que hubo algun tipo de error
			// no mostramos este mensaje como toast porque sino son muchas notificaciones juntas
			//MessageUtils.showToast(ToastType.ERROR, "Failed to load 3D model from the downloaded file"); 
			CustomLog.d(TAG, "onPostExecute - recibimos obj3d null"); 
    }
	
	private Object3D loadFromDirectory(File dir) {
		
		for (File f: dir.listFiles()) {
			// si hay subdirectorios buscamos dentro de forma recursiva
			if (f.isDirectory()) {
				Object3D obj = loadFromDirectory(f);
				if (obj != null)
					return obj;
			}
						
			String ext = FilenameUtils.getExtension(f.getName());
			if (Constants.VALID_3D_FILE_EXTENSIONS.contains(ext.toUpperCase())) {
				// Si es un tipo de model 3D valido, entonces lo cargamos
				// Esto hace la carga y parseo y como estamos actualmente en un async task no frenamos el thread UI
				MessageUtils.showToast(ToastType.INFO, "Loading 3D model from file...");
				return MainApplication.getInstance().getMainController().objectARLoadObject3D(f);
			}
		}		
		return null;
	}

}
