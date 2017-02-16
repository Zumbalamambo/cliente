package com.fi.uba.ar.views;

import java.util.ArrayList;
import java.util.List;

import com.fi.uba.ar.utils.CustomLog;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.WindowManager;

// https://github.com/codepath/android_guides/wiki/Basic-Painting-with-Views

public class HandDebugView extends SurfaceView {
	private static final String TAG = "HandDebugView";
	// setup initial color
	private final int paintColor = Color.GREEN;
	// defines paint and canvas
	private Paint drawPaint;
	// Store circles to draw each time the user touches down
	private List<Point> circlePoints;

	public HandDebugView(Context context) {
		super(context);
		setFocusable(true);
		setFocusableInTouchMode(true);
		setupPaint();
		circlePoints = new ArrayList<Point>();
		// https://stackoverflow.com/questions/7781892/own-defined-layout-ondraw-method-not-getting-called/7784369#7784369
		// https://stackoverflow.com/questions/3343912/custom-ondraw-method-not-called
		//XXX: esto era realmente importante de setear aca!
		this.setWillNotDraw(false);
		CustomLog.d(TAG, "view created");
		
	}

	public HandDebugView(Context context, AttributeSet attrs) {
		super(context, attrs);
		setFocusable(true);
		setFocusableInTouchMode(true);
		setupPaint();
		circlePoints = new ArrayList<Point>();
		this.setWillNotDraw(false);
		CustomLog.d(TAG, "view created");
	}

	// Setup paint with color and stroke styles
	private void setupPaint() {
		drawPaint = new Paint();
		drawPaint.setColor(paintColor);
		drawPaint.setAntiAlias(true);
		drawPaint.setStrokeWidth(5);
		drawPaint.setStyle(Paint.Style.STROKE);
		drawPaint.setStrokeJoin(Paint.Join.ROUND);
		drawPaint.setStrokeCap(Paint.Cap.ROUND);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		
//		canvas.drawCircle(209, 140, 20, drawPaint);
//		drawPaint.setColor(Color.GREEN);
//		canvas.drawCircle(50, 150, 20, drawPaint);
//		drawPaint.setColor(Color.BLUE);
//		canvas.drawCircle(50, 250, 20, drawPaint);
		//CustomLog.d(TAG, "Canvas - height = " + canvas.getHeight() + " - width = " + canvas.getWidth());
		// 08-21 18:28:45.082: D/FIUBAAR_HandDebugView(8569): Canvas - height = 1066 - width = 758

		
		// http://stackoverflow.com/questions/1016896/get-screen-dimensions-in-pixels
		// http://stackoverflow.com/questions/19155559/how-to-get-android-device-screen-size
		
		/*
		WindowManager wm = (WindowManager) this.getContext().getSystemService(Context.WINDOW_SERVICE);
		DisplayMetrics dm = new DisplayMetrics();
		wm.getDefaultDisplay().getMetrics(dm);
		int width=dm.widthPixels;
		int height=dm.heightPixels;
		int dens=dm.densityDpi;
		double wi=(double)width/(double)dens;
		double hi=(double)height/(double)dens;
		double x = Math.pow(wi,2);
		double y = Math.pow(hi,2);
		double screenInches = Math.sqrt(x+y);
		CustomLog.d(TAG, "screen - widthPixels = " + width + " - heightPixels = " + height + " - inches = " + screenInches);
		*/
		
		if (!circlePoints.isEmpty()) {
			CustomLog.d(TAG, "drawing circles...");
			canvas.drawColor(Color.TRANSPARENT); // limpiamos el canvas
			for (Point p : circlePoints) {
				canvas.drawCircle(p.x, p.y, 20, drawPaint);
			}		
		}
	}

	//XXX: siendo que ahora para los gestos estamos enviando un TouchEvent
	// por cada dedo necesitamos sacar este metodo para que no nos dibuje solo
	// un unico circulo y muestre realmente los dedos
	
	// Append new circle each time user presses on screen
	/*
	@Override
	public boolean onTouchEvent(MotionEvent event) {
		circlePoints.clear();
		
		float touchX = event.getX();
		float touchY = event.getY();
		circlePoints.add(new Point(Math.round(touchX), Math.round(touchY)));
		// indicate view should be redrawn
		postInvalidate();
		CustomLog.d(TAG, "Touch image coordinates: (" + touchX + ", " + touchY + ")");
		return true;
	}
	*/
	
	 public void addFingerTipPoints(List<Point> points) {
		 circlePoints.clear();
		 circlePoints.addAll(points);
		 CustomLog.d(TAG, "se agregaron " + points.size() + " dedos a la lista de la vista");
		 postInvalidate(); // para forzar a que redibuje la vista
		 
	 }

}
