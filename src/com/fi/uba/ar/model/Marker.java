package com.fi.uba.ar.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import android.app.Activity;
import android.util.Log;
import android.widget.Toast;

import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.bus.events.DisplayImageDebugEvent;
import com.fi.uba.ar.controllers.MarkerScanner;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.MatUtils;
import com.fi.uba.ar.utils.MessageUtils;

import de.greenrobot.event.EventBus;
import com.fi.uba.ar.utils.Toasty;
import com.fi.uba.ar.utils.MessageUtils.ToastType;

import rajawali.Object3D;


/**
 * Marker detected in an image, it must be a four-squared contour with black border and
 * a valid code inside it. 
 *
 */
// XXX: revisar si hace falta heredar de MatOfPoint2f
//XXX: Hay que hacer una limpieza de esta clase ya que ahora se usa solo como para asegurarnos
// de que tiene un borde negro y luego podamos leer el codigo QR de la MAT 
public class Marker extends MatOfPoint2f implements Comparable<Marker>{
	private static final String TAG = "Marker";	
	
	//private Object3D object;
	
	private static final long serialVersionUID = 1L;
	public int id;
	protected float ssize;
	private int rotations;
	
	private Code code; // a matrix of integer representing the code (see the class to further explanation)
	
	private Mat mat; // the cvMat of the CANONICAL marker (not the one taken from the capture)
	private Mat Rvec;
	private Mat Tvec;
	//XXX: este atributo points creo que seria lo mismo que MarkerCandidate::contour definido en aruco/markerdetector.h
	// El MarkerCandidate hereda de Marker con lo cual es como si esta clase hubiera juntado ambos
	private Vector<Point> points; 
	
	public Marker() {
		this(0, new Vector<Point>());
	}
	
