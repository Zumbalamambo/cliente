#include "opencv2/imgproc/imgproc.hpp"
#include "opencv2/imgproc/types_c.h"
#include "opencv2/highgui/highgui_c.h"
#include <opencv2/opencv.hpp>
#include <stdio.h>
#include <stdlib.h>
#include <iostream>
#include <string>
#include "myImage.hpp"
#include "roi.hpp"
#include "handGesture.hpp"
#include <vector>
#include <cmath>
#include "main.hpp"
#include "native_logcat.h"

using namespace cv;
using namespace std;

//TODO: todo este codigo nativo va a ser llamado de forma concurrente por threads del
// FrameService con lo cual el uso de variables globales es muy peligroso!
//XXX: Puede ser que esto no se use desde multiples threads finalmente y se use de manera secuencial

/* Global Variables  */
int fontFace = FONT_HERSHEY_PLAIN;
int square_len;
int avgColor[NSAMPLES][3];
int c_lower[NSAMPLES][3];
int c_upper[NSAMPLES][3];
int avgBGR[3];
int nrOfDefects;
int iSinceKFInit;
struct dim {
	int w;
	int h;
} boundingDim;
VideoWriter out;
Mat edges;
My_ROI roi1, roi2, roi3, roi4, roi5, roi6;
vector<My_ROI> roi;
vector<KalmanFilter> kf;
vector<Mat_<float> > measurement;
bool handColorSampled = false;
bool samplingHand = false;
MyImage m = MyImage();
HandGesture hg;
int palmFrameCount = 0;

/* end global variables */

void init() {
	square_len = 30;
	iSinceKFInit = 0;
	hg.m = m;
	initTrackbars();
}

// change a color from one space to another
void col2origCol(int hsv[3], int bgr[3], Mat src) {
	Mat avgBGRMat = src.clone();
	for (int i = 0; i < 3; i++) {
		avgBGRMat.data[i] = hsv[i];
	}
	cvtColor(avgBGRMat, avgBGRMat, COL2ORIGCOL);
	for (int i = 0; i < 3; i++) {
		bgr[i] = avgBGRMat.data[i];
	}
}

void printText(Mat src, string text) {
	int fontFace = FONT_HERSHEY_PLAIN;
	putText(src, text, Point(src.cols / 2, src.rows / 10), fontFace, 1.2f, Scalar(200, 0, 0), 2);
}

