package com.fi.uba.ar.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfInt4;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.RotatedRect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import com.fi.uba.ar.utils.CustomLog;

public class HandGesture {
	private static final String TAG = "HandGesture";
	
	public List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
	// va a contener el indice el biggest contour asignado en com.fi.uba.ar.model.HandGesture.findBiggestContour()
	public int cMaxId = -1; 
	public Mat hie = new Mat();
	public List<MatOfPoint> hullPoints = new ArrayList<MatOfPoint>();
	public MatOfInt hullIndices = new MatOfInt();
	public Rect boundingRect;
	public MatOfInt4 defects = new MatOfInt4();

	public ArrayList<Integer> defectIdAfter = new ArrayList<Integer>();

	public List<Point> fingerTips = new ArrayList<Point>();
	public List<Point> fingerTipsOrder = new ArrayList<Point>();
	public Map<Double, Point> fingerTipsOrdered = new TreeMap<Double, Point>();

	public MatOfPoint2f defectMat = new MatOfPoint2f();
	public List<Point> defectPoints = new ArrayList<Point>();
	public Map<Double, Integer> defectPointsOrdered = new TreeMap<Double, Integer>();

	public Point palmCenter = new Point();
	public MatOfPoint2f hullCurP = new MatOfPoint2f();
	public MatOfPoint2f approxHull = new MatOfPoint2f();

	public MatOfPoint2f approxContour = new MatOfPoint2f();

	public MatOfPoint palmDefects = new MatOfPoint();

	public Point momentCenter = new Point();
	public double momentTiltAngle;

	public Point palmCircleCenter = new Point();

	public double palmCircleRadius;

	public List<Double> features = new ArrayList<Double>();

	private boolean isHand = false;

	private float[] floatPalmCircleRadius = { 0 };

	public void findBiggestContour() {
		int idx = -1;
		int cNum = 0;

		for (int i = 0; i < contours.size(); i++) {
			int curNum = contours.get(i).toList().size();
			if (curNum > cNum) {
				idx = i;
				cNum = curNum;
			}
		}

		cMaxId = idx;
	}
	
	public void resetInternalValues() {
		// hacemos un reset de algunos valores internos que son necesarios 
		// para determinar si una mano es detectada y cuyo valor inicial deberia 
		// ser inicializado para cada frame que procesamos
		
		//XXX: revisar bien si hace falta inicializar alguno mas
		cMaxId = -1;
		boundingRect = null;
		fingerTips.clear();
		defectPoints.clear();
		defectPointsOrdered.clear();
		fingerTipsOrdered.clear();
		defectIdAfter.clear();
		
	}

	public boolean detectIsHand(Mat img) {
		int centerX = 0;
		int centerY = 0; //XXX: porque no se usa?
		
		if (boundingRect != null) {
			centerX = boundingRect.x + boundingRect.width / 2;
			centerY = boundingRect.y + boundingRect.height / 2;
		}
		if (cMaxId == -1)
			isHand = false;
		else if (boundingRect == null)
			isHand = false;
		else if ((boundingRect.height == 0) || (boundingRect.width == 0))
			isHand = false;
		else if ((centerX < img.cols() / 4) || (centerX > img.cols() * 3 / 4))
			isHand = false;
		else
			isHand = true;
		
		return isHand;
	}

	// Convert the feature indicated by label to the string used in SVM input file
	String feature2SVMString(int label) {
		String ret = Integer.toString(label) + " ";
		int i;
		for (i = 0; i < features.size(); i++) {
			int id = i + 1;
			ret = ret + id + ":" + features.get(i) + " ";
		}
		ret = ret + "\n";
		return ret;
	}

