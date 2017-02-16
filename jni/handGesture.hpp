#ifndef _HAND_GESTURE_
#define _HAND_GESTURE_ 

#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/opencv.hpp>
#include <vector>
#include <string>
#include "main.hpp"
#include "myImage.hpp"

using namespace cv;
using namespace std;

class HandGesture {
public:
	MyImage m;
	HandGesture();
	vector<vector<Point> > contours;
	vector<vector<int> > hullI;
	vector<vector<Point> > hullP;
	vector<vector<Vec4i> > defects;
	vector<Point> fingerTips;
	Rect rect;
	void printGestureInfo(Mat src);
	int cIdx;
	uint frameNumber;
	int mostFrequentFingerNumber;
	int nrOfDefects;
	Rect bRect;
	double bRect_width;
	double bRect_height;
	bool isHand;
	bool detectIfHand();
	void initVectors();
	void getFingerNumber();
	void eleminateDefects();
	void getFingerTips();
	void drawFingerTips();
private:
	string bool2string(bool tf);
	int fontFace;
	int prevNrFingerTips;
	void checkForOneFinger();
	float getAngle(Point s, Point f, Point e);
	vector<int> fingerNumbers;
	void analyzeContours();
	string intToString(int number);
	void computeFingerNumber();
	void drawNewNumber();
	void addNumberToImg();
	vector<int> numbers2Display;
	void addFingerNumberToVector();
	Scalar numberColor;
	int nrNoFinger;
	float distanceP2P(Point a, Point b);
	void removeRedundantEndPoints(vector<Vec4i> newDefects);
	void removeRedundantFingerTips();
};

#endif
