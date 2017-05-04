#ifndef _MAIN_HEADER_ 
#define _MAIN_HEADER_ 

#include <opencv2/imgproc/imgproc.hpp>
#include <opencv2/opencv.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <stdio.h>
#include <stdlib.h>
#include <iostream>
#include <string>

using namespace cv;

#define ORIGCOL2COL CV_BGR2HLS
#define COL2ORIGCOL CV_HLS2BGR
#define NSAMPLES 7
#define PI 3.14159

bool test(Mat frame);
void init();
void doHandColorSample(Mat mat);
void findHandAverageColor();
void getHandAverageColorValues(int[NSAMPLES][3]);
void initTrackbars();
void dumpMatInfo(Mat frame);
vector<Point> getFingetTipPoints();

#endif
