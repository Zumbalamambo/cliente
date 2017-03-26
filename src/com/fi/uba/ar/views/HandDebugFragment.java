package com.fi.uba.ar.views;

import java.util.List;

import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.R;

import android.app.Fragment;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

//TODO: el uso del HandDebugFragment tendria que ser opcional... como algo de debug
// Hay que agregar algo en la UI para poder elegir activar/desactivar
public class HandDebugFragment extends Fragment {

	private static final String TAG = "HandDebugFragment";

	private static HandDebugFragment instance = null;
	
	private static boolean enabled = false;
	
	HandDebugView handDebugView;
	
	public static void enable() {
		if (!enabled) {
			enabled = true;
			MainApplication.getInstance().getMainActivity().launchDebugFragment(null);
		}
	}
	
	public static void disable() {	
		enabled = false;
		if (instance != null)
			MainApplication.getInstance().getMainActivity().removeFragment(instance);
	}
	
	public static HandDebugFragment getInstance() {
		if (enabled) {
			if (instance == null)
				instance = new HandDebugFragment();
			return instance;
		}
		else 
			return null;
	}
	
	private HandDebugFragment() {
		super();
		instance = this;
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.hand_debug_fragment, container, false);
		
		handDebugView = (HandDebugView) view.findViewById(R.id.handDebugView);
		// http://stackoverflow.com/questions/5391089/how-to-make-surfaceview-transparent
		handDebugView.setZOrderOnTop(true); // necessary, pero hace que no se pueda poner algo ontop
		handDebugView.getHolder().setFormat(PixelFormat.TRANSPARENT); // hago el fondo transparente
		handDebugView.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.FILL_PARENT));
		handDebugView.setWillNotDraw(false);
		// https://stackoverflow.com/questions/7781892/own-defined-layout-ondraw-method-not-getting-called/7784369#7784369
		// https://stackoverflow.com/questions/3343912/custom-ondraw-method-not-called
		//view.setWillNotDraw(false);
		
		return view;
	}
	
	public void addFingerTipPoints(List<Point> points) {
		handDebugView.addFingerTipPoints(points);
	}
}
