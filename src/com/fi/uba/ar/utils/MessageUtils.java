package com.fi.uba.ar.utils;

import android.app.Activity;
import android.widget.Toast;

import com.fi.uba.ar.MainApplication;

/*
 * Clase de utilidades para mensajes informativos al usuario
 * Provee metodos convenientes para el uso de Toasts 
 * y tambien de Notifications
 * 
 */

public class MessageUtils {
	// TODO: implementar algo de notifications
	// https://developer.android.com/guide/topics/ui/notifiers/notifications.html

	private final static String TAG = "MessageUtils";
	private final static Activity activity = MainApplication.getInstance()
			.getMainActivity();

	public enum ToastType {
		NORMAL, INFO, WARNING, SUCCESS, ERROR, CUSTOM
	}

	public static void showToast(final ToastType type, final String msg) {
		// todos los toats van a ser de length short
		// Nos aseguramos que los Toasts siempre se ejecuten en el thread UI
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Toast t = null;
				switch (type) {
					case INFO:
						t = Toasty.info(activity, msg);
						break;
					case WARNING:
						t = Toasty.warning(activity, msg);
						break;
					case SUCCESS:
						Toasty.success(activity, msg);
						break;
					case ERROR:
						Toasty.error(activity, msg);
						break;
					case CUSTOM:
						// TODO: que hacemos aca? tendriamos que recibir mas
						// parametros quizas
						// t = Toasty.custom(context, message, icon, textColor,
						// duration, withIcon);
						break;
					case NORMAL:
					default:
						Toasty.normal(activity, msg);
						break;
				}
				t.show();
			}
		});

	}

	
}