void doHandColorSample(Mat mat) {
	LOGD("NativeHandTrackingDetector", "llamada a waitForPalmCover - arrancando...");
	//dumpMatInfo(mat);
	m.src = mat;
	std::stringstream ss;
	ss.str("");
	ss << "waitForPalmCover - m.src = " << m.src;
	const std::string tmpex = ss.str();
	LOGD("NativeHandTrackingDetector", tmpex.c_str());

	//flip(m.src, m.src, 1); //XXX: flip sobre eje Y - porque se hace el flip? esta en muchos lugares, es necesario?
	//flip(m.src, m.src, 0); // flip sobre el eje X
	// El codigo original lo que hacia era esperar al menos 50 frames para dar tiempo a que
	// pusieras la mano sobre los cuadritos, pero realmente solo tomaba un frame

	LOGD("NativeHandTrackingDetector", "waitForPalmCover - primer frame! seteando ROI");

	//roi.push_back(My_ROI(Point(m.src.cols/4, m.src.rows/2),Point(m.src.cols/4+square_len,m.src.rows/2+square_len),m.src));
	LOGD("NativeHandTrackingDetector", "waitForPalmCover - antes de crear p1 ");
	Point p1 = Point(m.src.cols / 4, m.src.rows / 2);
	LOGD("NativeHandTrackingDetector", "waitForPalmCover - p1 creado");
	Point p2 = Point(m.src.cols / 4 + square_len, m.src.rows / 2 + square_len);
	LOGD("NativeHandTrackingDetector", "waitForPalmCover - p2 creado");
	My_ROI mr = My_ROI(p1, p2, m.src);
	LOGD("NativeHandTrackingDetector", "waitForPalmCover - My_ROI creado ");
	roi.push_back(mr);
	LOGD("NativeHandTrackingDetector", "waitForPalmCover - My_ROI agregado a roi");

	roi.push_back(
			My_ROI(Point(m.src.cols / 3, m.src.rows / 6),
					Point(m.src.cols / 3 + square_len,
							m.src.rows / 6 + square_len), m.src));
	roi.push_back(
			My_ROI(Point(m.src.cols / 3, m.src.rows / 1.5),
					Point(m.src.cols / 3 + square_len,
							m.src.rows / 1.5 + square_len), m.src));
	roi.push_back(
			My_ROI(Point(m.src.cols / 2, m.src.rows / 2),
					Point(m.src.cols / 2 + square_len,
							m.src.rows / 2 + square_len), m.src));
	roi.push_back(
			My_ROI(Point(m.src.cols / 2.5, m.src.rows / 2.5),
					Point(m.src.cols / 2.5 + square_len,
							m.src.rows / 2.5 + square_len), m.src));
	roi.push_back(
			My_ROI(Point(m.src.cols / 2, m.src.rows / 1.5),
					Point(m.src.cols / 2 + square_len,
							m.src.rows / 1.5 + square_len), m.src));
	roi.push_back(
			My_ROI(Point(m.src.cols / 2.5, m.src.rows / 1.8),
					Point(m.src.cols / 2.5 + square_len,
							m.src.rows / 1.8 + square_len), m.src));


	LOGD("NativeHandTrackingDetector", "waitForPalmCover - procesando frame");
	// we take 50 frames to sample the color of the hand
	/*
	for (int j = 0; j < NSAMPLES; j++) {
		roi[j].draw_rectangle(m.src);
	}
	*/

	findHandAverageColor();

	LOGD("NativeHandTrackingDetector", "waitForPalmCover - sample finalizado!");
	handColorSampled = true;
}

int getMedian(vector<int> val) {
	int median;
	size_t size = val.size();
	sort(val.begin(), val.end());
	if (size % 2 == 0) {
		median = val[size / 2 - 1];
	} else {
		median = val[size / 2];
	}
	return median;
}

void getAvgColor(My_ROI roi, int avg[3]) {
	Mat r;
	roi.roi_ptr.copyTo(r);
	vector<int> hm;
	vector<int> sm;
	vector<int> lm;
	// generate vectors
	for (int i = 2; i < r.rows - 2; i++) {
		for (int j = 2; j < r.cols - 2; j++) {
			hm.push_back(r.data[r.channels() * (r.cols * i + j) + 0]);
			sm.push_back(r.data[r.channels() * (r.cols * i + j) + 1]);
			lm.push_back(r.data[r.channels() * (r.cols * i + j) + 2]);
		}
	}
	avg[0] = getMedian(hm);
	avg[1] = getMedian(sm);
	avg[2] = getMedian(lm);
}

void findHandAverageColor() {

	//flip(m.src, m.src, 1);

	//flip(m.src, m.src, 0); // flip sobre el eje X
	// el codigo original tomaba 30 frames y calculaba el color promedio
	// En este caso vamos a ir con un unico frame
	//for (int i = 0; i < 30; i++) {
		//m.cap >> m.src;
		//flip(m.src, m.src, 1); // No se si este flip es necesario
		cvtColor(m.src, m.src, ORIGCOL2COL);
		for (int j = 0; j < NSAMPLES; j++) {
			getAvgColor(roi[j], avgColor[j]);
			//roi[j].draw_rectangle(m.src); // evitamos toda operacion que dibuje en el mat
		}
		// creo que no hace falta volver atras el cambio porque este mat no se va a usar
		// en ningun otro lado para ninguna otra operacion ni tampoco para mostrar en pantalla
		// asi que podemos comentar esta operacion y evitamos algo de consumo de CPU
		//cvtColor(m.src, m.src, COL2ORIGCOL);

		//string imgText=string("Finding average color of hand");
		//printText(m.src,imgText);
		//imshow("img1", m.src);
		//if(cv::waitKey(30) >= 0) break;
	//}
}


