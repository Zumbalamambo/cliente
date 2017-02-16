package com.fi.uba.ar.rest;

import java.io.File;

import android.os.Looper;

import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.model.Configuration;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.FileUtils;
import com.loopj.android.http.*;
import org.apache.http.Header;
import org.json.*;

// http://stackoverflow.com/questions/24646635/android-loopj-async-http-crashes-after-1-4-5-update

//TODO: verificar que pasa con el cliente cuando recibe redirects (302)
public class RestClient {

	protected static final String TAG = "RestClient";

	private String BASE_URL;

	private AsyncHttpClient syncClient = null;
	private AsyncHttpClient asyncClient = null;

	public RestClient(String base_url) {
		syncClient = new SyncHttpClient();
		asyncClient = new AsyncHttpClient();

		if (base_url != null)
			BASE_URL = base_url;
		else
			BASE_URL = MainApplication.getInstance().getConfigManager().getValue(Configuration.SERVER_URL);
	}

	private AsyncHttpClient getClient() {
		// Return the synchronous HTTP client when the thread is not prepared
		if (Looper.myLooper() == null)
			return syncClient;
		return asyncClient;
	}

	public void get(String url, RequestParams params,
			AsyncHttpResponseHandler responseHandler) {
		String final_url = getAbsoluteUrl(url);
		CustomLog.d(TAG, "GET " + final_url);
		getClient().get(final_url, params, responseHandler);
	}

	// http://stackoverflow.com/questions/13052036/posting-json-xml-using-android-async-http-loopj
	public void post(String url, RequestParams params, AsyncHttpResponseHandler responseHandler) {
		String final_url = getAbsoluteUrl(url);
		CustomLog.d(TAG, "POST " + final_url);
		getClient().post(final_url, params, responseHandler);
	}

	private String getAbsoluteUrl(String relativeUrl) {
		if (relativeUrl.startsWith("/") && BASE_URL.endsWith("/"))
			relativeUrl = relativeUrl.substring(1);
		return BASE_URL + relativeUrl;
	}

	public File downloadFile(String url, String filename) {
		File cacheFile = new File(FileUtils.getCacheFolder(), filename);
		final boolean[] downloaded = new boolean[1];
		downloaded[0] = false;

		getClient().get(getAbsoluteUrl(url), new FileAsyncHttpResponseHandler(cacheFile) {
			@Override
			public void onSuccess(int statusCode, Header[] headers, File response) {
				// Do something with the file `response`
				// TODO: chequear si el archivo se escribio o no...
				CustomLog.d(TAG, "downloadFile - response = " + response);
				downloaded[0] = true;
			}

			@Override
			public void onFailure(int statusCode, Header[] headers, Throwable exception, File response) {
				CustomLog.e(TAG, "downloadFile - onFailure = " + response
						+ '\n' + exception.getMessage());
				//TODO: deberiamos de alguna forma notificar al usuario que fallo la descarga
				// Quizas podemso usar los "toast" messages como los que usamos para mostrar el QR
			}
			/*
			@Override
			public void onFailure(int statusCode, Header[] headers, byte[] bytes, Throwable exception) {
				
			}
			*/
		});

		if (downloaded[0])
			return cacheFile;

		return null;
	}

	// dummy response handler
	private static JsonHttpResponseHandler defaultJsonHttpResponseHandler = new JsonHttpResponseHandler() {
		@Override
		public void onSuccess(int statusCode, Header[] headers, JSONObject response) {
			// If the response is JSONObject instead of expected JSONArray
		}

		@Override
		public void onSuccess(int statusCode, Header[] headers, JSONArray response) {
			// Pull out the first event on the public timeline
			JSONObject firstEvent;
			try {
				firstEvent = (JSONObject) response.get(0);
				String tweetText = firstEvent.getString("text");
				// Do something with the response
				System.out.println(tweetText);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	};
}
