package com.fi.uba.ar.views;

import java.util.ArrayList;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;

import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.R;
import com.fi.uba.ar.utils.CommonUtils;
import com.fi.uba.ar.utils.Constants;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.MatUtils;
import com.fi.uba.ar.model.JavaCameraFrame;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;

import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.FrameLayout.LayoutParams;
import android.widget.RelativeLayout;

public class DebugFragment extends Fragment {

	private static final String TAG = "DebugFragment";

	private Bitmap mCacheBitmap;

	protected float mScale = 0;

	SurfaceView mSurfaceView;
	
	List<Rect> ListOfRect = new ArrayList<Rect>();

	public DebugFragment() {
		super();
	}

	// Este handler va a recibir intents que indiquen que un Mat debe ser
	// dibujado en pantalla y lo haremos llamando al metodo drawFrame
	private BroadcastReceiver mDrawFrameReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			CustomLog.d(TAG, "Got intent: " + intent);
			// Get extra data included in the Intent
			byte[] matBytes = intent.getByteArrayExtra("Mat");
			//Log.d(TAG, "Got Mat bytes: " + CommonUtils.bytesToHex(matBytes));
			int mFrameWidth = intent.getIntExtra("width", 600);
			int mFrameHeight = intent.getIntExtra("height", 800);
			Mat m = MatUtils.matFromBytes(mFrameWidth, mFrameHeight, matBytes, false, -1);
			drawFrame(m);
//			JavaCameraFrame frame = new JavaCameraFrame(m, mFrameWidth, mFrameHeight);			
//			drawFrame(frame.rgba());
		}

	};

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.debug_fragment, container, false);
		
		view.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {

				int x, y;

				x = (int) (event).getX();
				y = (int) (event).getY();
				Rect r = new Rect(
							new Point(x - 100, y - 100), 
							new Point(x + 100, y + 100));
				ListOfRect.add(r);

				CustomLog.i(TAG, "Touch image coordinates: (" + x + ", " + y + ")");
				
				//drawBlackBackground(); funciona

				return true;
			}

		});
		
		mSurfaceView = (SurfaceView) view.findViewById(R.id.debug_surfaceview);
		// http://stackoverflow.com/questions/5391089/how-to-make-surfaceview-transparent
		mSurfaceView.setZOrderOnTop(true); // necessary, pero hace que no se pueda poner algo ontop
		mSurfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT); // hago el fondo transparente
		mSurfaceView.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.FILL_PARENT, RelativeLayout.LayoutParams.FILL_PARENT));

		registerReceivers();
		
		Log.d(TAG, "surfaceview width = " + mSurfaceView.getWidth());
		Log.d(TAG, "surfaceview height = " + mSurfaceView.getHeight());
		
		return view;
	}
	
	public void clearSurfaceCanvas() {
		Canvas canvas = mSurfaceView.getHolder().lockCanvas();
		canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
		mSurfaceView.getHolder().unlockCanvasAndPost(canvas);
	}
	
	public void drawBlackBackground() {
		Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL_AND_STROKE);
        paint.setStrokeWidth(3);
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        Canvas canvas = mSurfaceView.getHolder().lockCanvas();
        canvas.drawRect(0, 0, mSurfaceView.getWidth(), mSurfaceView.getHeight(), paint);
        mSurfaceView.getHolder().unlockCanvasAndPost(canvas);

	}

	/**
	 * Este metodo se llama para dibujar un frame modificado en la pantalla Es
	 * una implementacion adaptada de
	 * org.opencv.android.CameraBridgeViewBase.deliverAndDrawFrame
	 * (CvCameraViewFrame)
	 * 
	 * @param mat - the current Mat to be delivered
	 */
	protected void drawFrame(Mat mat) {

		boolean bmpValid = true;
		JavaCameraFrame frame = new JavaCameraFrame(mat, mat.width(), mat.height());
		Mat mat_to_draw = frame.rgba();
		//Mat mat_to_draw = mat;
		CustomLog.i(TAG, "Mat= " + mat_to_draw);	
		CustomLog.i(TAG, "Mat width = " + mat_to_draw.width() + " - height = " + mat_to_draw.height());
		//Log.d(TAG, "Mat data = " + mat_to_draw.dump());
		
		 for(int i=0; i<ListOfRect.size(); i++){
		       Core.rectangle(mat_to_draw, ListOfRect.get(i).tl(), ListOfRect.get(i).br(),new Scalar( 0, 0, 255 ),0,8, 0 );}

		AllocateCache(mat_to_draw.width(), mat_to_draw.height()); 
		// XXX: fuerzo a que el bitmap tenga el mismo tamanio que el mat
		
		// XXX: hay que investigar bien el tema de resoluciones y tamanios
		// porqiue no estan iguales

		if (mat_to_draw != null) {
			try {
				//CustomLog.i(TAG, "Mat type: " + mat_to_draw);
				//CustomLog.i(TAG, "Bitmap type: width = " + mCacheBitmap.getWidth() + " * height = " + mCacheBitmap.getHeight());
				//Utils.matToBitmap(mat_to_draw, mCacheBitmap);
				//XXX: intento usar otra forma armando un bitmap del tamanio justo con esta utilidad
				mCacheBitmap = MatUtils.matToBitmap(mat_to_draw);
			} catch (Exception e) {
				
				CustomLog.e(TAG, "Utils.matToBitmap() throws an exception: " + e.getMessage());
				bmpValid = false;
			}
		}

		if (bmpValid && mCacheBitmap != null) {
			CustomLog.i(TAG, "a punto de obtener el holder y dibujar");
			Canvas canvas = mSurfaceView.getHolder().lockCanvas();
			
			if (canvas != null) {
				
				CustomLog.d(TAG, "canvas width = " + canvas.getWidth());
				CustomLog.d(TAG, "canvas height = " + canvas.getHeight());
				
				canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
				
				//calculateScale(mat_to_draw.width(), mat_to_draw.height());
				
				CustomLog.d(TAG, "mStretch value: " + mScale);

				if (mScale != 0) {
					canvas.drawBitmap(
							mCacheBitmap,
							new android.graphics.Rect(0, 0, mCacheBitmap.getWidth(), mCacheBitmap.getHeight()),
							new android.graphics.Rect(
									(int) ((canvas.getWidth() - mScale * mCacheBitmap.getWidth()) / 2),
									(int) ((canvas.getHeight() - mScale * mCacheBitmap.getHeight()) / 2),
									(int) ((canvas.getWidth() - mScale * mCacheBitmap.getWidth()) / 2 + mScale * mCacheBitmap.getWidth()),
									(int) ((canvas.getHeight() - mScale * mCacheBitmap.getHeight()) / 2 + mScale * mCacheBitmap.getHeight())), null);
				} else {
					canvas.drawBitmap(mCacheBitmap, 0, 0, null);
					
//					canvas.drawBitmap(
//							mCacheBitmap,
//							new android.graphics.Rect(0, 0, mCacheBitmap.getWidth(),
//									mCacheBitmap.getHeight()),
//							new android.graphics.Rect((canvas.getWidth() - mCacheBitmap
//									.getWidth()) / 2,
//									(canvas.getHeight() - mCacheBitmap
//											.getHeight()) / 2, (canvas
//											.getWidth() - mCacheBitmap
//											.getWidth())
//											/ 2 + mCacheBitmap.getWidth(),
//									(canvas.getHeight() - mCacheBitmap
//											.getHeight())
//											/ 2
//											+ mCacheBitmap.getHeight()), null);
				}

				mSurfaceView.getHolder().unlockCanvasAndPost(canvas);
				CustomLog.i(TAG, "unlock del canvas listo");

			}
		}
	}
	
	private void calculateScale(int imgWidth, int imgHeight) {
		// http://stackoverflow.com/questions/9149088/set-large-image-as-surfaceviews-background-so-that-it-suits-the-display-height
	    float scale = (float)imgHeight/(float)mSurfaceView.getHeight();	    
	    int newWidth = Math.round(imgWidth/scale);
	    int newHeight = Math.round(imgHeight/scale);
	    CustomLog.d(TAG, "calculateScale - imgWidth = " + imgWidth + " - imgHeight = " + imgHeight);
	    CustomLog.d(TAG, "calculateScale - newWidth = " + newWidth + " - newHeight = " + newHeight);
	    mScale = scale;
	    
//	    mScale = Math.min(imgHeight/((float)mSurfaceView.getHeight()), imgWidth/((float)mSurfaceView.getWidth()));
	}

	private void registerReceivers() {
		CustomLog.d(TAG, "Registrando mDrawFrameReceiver con IntentFilter FiubaAR-drawFrame-Intent");
		LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
				mDrawFrameReceiver,
				new IntentFilter(Constants.DRAW_FRAME_INTENT_NAME));

		// MainApplication.getInstance().getLocalBroadcastManager().registerReceiver(
		// mDrawFrameReceiver, new
		// IntentFilter(Constants.DRAW_FRAME_INTENT_NAME));

	}

	private void unregisterReceivers() {
		MainApplication.getInstance().getLocalBroadcastManager()
				.unregisterReceiver(mDrawFrameReceiver);
	}

	// NOTE: On Android 4.1.x the function must be called before SurfaceTextre
	// constructor!
	protected void AllocateCache(int mFrameWidth, int mFrameHeight) {
		mCacheBitmap = Bitmap.createBitmap(mFrameWidth, mFrameHeight, Bitmap.Config.ARGB_8888);
	}

	@Override
	public void onResume() {
		super.onResume();
		registerReceivers();
	}

	@Override
	public void onPause() {
		super.onPause();
		unregisterReceivers();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		unregisterReceivers();
	}
}