void getHandAverageColorValues(int values[NSAMPLES][3]) {
	for (int i = 0; i < NSAMPLES; i++) {
		values[i][0] = avgColor[i][0];
		values[i][1] = avgColor[i][1];
		values[i][2] = avgColor[i][2];
	}
}

//XXX: quizas tenemos que hacer aun mas pruebas con estos valores
// y el average del color de manos para ver si podemos mejorar
// el rango para detectar la mano.
// En este momento sospecho que el cambio de luces complica mucho
// el tema de colores y asi perdemos mucho en la deteccion
void initTrackbars() {
	for (int i = 0; i < NSAMPLES; i++) {
		c_lower[i][0] = 12;
		c_upper[i][0] = 7;
		c_lower[i][1] = 30;
		c_upper[i][1] = 40;
		c_lower[i][2] = 80;
		c_upper[i][2] = 80;
	}
	//TODO: quizas a modo de testing deberiamos hacer una UI en android
	// que funcione como estos trackbars, asi podriamos
	// modificar los valores en tiempo real como la version de consola
	// de este mismo codigo
	/*
	createTrackbar("lower1", "trackbars", &c_lower[0][0], 255);
	createTrackbar("lower2", "trackbars", &c_lower[0][1], 255);
	createTrackbar("lower3", "trackbars", &c_lower[0][2], 255);
	createTrackbar("upper1", "trackbars", &c_upper[0][0], 255);
	createTrackbar("upper2", "trackbars", &c_upper[0][1], 255);
	createTrackbar("upper3", "trackbars", &c_upper[0][2], 255);
	*/
}

void normalizeColors() {
	// copy all boundries read from trackbar
	// to all of the different boundries
	for (int i = 1; i < NSAMPLES; i++) {
		for (int j = 0; j < 3; j++) {
			c_lower[i][j] = c_lower[0][j];
			c_upper[i][j] = c_upper[0][j];
		}
	}
	// normalize all boundries so that 
	// threshold is whithin 0-255
	for (int i = 0; i < NSAMPLES; i++) {
		if ((avgColor[i][0] - c_lower[i][0]) < 0) {
			c_lower[i][0] = avgColor[i][0];
		}
		if ((avgColor[i][1] - c_lower[i][1]) < 0) {
			c_lower[i][1] = avgColor[i][1];
		}
		if ((avgColor[i][2] - c_lower[i][2]) < 0) {
			c_lower[i][2] = avgColor[i][2];
		}
		if ((avgColor[i][0] + c_upper[i][0]) > 255) {
			c_upper[i][0] = 255 - avgColor[i][0];
		}
		if ((avgColor[i][1] + c_upper[i][1]) > 255) {
			c_upper[i][1] = 255 - avgColor[i][1];
		}
		if ((avgColor[i][2] + c_upper[i][2]) > 255) {
			c_upper[i][2] = 255 - avgColor[i][2];
		}
	}
}

void produceBinaries() {
	Scalar lowerBound;
	Scalar upperBound;
	Mat foo;
	for (int i = 0; i < NSAMPLES; i++) {
		normalizeColors();
		lowerBound = Scalar(avgColor[i][0] - c_lower[i][0],
				avgColor[i][1] - c_lower[i][1], avgColor[i][2] - c_lower[i][2]);
		upperBound = Scalar(avgColor[i][0] + c_upper[i][0],
				avgColor[i][1] + c_upper[i][1], avgColor[i][2] + c_upper[i][2]);
		m.bwList.push_back(Mat(m.srcLR.rows, m.srcLR.cols, CV_8U));
		inRange(m.srcLR, lowerBound, upperBound, m.bwList[i]);
	}
	m.bwList[0].copyTo(m.bw);
	for (int i = 1; i < NSAMPLES; i++) {
		m.bw += m.bwList[i];
	}
	medianBlur(m.bw, m.bw, 7);
}

void initWindows() {
	namedWindow("trackbars", CV_WINDOW_KEEPRATIO);
	namedWindow("img1", CV_WINDOW_FULLSCREEN);
}

