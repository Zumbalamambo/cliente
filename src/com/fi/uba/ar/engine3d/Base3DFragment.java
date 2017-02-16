package com.fi.uba.ar.engine3d;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import rajawali.RajawaliFragment;
import rajawali.renderer.RajawaliRenderer;
import rajawali.util.RajLog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import com.fi.uba.ar.R;
import com.fi.uba.ar.utils.CustomLog;

public abstract class Base3DFragment extends RajawaliFragment implements OnClickListener {

	private static final String TAG = "Base3DFragment";
	protected RajawaliRenderer mRenderer;
	protected ProgressBar mProgressBarLoader;	

	@Override
	public void onCreate(Bundle savedInstanceState) {	
		// Si estamos ejecutando en un emulador necesitamos setear algunas variables de este fragment
		// de acuerdo a: https://github.com/MasDennis/Rajawali/issues/235		
		this.mMultisamplingEnabled = true;
		this.checkOpenGLVersion = false;
		
		super.onCreate(savedInstanceState);

		final Bundle bundle = getArguments();
		
		if (isTransparentSurfaceView())
			setGLBackgroundTransparent(true);

		mRenderer = createRenderer();
		if (mRenderer == null) {
			CustomLog.w(TAG, "No se pudo crear un Renderer! - NullRenderer");
			mRenderer = new NullRenderer(getActivity());
		} else {
			CustomLog.w(TAG, "Renderer creado = " +  mRenderer);
		}

		mRenderer.setSurfaceView(mSurfaceView);
		setRenderer(mRenderer);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		mLayout = (FrameLayout) inflater.inflate(R.layout.base3d_fragment, container, false);

		mLayout.addView(mSurfaceView);

		mLayout.findViewById(R.id.relative_layout_loader_container).bringToFront();

		// Create the loader
		mProgressBarLoader = (ProgressBar) mLayout.findViewById(R.id.progress_bar_loader);
		mProgressBarLoader.setVisibility(View.GONE);

		return mLayout;
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		
		}
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();

		if (mLayout != null)
			mLayout.removeView(mSurfaceView);
	}

	@Override
	public void onDestroy() {
		try {
			super.onDestroy();
		} catch (Exception e) {
		}
		mRenderer.onSurfaceDestroyed();
	}

	public RajawaliRenderer getRenderer() {
		return this.mRenderer;
	}
	
	/**
	 * Create a renderer to be used by the fragment. Optionally null can be returned by fragments
	 * that do not intend to display a rendered scene. Returning null will cause a warning to be
	 * logged to the console in the event null is in error.
	 * 
	 * @return
	 */
	protected abstract Base3DRenderer createRenderer();

	protected void hideLoader() {
		mProgressBarLoader.post(new Runnable() {
			@Override
			public void run() {
				mProgressBarLoader.setVisibility(View.GONE);
			}
		});
	}

	protected boolean isTransparentSurfaceView() {
		//return false;
		// haciendo la superficie transparente la imagen debajo de este fragment se peude seguir viendo
		return true; 
	}

	protected void showLoader() {
		mProgressBarLoader.post(new Runnable() {
			@Override
			public void run() {
				mProgressBarLoader.setVisibility(View.VISIBLE);
			}
		});
	}

	protected abstract class Base3DRenderer extends RajawaliRenderer {

		public Base3DRenderer(Context context) {
			super(context);
			setFrameRate(60);
		}

		public void onSurfaceCreated(GL10 gl, EGLConfig config) {
			showLoader();
			super.onSurfaceCreated(gl, config);
			hideLoader();
		}

	}

	private static final class NullRenderer extends RajawaliRenderer {

		public NullRenderer(Context context) {
			super(context);
			RajLog.w(this + ": Fragment created without renderer!");
		}

		@Override
		public void onSurfaceDestroyed() {
			stopRendering();
		}
	}

}