	// Extract hand features from img
	public String featureExtraction(Mat img, int label) {
		String ret = null;
		
		if ((detectIsHand(img))) {
			
			//CustomLog.d(TAG, "featureExtraction - img.height = " + img.height() + " - img.width = " + img.width());

			defectMat.fromList(defectPoints);

			List<Integer> dList = defects.toList();
			Point[] contourPts = contours.get(cMaxId).toArray();
			Point prevDefectVec = null;
			int i;
			
			//XXX: cual es el proposito de este for loop?
			// El valor de i se usa en el loop siguiente para dibujar los "defect points"
			
			//XXX: defectIdAfter se llena con valores en la llamada a 
			// com.fi.uba.ar.services.detectors.HandTrackingDetector.makeContours()
			// y parece que valida algo con los angulos entre "potenciales dedos" para
			// determinar si es un defect point valido o no
			for (i = 0; i < defectIdAfter.size(); i++) {
				int curDlistId = defectIdAfter.get(i);
				int curId = dList.get(curDlistId);

				Point curDefectPoint = contourPts[curId];
				Point curDefectVec = new Point();
				curDefectVec.x = curDefectPoint.x - palmCircleCenter.x;
				curDefectVec.y = curDefectPoint.y - palmCircleCenter.y;

				if (prevDefectVec != null) {
					double dotProduct = curDefectVec.x * prevDefectVec.x + curDefectVec.y * prevDefectVec.y;
					double crossProduct = curDefectVec.x * prevDefectVec.y - prevDefectVec.x * curDefectVec.y;
					//XXX: necesitamos determinar que indica esta condicion de corte
					if (crossProduct <= 0) 
						break;
				}

				prevDefectVec = curDefectVec;
			}

			int startId = i;
			int countId = 0;

			ArrayList<Point> finTipsTemp = new ArrayList<Point>();

			if (defectIdAfter.size() > 0) {
				boolean end = false;

				for (int j = startId;; j++) {
					if (j == defectIdAfter.size()) {

						if (end == false) {
							j = 0;
							end = true;
						} else
							break;
					}

					if ((j == startId) && (end == true))
						break;

					int curDlistId = defectIdAfter.get(j);
					int curId = dList.get(curDlistId);

					Point curDefectPoint = contourPts[curId];
					Point fin0 = contourPts[dList.get(curDlistId - 2)];
					Point fin1 = contourPts[dList.get(curDlistId - 1)];
					finTipsTemp.add(fin0);
					finTipsTemp.add(fin1);

					// Valid defect point is stored in curDefectPoint
					// Dibuja un pequenio punto azul en cada uno de los defect points aceptales
					// Esto basicamente son los puntos "entre" los dedos
					//XXX: no nos iteresa dibujar, asi que lo comentamos para ver si mejora la performance
					//Core.circle(img, curDefectPoint, 2, new Scalar(0, 0, 255), -5);

					countId++;
				}
			}

			int count = 0;
			features.clear();
			// dibuja los dedos y solo dibuja 5
			//XXX: sera que pueden haber mas de 5 posibles dedos ??
			for (int fid = 0; fid < finTipsTemp.size(); ) {
				// la cuenta empieza en 0 asi que si pasamos 4 ya dibujamos 5 dedos
				if (count > 4) 					
					break;

				Point curFinPoint = finTipsTemp.get(fid);

				if ((fid % 2 == 0)) {

					if (fid != 0) {
						Point prevFinPoint = finTipsTemp.get(fid - 1);
						curFinPoint.x = (curFinPoint.x + prevFinPoint.x) / 2;
						curFinPoint.y = (curFinPoint.y + prevFinPoint.y) / 2;
					}

					if (fid == (finTipsTemp.size() - 2))
						fid++;
					else
						fid += 2;
				} else
					fid++;

				//XXX: por el momento comentamos esto porque no nos es de total necesidad para detectar los dedos
				/*
				Point disFinger = new Point(curFinPoint.x - palmCircleCenter.x, curFinPoint.y - palmCircleCenter.y);
				double dis = Math.sqrt(disFinger.x * disFinger.x + disFinger.y * disFinger.y);
				Double f1 = (disFinger.x) / palmCircleRadius;
				Double f2 = (disFinger.y) / palmCircleRadius;
				features.add(f1);
				features.add(f2);
				*/
				
				// curFinPoint stores the location of the finger tip
				//XXX: parece que nunca guardo la punta de los dedos realmente en el campo fingertips 
				// y solo los dejo aca en temp.. asi que los guarto a ver si va bien
				fingerTips.add(curFinPoint);								
				
				count++;

			}
			
			if (fingerTips.size() == 0)
				checkForOneFinger(img);
			
			//XXX: no nos iteresa dibujar, asi que lo comentamos para ver si mejora la performance
			//drawFingertips(img);
			//ret = feature2SVMString(label);
		}

		return ret;
	}
	
	private void checkForOneFinger(Mat img) {
		//XXX: el uso de convexity defects para deteccion de dedos no sirve para el caso 
		// en el que hay un unico dedo entonces hay que usar otro algoritmo para 
		// contemplar este caso
		
		//TODO: implementar esto!!
		
		// La implementacion nativa hacia uso de este codigo:
		/*
		 // convexity defects does not check for one finger
		// so another method has to check when there are no
		// convexity defects
		void HandGesture::checkForOneFinger(MyImage *m) {
		    int yTol = bRect.height / 6;
		    Point highestP;
		    highestP.y = m->src.rows;
		    vector<Point>::iterator d = contours[cIdx].begin();
		    while (d != contours[cIdx].end()) {
		        Point v = (*d);
		        if (v.y < highestP.y) {
		            highestP = v;
		        }
		        d++;
		    }
		    int n = 0;
		    d = hullP[cIdx].begin();
		    while (d != hullP[cIdx].end()) {
		        Point v = (*d);
		        
		        if (v.y < highestP.y + yTol && v.y != highestP.y && v.x != highestP.x) {
		            n++;
		        }
		        d++;
		    }
		    if (n == 0) {
		        fingerTips.push_back(highestP);
		    }
		}
		 */
	}
	