void showWindows() {
	pyrDown(m.bw, m.bw);
	pyrDown(m.bw, m.bw);
	Rect roi(Point(3 * m.src.cols / 4, 0), m.bw.size());
	vector<Mat> channels;
	Mat result;
	for (int i = 0; i < 3; i++)
		channels.push_back(m.bw);
	merge(channels, result);
	result.copyTo(m.src(roi));
	//imshow("img1",m.src);
}

int findBiggestContour(vector<vector<Point> > contours) {
	int indexOfBiggestContour = -1;
	int sizeOfBiggestContour = 0;
	for (int i = 0; i < contours.size(); i++) {
		if (contours[i].size() > sizeOfBiggestContour) {
			sizeOfBiggestContour = contours[i].size();
			indexOfBiggestContour = i;
		}
	}
	return indexOfBiggestContour;
}

void myDrawContours() {
	drawContours(m.src, hg.hullP, hg.cIdx, cv::Scalar(200, 0, 0), 2, 8, vector<Vec4i>(), 0, Point());

	rectangle(m.src, hg.bRect.tl(), hg.bRect.br(), Scalar(0, 0, 200));
	vector<Vec4i>::iterator d = hg.defects[hg.cIdx].begin();
	int fontFace = FONT_HERSHEY_PLAIN;

	vector<Mat> channels;
	Mat result;

	for (int i = 0; i < 3; i++)
		channels.push_back(m.bw);

	merge(channels, result);
	//	drawContours(result,hg.contours,hg.cIdx,cv::Scalar(0,200,0),6, 8, vector<Vec4i>(), 0, Point());
	drawContours(result, hg.hullP, hg.cIdx, cv::Scalar(0, 0, 250), 10, 8, vector<Vec4i>(), 0, Point());

	while (d != hg.defects[hg.cIdx].end()) {
		Vec4i& v = (*d);
		int startidx = v[0];
		Point ptStart(hg.contours[hg.cIdx][startidx]);
		int endidx = v[1];
		Point ptEnd(hg.contours[hg.cIdx][endidx]);
		int faridx = v[2];
		Point ptFar(hg.contours[hg.cIdx][faridx]);
		float depth = v[3] / 256;
		/*
		 line( m.src, ptStart, ptFar, Scalar(0,255,0), 1 );
		 line( m.src, ptEnd, ptFar, Scalar(0,255,0), 1 );
		 circle( m.src, ptFar,   4, Scalar(0,255,0), 2 );
		 circle( m.src, ptEnd,   4, Scalar(0,0,255), 2 );
		 circle( m.src, ptStart,   4, Scalar(255,0,0), 2 );
		 */
		circle(result, ptFar, 9, Scalar(0, 205, 0), 5);

		d++;

	}
//	imwrite("./images/contour_defects_before_eliminate.jpg",result);

}

bool makeContours() {
	bool isHand = false;
	Mat aBw;
	pyrUp(m.bw, m.bw);
	m.bw.copyTo(aBw);
	findContours(aBw, hg.contours, CV_RETR_EXTERNAL, CV_CHAIN_APPROX_NONE);
	hg.initVectors();
	hg.cIdx = findBiggestContour(hg.contours);
	if (hg.cIdx != -1) {
//		approxPolyDP( Mat(hg.contours[hg.cIdx]), hg.contours[hg.cIdx], 11, true );
		hg.bRect = boundingRect(Mat(hg.contours[hg.cIdx]));
		convexHull(Mat(hg.contours[hg.cIdx]), hg.hullP[hg.cIdx], false, true);
		convexHull(Mat(hg.contours[hg.cIdx]), hg.hullI[hg.cIdx], false, false);
		approxPolyDP(Mat(hg.hullP[hg.cIdx]), hg.hullP[hg.cIdx], 18, true);
		if (hg.contours[hg.cIdx].size() > 3) {
			convexityDefects(hg.contours[hg.cIdx], hg.hullI[hg.cIdx], hg.defects[hg.cIdx]);
			hg.eleminateDefects();
		}
		isHand = hg.detectIfHand();
		//hg.printGestureInfo(m.src);
		if (isHand) {
			hg.getFingerTips();
			//XXX: la realidad es que no nos interesa dibujar, solo detectar posiciones de los dedos
			//hg.drawFingerTips();
			//myDrawContours();
		} else {
			LOGD("NativeHandTrackingDetector", "makeContours - isHand es falso!");
		}
	} else {
		LOGD("NativeHandTrackingDetector", "makeContours - cIdx == -1 al hacer findBiggestContour");
	}
	return isHand;
}

