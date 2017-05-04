package com.fi.uba.ar.model;

import java.io.File;

import org.opencv.core.Mat;

import com.fi.uba.ar.R;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.views.engine3d.LoadModelFragment;
import com.fi.uba.ar.views.engine3d.LoadModelFragment.LoadModelRenderer;

import android.app.Fragment;
import rajawali.Object3D;
import rajawali.parser.LoaderOBJ;
import rajawali.parser.ParsingException;
import rajawali.renderer.RajawaliRenderer;
import rajawali.util.RajLog;

/*
 * Esta clase representa un objeto AR que esta compuesto por 
 *  - marker detectado
 *  - su correspondiente objeto 3D obtenido desde el server
 *  - el fragment usado para renderizar el objeto
 */

//TODO: limpiar el codigo aca porque tiene muchas cosas que ya no se usan mas con vuforia

public class ObjectAR {
	
	private static final String TAG = "ObjectAR";
	private Integer id;
	private String qrCode;
	private Marker marker;
	private Object3D object3d;
	private Fragment container_fragment;

	public ObjectAR(int id, String qr) {
		this.marker = null;
		this.id = id;
		this.object3d = null;
		this.qrCode = qr;
		this.setFragment(null);	
	}
	
	public ObjectAR(Marker m) {
		this.marker = m;
		this.id = this.marker.getMarkerId();
		this.object3d = null;
		this.qrCode = null;
		this.setFragment(null);	
	}
	
	public ObjectAR(Marker m, Object3D obj3d) {
		this(m);
		this.setObject3D(obj3d);
	}
	
	private LoadModelFragment.LoadModelRenderer getModelRenderer() {
		Fragment f = getFragment();
		if (f != null) {
			return (LoadModelRenderer) ((LoadModelFragment)f).getRenderer();		
		}
		return null;
	}
	
	/*
	//XXX: esto quizas necesite ser refactorizado de tal forma que tengamos una cola
	// de nuevas posiciones y que tengamos un thread que vaya actualizando el objeto3d
	// y el fragment pero sin realmente copiar cada nuevo rvec y tvec en el marker
	@Deprecated //XXX: borrar en refactor
	public void updateMarkerPosition(Mat rvec, Mat tvec) {
		if (this.marker != null) {
			rvec.copyTo(this.marker.getRvec());
			tvec.copyTo(this.marker.getTvec());
			update3DObjectPosition();
		}
	}
	*/
	
	public void updateMarkerPosition(float[] modelViewMatrix) {
		update3DObjectPosition(modelViewMatrix);
	}
	
	private void update3DObjectPosition(float[] modelViewMatrix) {
		try {
			
			LoadModelFragment.LoadModelRenderer renderer = getModelRenderer();
			if (renderer != null)
				renderer.foundMarker(modelViewMatrix);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	
	//XXX: tendria que estar esto en el fragment asociado al objeto?
	@Deprecated //XXX: borrar en refactor
	/*
	private void update3DObjectPosition() {
		double[] matrix = new double[16];
		try {
			ArucoUtils.glGetModelViewMatrix(matrix, this.marker.getRvec(), this.marker.getTvec());
//			ArucoUtils.glIdentityMatrix(matrix);
			//XXX: esto aca abajo parece que fue agregado en el engine min3d y en Rajawali no esta..
			// Hay que revisar como se puede hacer sto en Rajawali o si hay que agregar dicha modificacion
			//this.object3d.setModelViewMatrix(matrix);
			LoadModelFragment.LoadModelRenderer renderer = getModelRenderer();
			if (renderer != null)
				renderer.renderFrame();
//				renderer.foundMarker(matrix);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	*/
	
	public void setObject3D(Object3D obj3d) {
		this.object3d = obj3d;	
		((LoadModelFragment)getFragment()).setObject3D(obj3d);
	}
	
	public Object3D getObject3D() {
		return this.object3d;
	}

	public String getQRCode() {
		return this.qrCode;
	}
	
	public Fragment getFragment() {
		return container_fragment;
	}

	public void setFragment(Fragment fragment) {
		this.container_fragment = fragment;
	}
	
	public boolean isRendered() {
		if ( (this.object3d != null) && (getFragment() != null) ) {
			//TODO: habria que verificar que el fragment se este mostrando?
			return true;
		} else
			return false;
	}
	
	public Object3D load3DObjectFromFile(File file) {
		RajawaliRenderer renderer = null;
		
		try {			
			renderer = getModelRenderer();
			CustomLog.d(TAG, "load3DObjectFromFile - File = " + file.getAbsolutePath());
			CustomLog.d(TAG, "load3DObjectFromFile - renderer = " + renderer);
			//TODO: el tipo de Loader usado depende del tipo de formato del archivo
			// Rajawali provee diferentes loaders para cada export (3ds, obj, mds, awd, etc)
			// Tenemos que saber que tipo de export se esta usando (por la extension del archivo quizas?)
			LoaderOBJ objParser = new LoaderOBJ(renderer, file); //TODO: ver si funciona igual pasando un renderer null
			objParser.parse();
			//setObject3D(objParser.getParsedObject());
			return objParser.getParsedObject();
		} catch (Exception e) {
			CustomLog.e(TAG, "Error cargando objeto 3D desde archivo");
			CustomLog.e(TAG, e.toString());
			e.printStackTrace();
		}
		
		return null;
	}
	
	public void load3DObjectDEBUG() {
		
		LoadModelFragment.LoadModelRenderer renderer = getModelRenderer();
		if (renderer != null)
			this.object3d = renderer.getObject3D();
			
//			try {
//				this.object3d = renderer.loadTestObject();
//			} catch (ParsingException e) {
//				e.printStackTrace();
//			}
		
	}
	
	// Intentamos restaurar la posicion original del objeto tal como
	// se veia cuando recien se cargo al detectar el marker
	public void restoreOriginalStatus() {
		getModelRenderer().restoreObject3DOriginalStatus();
	}
	
	//XXX: solo para debug, para poder dumpear al log cualquier info util que necesitemos
	public void dumpObject3DDebugInfo() {
		LoadModelFragment.LoadModelRenderer renderer = getModelRenderer();
		if (renderer != null) {
			renderer.dumpCoordinates();
		}					
	}
	
	public int getID() {
		return id;
	}
}