	public Marker(float size, Vector<Point> p) {
		id = -1;
		ssize = size;
		// TODO revise how the mats are initialized, better to create them with the proper type
		// code more legible
		code = new Code();
//		Rvec = new Mat(3,1,CvType.CV_64FC1);
//		Tvec = new Mat(3,1,CvType.CV_64FC1);
		Rvec = new Mat(3,1,CvType.CV_64F);
		Tvec = new Mat(3,1,CvType.CV_64F);
		mat = new Mat();
		points = new Vector<Point>();
		for(int i=0;i<p.size();i++)
			points.add(p.get(i));
		this.fromList(points);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Marker [\n");
		sb.append("\tID = " + id + "\n");
		sb.append("\tssize = " + ssize + "\n");
		sb.append("\tRvec = " + Rvec + "\n");
		sb.append("\tTvec = " + Tvec + "\n");
		sb.append("\tPoints:\n");
		for(int i=0;i<points.size();i++)
			sb.append("\t\t" + points.get(i) + "\n");
		sb.append("]");		
		return sb.toString();
	}
	
	public void draw(Mat in, Scalar color, int lineWidth, boolean writeId){
	    if (total()!=4)
	    	return;

	    // TODO loop¿?
	    for(int i=0;i<4;i++)
	    	Core.line(in, points.get(i), points.get((i+1)%4), color, lineWidth);
	    if(writeId){
	    	String cad = new String();
	    	cad = "id="+id;
	    	// determine the centroid
	    	Point cent = new Point(0,0);
	    	for(int i=0;i<4;i++){
	    		cent.x += points.get(i).x;
	    		cent.y += points.get(i).y;
	    	}
	        cent.x/=4.;
	        cent.y/=4.;
	        Core.putText(in,cad, cent,Core.FONT_HERSHEY_SIMPLEX, 0.5,  color,2);
	    }
	}
	
	/**
	 * returns the perimeter of the marker, the addition of the distances between
	 * consecutive points.
	 * @return the perimeter.
	 */
	public double perimeter(){
		double sum=0;
		for(int i=0;i<total();i++){
			Point current = points.get(i);
			Point next = points.get((i+1)%4);
			sum+=Math.sqrt( (current.x-next.x)*(current.x-next.x) +
					(current.y-next.y)*(current.y-next.y));
		}
		return sum;
	}
	
	/**
	 * method to access the id, this only returns the id. Doesn't calculate it.
	 * @return the marker id.
	 */
	public int getMarkerId(){
		return id;
	}
	
	public void setMarkerId(int id) {
		this.id = id;
	}
	
	public static Mat createMarkerImage(int id,int size) throws CvException	{
	    if (id>=1024)
	    	throw new CvException("id out of range");
	    Mat marker = new Mat(size,size, CvType.CV_8UC1, new Scalar(0));
	    //for each line, create
	    int swidth=size/7;
	    int ids[]={0x10,0x17,0x09,0x0e};
	    for (int y=0;y<5;y++) {
	        int index=(id>>2*(4-y)) & 0x0003;
	        int val=ids[index];
	        for (int x=0;x<5;x++) {
	            Mat roi=marker.submat((x+1)*swidth, (x+2)*swidth,(y+1)*swidth,(y+2)*swidth);// TODO check
	            if ( (( val>>(4-x) ) & 0x0001) != 0 )
	            	roi.setTo(new Scalar(255));
	            else
	            	roi.setTo(new Scalar(0));
	        }
	    }
	    return marker;
	}
	/*	
	public void draw3dCube(Mat frame, CameraParameters cp, Scalar color){
		MatOfPoint3f objectPoints = new MatOfPoint3f();
		double halfSize = ssize/2.0;
		Vector<Point3> points = new Vector<Point3>();
		points.add(new Point3(-halfSize, -halfSize, 0));
		points.add(new Point3(-halfSize,  halfSize, 0));
		points.add(new Point3( halfSize,  halfSize, 0));
		points.add(new Point3( halfSize, -halfSize, 0));
		points.add(new Point3(-halfSize, -halfSize, ssize));
		points.add(new Point3(-halfSize,  halfSize, ssize));
		points.add(new Point3( halfSize,  halfSize, ssize));
		points.add(new Point3( halfSize, -halfSize, ssize));
		objectPoints.fromList(points);
		MatOfPoint2f imagePoints = new MatOfPoint2f();
		Calib3d.projectPoints(objectPoints, Rvec, Tvec, cp.getCameraMatrix(), cp.getDistCoeff(), imagePoints);
		
		List<Point> pts = new Vector<Point>();
		pts = imagePoints.toList();
		// draw
	    for (int i=0;i<4;i++){
	        Core.line(frame ,pts.get(i),pts.get((i+1)%4), color, 2);
	        Core.line(frame,pts.get(i+4),pts.get(4+(i+1)%4), color, 2);
	        Core.line(frame,pts.get(i),pts.get(i+4), color, 2);
	    }	        
	}
	*/
	
	public Mat getMat() {
		return this.mat;
	}
	
	public void setMat(Mat in){
		in.copyTo(mat);
	}
	
	public long getRvecNativeObjAddr() {
		return this.Rvec.getNativeObjAddr();
	}
	
	public void setRvec(Mat in){
		in.copyTo(Rvec);
	}
	
	public Mat getRvec(){
		return this.Rvec;
	}
	
	public void setRvec(long matAddr){
		CustomLog.i(TAG,"setRvec llamado con address = " + matAddr);
//		Mat in = new Mat(matAddr);
//		in.copyTo(Rvec);
	}
	
	public long getTvecNativeObjAddr() {
		return this.Tvec.getNativeObjAddr();
	}
	
	public void setTvec(Mat in){
		in.copyTo(Tvec);
	}
	
	public Mat getTvec(){
		return this.Tvec;
	}
	
	public void setTvec(long matAddr){
		CustomLog.i(TAG,"setTvec llamado con address = " + matAddr);
//		Mat in = new Mat(matAddr);
//		in.copyTo(Tvec);
	}
	
	public void setSize(float size) {
		this.ssize = size;
	}
	
	/**
	 * construct the matrix of integers from the mat stored.
	 */
	public void extractCode(){
		int rows = mat.rows();
		int cols = mat.cols();
		assert(rows == cols);
		Mat grey = new Mat();
		// change the color space if necessary
		if(mat.type() == CvType.CV_8UC1)
			grey = mat;
		else
			Imgproc.cvtColor(mat, grey, Imgproc.COLOR_RGBA2GRAY);
		// apply a threshold
		Imgproc.threshold(grey, grey, 125, 255, Imgproc.THRESH_BINARY|Imgproc.THRESH_OTSU);
		// the swidth is the width of each row
		int swidth = rows/7;
		// we go through all the rows
		for(int y=0;y<7;y++){
			for(int x=0;x<7;x++){
				int Xstart = x*swidth;
				int Ystart = y*swidth;
				Mat square = grey.submat(Xstart, Xstart+swidth, Ystart, Ystart+swidth);
				int nZ = Core.countNonZero(square);
				if(nZ > (swidth*swidth)/2)
					code.set(x, y, 1);
				else
					code.set(x,y,0);
			}
		}
	}
	
	/**
	 * Return the id read in the code inside a marker. Each marker is divided into 7x7 regions
	 * of which the inner 5x5 contain info, the border should always be black. This function
	 * assumes that the code has been extracted previously.
	 * @param in a marker
	 * @return the id of the marker
	 */
	public int calculateMarkerId(){
		// check all the rotations of code
		Code[] rotations = new Code[4];
		rotations[0] = code;
		int[] dists = new int[4];
		dists[0] = hammDist(rotations[0]);
		int[] minDist = {dists[0],0};
		for(int i=1;i<4;i++){
			// rotate
			rotations[i] = Code.rotate(rotations[i-1]);
			dists[i] = hammDist(rotations[i]);
			if(dists[i] < minDist[0]){
				minDist[0] = dists[i];
				minDist[1] = i;
			}
		}
		this.rotations = minDist[1];
		if(minDist[0] != 0){
			return -1; // matching id not found
		}
		else{
			this.id = mat2id(rotations[minDist[1]]);
		}
		return id;
	}
	
	/**
	 * this functions checks if the whole border of the marker is black
	 * @return true if the border is black, false otherwise
	 */
	public boolean checkBorder(){
		for(int i=0;i<7;i++){
			// normally we'll only check first and last square
			int inc = 6;
			if(i==0 || i==6)// in first and last row the whole row must be checked
				inc = 1;
			for(int j=0;j<7;j+=inc)
				if(code.get(i, j)==1)
					return false;
		}
		return true;
	}
	
	/**
	 * Calculate 3D position of the marker based on its translation and rotation matrix.
	 * This method fills in these matrix properly.
	 * @param camMatrix
	 * @param distCoeff
	 */
	public void calculateExtrinsics(Mat camMatrix, MatOfDouble distCoeffs, float sizeMeters){
		// TODO check params
		
		// set the obj 3D points
		double halfSize = sizeMeters/2.0;
		List<Point3> objPoints = new ArrayList<Point3>();
		objPoints.add(new Point3(-halfSize, -halfSize,0));
		objPoints.add(new Point3(-halfSize,  halfSize,0));
		objPoints.add(new Point3( halfSize,  halfSize,0));
		objPoints.add(new Point3( halfSize, -halfSize,0));

		MatOfPoint3f objPointsMat = new MatOfPoint3f();
		objPointsMat.fromList(objPoints);
		Calib3d.solvePnP(objPointsMat, this, camMatrix, distCoeffs, Rvec, Tvec);
//		ArucoUtils.rotateXAxis(Rvec);
	}
	
	public void setPoints(List<Point> p){
		this.points.clear();
		for(int i=0;i<p.size();i++)
			points.add(p.get(i));
		this.fromList(points);		
	}

	private int hammDist(Code code){
		int ids[][] = {
				{1,0,0,0,0},
				{1,0,1,1,1},
				{0,1,0,0,1},
				{0,1,1,1,0}
		};
		int dist = 0;
		for(int y=0;y<5;y++){
			int minSum = Integer.MAX_VALUE;
			// hamming distance to each possible word
			for(int p=0;p<4;p++){
				int sum=0;
				for(int x=0;x<5;x++)
					sum+= code.get(y+1,x+1) == ids[p][x]? 0:1;
				minSum = sum<minSum? sum:minSum;
			}
			dist+=minSum;
		}
		return dist;
	}

	private int mat2id(Code code){
		int val=0;
		for(int y=1;y<6;y++){
			val<<=1;
			if(code.get(y,2) == 1)
				val |= 1;
			val<<=1;
			if(code.get(y,4) == 1)
				val |= 1;
		}
		return val;
	}
	
	public int getRotations(){
		return this.rotations;
	}

	@Override
	public int compareTo(Marker other) {
		if(id < other.id)
			return -1;
		else if(id > other.id)
			return 1;
		return 0;
	}

//	public void set3DObject(Object3D object) throws ExtParamException {
//		this.object = object;
//		double[] matrix = new double[16];
//		ArucoUtils.glGetModelViewMatrix(matrix,Rvec,Tvec);
////		ArucoUtils.glIdentityMatrix(matrix);
//		//XXX: esto aca abajo parece que fue agregado en el engine min3d y en Rajawali no esta..
//		//this.object.setModelViewMatrix(matrix);
//	}
	
	/*
	//XXX: ArutoUtils ya no la necesitamos mas asi que este metodo no se necesita
	public void draw3DAxis(Mat frame, CameraParameters cp, Scalar color){
		ArucoUtils.draw3dAxis(frame, cp, color, 2*ssize, Rvec, Tvec);
	}
	*/
	
//	public Object3D object(){
//		return object;
//	}
	
	public float getSize(){
		return ssize;
	}
	
	/*
	 * Intenta leer si en el cuadrado del marker hay un codigo QR
	 */
	public ArrayList<String> extractQRCode() {
		//XXX: en el refactor por el momento  no necesitamos usar el bus
		//EventBus.getDefault().post(new DisplayImageDebugEvent(MatUtils.matToBitmap(mat)));

		int imageWidth = mat.width();
		int imageHeight = mat.height();
		
		ArrayList<String> results =  MarkerScanner.scan(imageWidth, imageHeight, MatUtils.matToBytes(mat));            
        for (final String s: results) {
        	CustomLog.d("Marker", "extractQRCode - QR found = " + s);
        	//MessageUtils.showToast(ToastType.INFO, "QR found = " + s);
        	
        }
        return results;
	}
}
