package com.fi.uba.ar.engine3d;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.FloatBuffer;

import javax.microedition.khronos.opengles.GL10;

import rajawali.Object3D;
import rajawali.animation.Animation.RepeatMode;
import rajawali.animation.Animation3D;
import rajawali.animation.EllipticalOrbitAnimation3D;
import rajawali.animation.EllipticalOrbitAnimation3D.OrbitDirection;
import rajawali.animation.RotateOnAxisAnimation;
import rajawali.lights.PointLight;
import rajawali.math.Matrix4;
import rajawali.math.Quaternion;
import rajawali.math.vector.Vector3;
import rajawali.math.vector.Vector3.Axis;
import rajawali.parser.LoaderGCode;
import rajawali.parser.LoaderOBJ;
import rajawali.parser.ParsingException;
import rajawali.parser.fbx.LoaderFBX;
import rajawali.util.GLU;
import rajawali.util.RajLog;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.opengl.Matrix;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.View.OnTouchListener;

import com.fi.uba.ar.R;
import com.fi.uba.ar.controllers.MainActivity;
import com.fi.uba.ar.utils.CommonUtils;
import com.fi.uba.ar.utils.CustomLog;
import com.qualcomm.vuforia.Matrix44F;
import com.qualcomm.vuforia.Renderer;
import com.qualcomm.vuforia.State;
import com.qualcomm.vuforia.Tool;
import com.qualcomm.vuforia.TrackableResult;
import rajawali.materials.*;
import rajawali.materials.methods.DiffuseMethod;
import rajawali.materials.methods.SpecularMethod;
import rajawali.materials.textures.ATexture;
import rajawali.materials.textures.Texture;
import rajawali.materials.textures.ATexture.TextureException;
import rajawali.materials.textures.ATexture.TextureType;
import rajawali.materials.textures.NormalMapTexture;

public class LoadModelFragment extends Base3DFragment  implements OnTouchListener {
	private ScaleGestureDetector mScaleDetector;
	private float mScaleFactor = 1.f;
	private float mScaleSpan;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mSurfaceView.setOnTouchListener(this);
		// Create our ScaleGestureDetector
	    mScaleDetector = new ScaleGestureDetector(getActivity(), new ScaleListener());	    
	}
	
	@Override
	protected Base3DRenderer createRenderer() {
		return new LoadModelRenderer(getActivity());		
	}
	
	public void setObject3D(Object3D obj) {
		((LoadModelRenderer)getRenderer()).setObject3D(obj);		
	}
	
//	@Override
	public boolean onTouch(View v, MotionEvent event) {
		// Let the ScaleGestureDetector inspect all events.
	    mScaleDetector.onTouchEvent(event);
	    
		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			//mRenderer.getObjectAt(event.getX(), event.getY());
			break;
		case MotionEvent.ACTION_MOVE:
			// Only move if the ScaleGestureDetector isn't processing a gesture.
	        if (!mScaleDetector.isInProgress()) 
				//((LoadModelRenderer) mRenderer).moveSelectedObject(event.getX(), event.getY());
				((LoadModelRenderer) mRenderer).rotateObjectTEST(event.getX(), event.getY());
			break;
		case MotionEvent.ACTION_UP:
			//((TouchAndDragRenderer) mRenderer).stopMovingSelectedObject();
			break;
		}
		return true;
	}
	
	
	private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
	    @Override
	    public boolean onScale(ScaleGestureDetector detector) {
	    	CustomLog.d("ScaleListener", "detector.getCurrentSpan() = " + detector.getCurrentSpan());
	    	CustomLog.d("ScaleListener", "detector.getPreviousSpan() = " + detector.getPreviousSpan());
	    	
	        mScaleFactor *= detector.getScaleFactor();	
	        mScaleSpan = detector.getCurrentSpan(); // average distance between fingers
	        // Don't let the object get too small or too large.
	        mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 5.0f));
	        ((LoadModelRenderer) mRenderer).zoomTEST(mScaleFactor);