	private void drawFingertips(Mat img) {
		//XXX: solo a modo debug para probar
		dumpData();
		
		int count = 0;
		
		for (Point curFinPoint: fingerTips) {
			count++;
			
			// Dibuja una linea desde el centro de la palma hasta la punta del dedo
			Core.line(img, palmCircleCenter, curFinPoint, new Scalar(24, 77, 9), 2);
			
			// Dibuja un pequenio punto negro en la punta del dedo
			Core.circle(img, curFinPoint, 2, Scalar.all(0), -5);
	
			// Escribe el numero de dedo en la punta
			Core.putText(img, Integer.toString(count), 
					new Point(curFinPoint.x - 10, curFinPoint.y - 10),
					Core.FONT_HERSHEY_SIMPLEX, 0.5, Scalar.all(0));
			
		}
	}

	public native double findInscribedCircleJNI(long imgAddr, double rectTLX,
			double rectTLY, double rectBRX, double rectBRY, double[] incircleX,
			double[] incircleY, long contourAddr);

	// Find the location of inscribed circle and return the radius and the
	// center location
	public void findInscribedCircle(Mat img) {

		Point tl = boundingRect.tl();
		Point br = boundingRect.br();

		double[] cirx = new double[] { 0 };
		double[] ciry = new double[] { 0 };

		palmCircleRadius = findInscribedCircleJNI(img.getNativeObjAddr(), 
												tl.x,
												tl.y, 
												br.x, 
												br.y, 
												cirx, 
												ciry, 
												approxContour.getNativeObjAddr());
		palmCircleCenter.x = cirx[0];
		palmCircleCenter.y = ciry[0];

		Core.circle(img, palmCircleCenter, (int) palmCircleRadius, new Scalar(240, 240, 45, 0), 2);
		Core.circle(img, palmCircleCenter, 3, Scalar.all(0), -2);
	}

	// funcion solo a modo de debug para poder ver toda la info interna de la instancia	
	public void dumpData() {
		CustomLog.d(TAG, "--------------------------------------------");
		
		if ( isHand || cMaxId > -1) {
			CustomLog.d(TAG, "isHand = " + isHand);
			CustomLog.d(TAG, "cMaxId = " + cMaxId);			
			CustomLog.d(TAG, "approxContour = " + approxContour);
			CustomLog.d(TAG, "approxHull = " + approxHull);
			CustomLog.d(TAG, "boundingRect = " + boundingRect);		
			CustomLog.d(TAG, "contours = " + contours);
			CustomLog.d(TAG, "defectIdAfter = " + defectIdAfter);
			CustomLog.d(TAG, "defectMat = " + defectMat );
			CustomLog.d(TAG, "defectPoints = " + defectPoints);
			CustomLog.d(TAG, "defectPointsOrdered = " + defectPointsOrdered);
			CustomLog.d(TAG, "defects = " + defects);
			CustomLog.d(TAG, "features = " + features);
			CustomLog.d(TAG, "fingerTips = " + fingerTips);
			CustomLog.d(TAG, "fingerTipsOrder = " + fingerTipsOrder);
			CustomLog.d(TAG, "fingerTipsOrdered = " + fingerTipsOrdered );
			CustomLog.d(TAG, "hie = " + hie);
			CustomLog.d(TAG, "hullCurP = " + hullCurP);
			CustomLog.d(TAG, "hullI = " + hullIndices);
			CustomLog.d(TAG, "hullP = " + hullPoints);
			CustomLog.d(TAG, "inCircle = " + palmCircleCenter);
			CustomLog.d(TAG, "inCircleRadius = " + palmCircleRadius);		
			CustomLog.d(TAG, "momentCenter = " + momentCenter);
			CustomLog.d(TAG, "momentTiltAngle = " + momentTiltAngle);
			CustomLog.d(TAG, "palmCenter = " + palmCenter);
			CustomLog.d(TAG, "palmCircleRadius = " + floatPalmCircleRadius);
			CustomLog.d(TAG, "palmDefects = " + palmDefects);			
		}
		else
			CustomLog.d(TAG, "No se detecto una mano");
		
		CustomLog.d(TAG, "--------------------------------------------");
	}
}