void dumpMatInfo(Mat frame) {
	std::stringstream ss;
	ss.str("");
	ss << "Mat data" << endl;
	ss << "type: " << frame.type() << endl;
	ss << "depth: " << frame.depth() << endl;
	ss << "channels: " << frame.channels() << endl;
	const std::string tmpex = ss.str();
	LOGD("NativeHandTrackingDetector", tmpex.c_str());// creo que no hace falta volver atras el cambio porque este mat no se va a usar
}

bool test(Mat frame) {
	bool findersDetected = false;
	hg.frameNumber++;
	// si aun no se hizo el sampling de la mano y no se activo a pedido hacer el sampling
	// entonces el frame que llegue es ignorado y no se hace nada
	if (handColorSampled) {
		m.src = frame;
		//dumpMatInfo(frame);
		//flip(m.src, m.src, 1);
		flip(m.src, m.src, 0); // flip sobre el eje X
		pyrDown(m.src, m.srcLR);
		blur(m.srcLR, m.srcLR, Size(3, 3));
		cvtColor(m.srcLR, m.srcLR, ORIGCOL2COL);
		//LOGD("NativeHandTrackingDetector", "antes de produceBinaries");
		produceBinaries();
		//LOGD("NativeHandTrackingDetector", "despues de produceBinaries");
		//XXX: puede ser que este cambio de color de vuelta al original no sea necesario
		// probamos comentarlo y ver si mejora la performance
		//cvtColor(m.srcLR, m.srcLR, COL2ORIGCOL);
		//LOGD("NativeHandTrackingDetector", "antes de makeContours");
		findersDetected = makeContours();
		//LOGD("NativeHandTrackingDetector", "despues de makeContours");
		/*
		LOGD("NativeHandTrackingDetector", "antes de getFingerNumber");
		hg.getFingerNumber();
		LOGD("NativeHandTrackingDetector", "despues de getFingerNumber");
		*/
		//XXX: creo que esta funcion showWindows da el error en un copyTo que hace
		//XXX: que pasa si no la llamamos?
		//showWindows();
	}
	return findersDetected;
}

// implementacion original
/*
int main() {
	//MyImage m(0);
	MyImage m = MyImage();
	HandGesture hg;
	init(&m);
	m.cap >> m.src;
	namedWindow("img1", CV_WINDOW_KEEPRATIO);
	out.open("out.avi", CV_FOURCC('M', 'J', 'P', 'G'), 15, m.src.size(), true);
	waitForPalmCover(&m);
	findHandAverageColor(&m);
	destroyWindow("img1");
	initWindows(m);
	initTrackbars();
	for (;;) {
		hg.frameNumber++;
		m.cap >> m.src;
		flip(m.src, m.src, 1);
		pyrDown(m.src, m.srcLR);
		blur(m.srcLR, m.srcLR, Size(3, 3));
		cvtColor(m.srcLR, m.srcLR, ORIGCOL2COL);
		produceBinaries(&m);
		cvtColor(m.srcLR, m.srcLR, COL2ORIGCOL);
		makeContours(&m, &hg);
		hg.getFingerNumber(&m);
		showWindows(m);
		out << m.src;
		//imwrite("./images/final_result.jpg",m.src);
		if (cv::waitKey(30) == char('q'))
			break;
	}
	destroyAllWindows();
	out.release();
	m.cap.release();
	return 0;
}
*/

vector<Point> getFingetTipPoints() {
	return hg.fingerTips;
}