//	        invalidate();
	        return true;
	    }
	}

	public final class LoadModelRenderer extends Base3DRenderer {
		private static final String TAG = "LoadModelRenderer";
		private PointLight mLight;
		private Object3D mObject3D = null;
		private Object3D loadingObject3D;
		
		private Animation3D mCameraAnim, mLightAnim;
		
		private int[] mViewport;
		private double[] mNearPos4;
		private double[] mFarPos4;
		private Vector3 mNearPos;
		private Vector3 mFarPos;
		private Vector3 mNewObjPos;
		private Matrix4 mViewMatrix;
		private Matrix4 mProjectionMatrix;
		
		private Vector3 mPosition;
		private Quaternion mOrientation;		
		private double[] mModelViewMatrix;
		
		private Activity mActivity;
		
		private boolean initialized = false;

		public LoadModelRenderer(Context context) {
			super(context);
			mActivity = (Activity) context;
		}
		
		public boolean isInitialized() {
			return initialized;
		}
				
		public Object3D loadLoadingObject() throws ParsingException {
			
			try {			
				
				LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.loading_obj);
				
				//XXX: probando modelos... 
				//LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.loading_obj);
				//LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.creamer3_obj);
				//LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.orange_cone_obj);
				//LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.raptor_obj);
				//LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.gameboy_obj);
				//LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.supercohete_obj);
				//LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.supermodulolunar_obj);
				//LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.supertricycle_obj);
				
				objParser.parse();				
				Object3D obj = objParser.getParsedObject();
				/*
				try {
					Material material2 = new Material();
					material2.setDiffuseMethod(new DiffuseMethod.Lambert());
					material2.setSpecularMethod(new SpecularMethod.Phong(Color.WHITE, 150));
					material2.enableLighting(true);
					material2.addTexture(new Texture("earthDiffuseTex", R.raw.raptor_jpg));
					material2.addTexture(new NormalMapTexture("eartNormalTex", R.raw.raptor_normal_jpg));
					material2.setColorInfluence(0);
					obj.setMaterial(material2);
					
				} catch (Exception e) {
					StringWriter errors = new StringWriter();
					e.printStackTrace(new PrintWriter(errors));
					CustomLog.e(TAG, "ERROR AL CARGAR TEXTURA - " + errors.toString().replace("\n", " -- "));
					CustomLog.e(TAG, e.getMessage());
				}
				*/
				
				return obj;
			} catch (ParsingException e) {				
				e.printStackTrace();
				throw e;
			}			
		}
		
		public Object3D loadTestObject() throws ParsingException {
			
			try {
//				LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.multiobjects_obj);
//				LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.banco2_obj);
				//LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.fireflower_obj);
				LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.loading_obj);
				//LoaderOBJ objParser = new LoaderOBJ(mContext.getResources(), mTextureManager, R.raw.orange_cone_obj);
				objParser.parse();
				return objParser.getParsedObject();
			} catch (ParsingException e) {				
				e.printStackTrace();
				throw e;
			}			
		}
		
		public Object3D loadTestFBXObject() throws ParsingException {
			try {
				// -- Model by Sampo Rask
				// (http://www.blendswap.com/blends/characters/low-poly-rocks-character/)
				LoaderFBX parser = new LoaderFBX(this, R.raw.lowpolyrocks_character_blendswap);
				parser.parse();
				Object3D o = parser.getParsedObject();
	//			o.setY(-.5f);
				return o;
			} catch (ParsingException e) {				
				e.printStackTrace();
				throw e;
			}	
		}
		
		public Object3D loadTestGCObject() throws ParsingException {
			try {
				//LoaderGCode parser = new LoaderGCode(getResources(), getTextureManager(), R.raw.calibrationcube_404020_psm_pla35);
				LoaderGCode parser = new LoaderGCode(getResources(), getTextureManager(), R.raw.supermickey_obj);
				parser.parse();
				Object3D o = parser.getParsedObject();
				return o;
			} catch (ParsingException e) {				
				e.printStackTrace();
				throw e;
			}	
		}

		protected void initScene() {
			RajLog.i("initScene called");
			
			mViewport = new int[] { 0, 0, mViewportWidth, mViewportHeight };
			mNearPos4 = new double[4];
			mFarPos4 = new double[4];
			mNearPos = new Vector3();
			mFarPos = new Vector3();
			mNewObjPos = new Vector3();
			mViewMatrix = getCurrentCamera().getViewMatrix();
			mProjectionMatrix = getCurrentCamera().getProjectionMatrix();	
			
			mPosition = new Vector3();
			mOrientation = new Quaternion();
			
			mModelViewMatrix = new double[16];
			
//			mLight = new PointLight();
//			mLight.setPosition(0, 0, 4);
//			mLight.setPower(3);			
//			getCurrentScene().addLight(mLight);
//			
			getCurrentCamera().setNearPlane(10);
			getCurrentCamera().setFarPlane(2500);
//			getCurrentCamera().setZ(10);
			getCurrentCamera().setZ(16);
 
			RajLog.i("initScene - created obj parser");
			
			try {
				// al crear este fragment cargamos un objeto "loading" hasta que
				// de forma async se obtenga del server el objeto real que corresponde al maker
				CustomLog.d(TAG, "Intentamos a continuacion cargar el loading object...");
				loadingObject3D = loadLoadingObject();
				loadingObject3D.setScale(15); //XXX: probando si asi podemos hacer que no se vea tan chico sobre el marker
				getCurrentScene().addChild(loadingObject3D);
				CustomLog.d(TAG, "se hizo la carga del loading object...");

				//XXX: esto es solo para testear carga de objetos
//				mObject3D = loadTestObject();
//				mObject3D = loadTestFBXObject();
//				mObject3D = loadTestGCObject();
				
				//XXX: en la implementacion de aruco android hacen un scale del object de acuerdo al
				// tamanio del marker de la siguiente manera
//				mObjectGroup.initialScale(mMarkerSize/2);
//				mObjectGroup.getScale().x = mObjectGroup.getScale().y = mObjectGroup.getScale().z = mMarkerSize/2;				
//				getCurrentScene().addChild(mObject3D);
//
//				mCameraAnim = new RotateOnAxisAnimation(Axis.Y, 360);
//				mCameraAnim.setDurationMilliseconds(8000);
//				mCameraAnim.setRepeatMode(RepeatMode.INFINITE);
//				mCameraAnim.setTransformable3D(mObjectGroup);
			} catch (ParsingException e) {
				e.printStackTrace();
			}

//			mLightAnim = new EllipticalOrbitAnimation3D(new Vector3(),
//					new Vector3(0, 10, 0), Vector3.getAxisVector(Axis.Z), 0,
//					360, OrbitDirection.CLOCKWISE);
//
//			mLightAnim.setDurationMilliseconds(3000);
//			mLightAnim.setRepeatMode(RepeatMode.INFINITE);
//			mLightAnim.setTransformable3D(mLight);
//
//			getCurrentScene().registerAnimation(mCameraAnim);
//			getCurrentScene().registerAnimation(mLightAnim);
//
//			//mCameraAnim.play();
//			mLightAnim.play();
			initialized = true;
			RajLog.i("initScene - ending method call...");
		}
		
		public void moveSelectedObject(float x, float y) {
			RajLog.i("moveSelectedObject - new pos x = " + x + " - y = " + y);
			//
			// -- unproject the screen coordinate (2D) to the camera's near plane
			//

			GLU.gluUnProject(x, mViewportHeight - y, 0, mViewMatrix.getDoubleValues(), 0,
					mProjectionMatrix.getDoubleValues(), 0, mViewport, 0, mNearPos4, 0);

			//
			// -- unproject the screen coordinate (2D) to the camera's far plane
			//

			GLU.gluUnProject(x, mViewportHeight - y, 1.f, mViewMatrix.getDoubleValues(), 0,
					mProjectionMatrix.getDoubleValues(), 0, mViewport, 0, mFarPos4, 0);

			//
			// -- transform 4D coordinates (x, y, z, w) to 3D (x, y, z) by dividing
			// each coordinate (x, y, z) by w.
			//

			mNearPos.setAll(mNearPos4[0] / mNearPos4[3], mNearPos4[1]
					/ mNearPos4[3], mNearPos4[2] / mNearPos4[3]);
			mFarPos.setAll(mFarPos4[0] / mFarPos4[3],
					mFarPos4[1] / mFarPos4[3], mFarPos4[2] / mFarPos4[3]);

			//
			// -- now get the coordinates for the selected object
			//

			double factor = (Math.abs(mObject3D.getZ()) + mNearPos.z)
					/ (getCurrentCamera().getFarPlane() - getCurrentCamera().getNearPlane());

			mNewObjPos.setAll(mFarPos);
			mNewObjPos.subtract(mNearPos);
			mNewObjPos.multiply(factor);
			mNewObjPos.add(mNearPos);

//			mObjectGroup.setX(mNewObjPos.x);
//			mObjectGroup.setY(mNewObjPos.y);
			CustomLog.d("moveSelectedObject", "mNewObjPos = " + mNewObjPos);
			mObject3D.setPosition(mNewObjPos);
			
		}
		
		public void onSurfaceChanged(GL10 gl, int width, int height) {
			super.onSurfaceChanged(gl, width, height);
			mViewport[2] = mViewportWidth;
			mViewport[3] = mViewportHeight;
			mViewMatrix = getCurrentCamera().getViewMatrix();
			mProjectionMatrix = getCurrentCamera().getProjectionMatrix();
		}
		
		//XXX: los metodos aca abajo fueron tomados del ejemplo de integracion de Rajawali y Vuforia (AR)
		// esa app hace exactamente el tracking de markers y mueve los objetos 3D de Rajawali de acuerdo a 
		// las matrices obtenidas por el framework de AR
		// https://github.com/MasDennis/RajawaliVuforia				
		
		public void foundMarker(float[] modelViewMatrix) {
		//public void foundMarker(double[] modelViewMatrix) {
			synchronized (this) {
				if (initialized) {
//					CustomLog.d("LoadModelRenderer", "foundMarker - modelViewMatrix = " + modelViewMatrix);
//					CustomLog.d("LoadModelRenderer", "modelViewMatrix = ");
//					StringBuffer sb = new StringBuffer();
//					for (int i = 0; i < modelViewMatrix.length; i++){
//						sb.append(modelViewMatrix[i]);
//						if (((i+1) % 4) == 0 )
//							sb.append("\n");
//						else
//							sb.append(", ");
//					}
//					CustomLog.d("LoadModelRenderer", sb.toString());
					transformPositionAndOrientation(modelViewMatrix);
					foundFrameMarker(mPosition, mOrientation);
				}
//				} else
//					CustomLog.d("LoadModelRenderer", "foundMarker - 3D object renderer still not initialized - modelViewMatrix = " + modelViewMatrix + " - size = " + modelViewMatrix.length);
			}
		}
		
		private void transformPositionAndOrientation(float[] modelViewMatrix) {
		//private void transformPositionAndOrientation(double[] modelViewMatrix) {
			mPosition.setAll(modelViewMatrix[12], -modelViewMatrix[13], -modelViewMatrix[14]);
			
			CommonUtils.copyFloatToDoubleArray(modelViewMatrix, mModelViewMatrix);		
			mOrientation.fromRotationMatrix(mModelViewMatrix);
			
			if(((MainActivity)mActivity).getScreenOrientation() == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
			{
				mPosition.setAll(modelViewMatrix[12], -modelViewMatrix[13], -modelViewMatrix[14]);
				mOrientation.y = -mOrientation.y;
				mOrientation.z = -mOrientation.z;
			}
			else
			{
				mPosition.setAll(-modelViewMatrix[13], -modelViewMatrix[12], -modelViewMatrix[14]);
				double orX = mOrientation.x;
				mOrientation.x = -mOrientation.y;
				mOrientation.y = -orX;
				mOrientation.z = -mOrientation.z;
			}			
		}
		
		protected void foundFrameMarker(Vector3 position, Quaternion orientation) {			
//			CustomLog.d("LoadModelRenderer", "foundFrameMarker - position = " + position + " - orientation = " + orientation);
			if (mObject3D != null) {
				mObject3D.setPosition(position);
				mObject3D.setOrientation(orientation);
				mObject3D.setVisible(true);
			} else {
				loadingObject3D.setPosition(position);
				loadingObject3D.setOrientation(orientation);
				loadingObject3D.setVisible(true);
			}
			
		}
		
		public Object3D getObject3D() {
			return this.mObject3D;
		}
		
		public void setObject3D(Object3D obj) {
			this.mObject3D = obj;
			
			//XXX: en la implementacion de aruco android hacen un scale del object de acuerdo al
			// tamanio del marker de la siguiente manera
//			mObjectGroup.initialScale(mMarkerSize/2);
//			mObjectGroup.getScale().x = mObjectGroup.getScale().y = mObjectGroup.getScale().z = mMarkerSize/2;				
//			getCurrentScene().addChild(mObject3D);
			//XXX: el problema aca es que desde aca no estoy seguro si podemos obtener el tamanio del marker desde el tracker de vuforia
			
			//TODO: corregir el factor de escala probablemente verificando primero el tamanio real del objeto
			mObject3D.setScale(3); // XXX: agregado a modo de testing para asegurarnos del que el objeto se carga bien sobre el marker y se ve
			getCurrentScene().addChild(mObject3D);
			if (loadingObject3D != null) {
				// como ya tenemos el objeto real a mostrar, tenemos que quitar el objeto de loading
				loadingObject3D.setVisible(false);
				getCurrentScene().removeChild(loadingObject3D);
			}
		}
		
		@Override
		protected void onRender(final double deltaTime) {
			//renderFrame(mBackgroundRenderTarget.getFrameBufferHandle(), mBackgroundRenderTarget.getTexture().getTextureId());
			// hacemos el render del objeto de acuerdo a lo que obtenemos de vuforia
			super.onRender(deltaTime);
		}
		
		
		@Deprecated //XXX: borrar en refactor? - la posicion se esta actualizando desde com.fi.uba.ar.engine3d.LoadModelFragment.LoadModelRenderer.foundMarker(float[])
		public void renderFrame()
	    {
	       
	        // Get the state from Vuforia and mark the beginning of a rendering
	        // section
	        State state = Renderer.getInstance().begin();
	        
	        // Explicitly render the Video Background
	        Renderer.getInstance().drawVideoBackground();
	        
	        // Did we find any trackables this frame?
	        for (int tIdx = 0; tIdx < state.getNumTrackableResults(); tIdx++)
	        {
	            // Get the trackable:
	            TrackableResult trackableResult = state.getTrackableResult(tIdx);
	            Matrix44F modelViewMatrix_Vuforia = Tool.convertPose2GLMatrix(trackableResult.getPose());
	            float[] modelViewMatrix = modelViewMatrix_Vuforia.getData();
	            
//	            float[] modelViewProjection = new float[16];
//	            Matrix.translateM(modelViewMatrix, 0, 0.0f, 0.0f, kObjectScale);
//	            Matrix.scaleM(modelViewMatrix, 0, kObjectScale, kObjectScale, kObjectScale);
//	            Matrix.multiplyMM(modelViewProjection, 0, vuforiaAppSession.getProjectionMatrix().getData(), 0, modelViewMatrix, 0);
	            /*
	            // en el ejemplo de rajawali tiene ademas
	             if (isActivityInPortraitMode)
					Utils::rotatePoseMatrix(90.0f, 0, 1.0f, 0,&modelViewMatrix.data[0]);
				Utils::rotatePoseMatrix(-90.0f, 1.0f, 0, 0, &modelViewMatrix.data[0]);
	            
	            Matrix.translateM(modelViewMatrix, 0, 0.0f, -0.50f * 120.0f, 1.35f * 120.0f);
            	Matrix.rotateM(modelViewMatrix, 0, -90.0f, 1.0f, 0, 0);
	            */
	            foundMarker(modelViewMatrix);

	        }
	        	        
	        Renderer.getInstance().end();
	    }
		

		public void rotateObjectTEST(float x, float y) {
			RajLog.i("rotateObjectTEST - new pos x = " + x + " - y = " + y);
//			// -- get vertex buffer
//			FloatBuffer vertBuffer = mObject3D.getGeometry().getVertices();
//			// -- get the normal buffer
//			FloatBuffer normBuffer = mObject3D.getGeometry().getNormals();
			Object3D obj = null;
			
			if (mObject3D != null)
				obj = mObject3D;
			else if (loadingObject3D != null)
				obj = loadingObject3D;
			
			if (obj != null)
				obj.setRotation(obj.getY() - y + 180, obj.getX() - x, obj.getZ());
		}
		
		public void zoomTEST(float z) {
			//XXX: revisar esto https://github.com/MasDennis/Rajawali/issues/709
			// https://github.com/MasDennis/Rajawali/wiki/Tutorial-13-Animation-Classes			
			float scaled_z = z / 100;
			double current_field_of_view = getCurrentCamera().getFieldOfView();
//			RajLog.i("zoomTEST - current position ( x=" + mObject3D.getX() + ", y=" + mObject3D.getY() + ", z=" + mObject3D.getZ());
//			RajLog.i("zoomTEST - new z = " + (mObject3D.getZ() + scaled_z));
			RajLog.i("zoomTEST - z = " + z);
			RajLog.i("zoomTEST - scaled_z = " + scaled_z);
			RajLog.i("zoomTEST - current FieldOfView = " + current_field_of_view);
			//mObject3D.setPosition(mObject3D.getX(), mObject3D.getY(), mObject3D.getZ() + scaled_z);
			
			// sin dudas usar el Fiel of view es la forma del zooom, pero hay que ver bien lo del factor y ver si sumamos o restamos
			getCurrentCamera().setFieldOfView(current_field_of_view * z);
			//TODO: resulta ser que hacer el zoom cambiando el setFielOfView nos genera problemas despues con el posicionamiento
			// con el marker y el objeto empieza a aparecer en lugares incorrectos.
			// Habria que ver de cambiar eso haciendo algo como mObject3D.setScale(factor)
			
		}
		
		//XXX: solo para debug, nos permite dumpear al log los valores actuales de position y orientation
		// que son los valores que se actualizan cuando se hace tracking del marker
		public void dumpCoordinates() {
			
			if (mObject3D != null) {
				Vector3 position = mObject3D.getPosition();
				CustomLog.d(TAG, "--------- POSITION ------------");
				CustomLog.d(TAG, "x = " + position.x);
				CustomLog.d(TAG, "y = " + position.y);
				CustomLog.d(TAG, "z = " + position.z);
				
				Quaternion orientation = mObject3D.getOrientation(new Quaternion());
				CustomLog.d(TAG, "--------- ORIEINTATION ------------");
				CustomLog.d(TAG, "w = " + orientation.w);
				CustomLog.d(TAG, "x = " + orientation.x);
				CustomLog.d(TAG, "y = " + orientation.y);
				CustomLog.d(TAG, "z = " + orientation.z);	
				
				CustomLog.d(TAG, "--------- FOV ------------");
				CustomLog.d(TAG, "Field of View = " + getCurrentCamera().getFieldOfView());
				
				Vector3 scale = mObject3D.getScale();
				CustomLog.d(TAG, "--------- SCALE ------------");
				CustomLog.d(TAG, "x = " + scale.x);
				CustomLog.d(TAG, "y = " + scale.y);
				CustomLog.d(TAG, "z = " + scale.z);
				
			}
		}
	}

}
