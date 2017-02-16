#include "myImage.hpp"
#include <opencv2/imgproc/imgproc.hpp>
#include<opencv2/opencv.hpp>
#include <opencv2/highgui/highgui.hpp>
#include <stdio.h>
#include <stdlib.h>
#include <iostream>
#include <string>
#include "roi.hpp"
#include "native_logcat.h"

using namespace cv;
using namespace std;

My_ROI::My_ROI() {
	upper_corner = Point(0, 0);
	lower_corner = Point(0, 0);
	border_thickness = 0;
}

My_ROI::My_ROI(Point u_corner, Point l_corner, Mat src) {
	upper_corner = u_corner;
	lower_corner = l_corner;
	color = Scalar(0, 255, 0);
	border_thickness = 2;
	Rect r = Rect(u_corner.x, u_corner.y, l_corner.x - u_corner.x, l_corner.y - u_corner.y);

	/*
	std::stringstream ss;
	ss.str("");
	ss << "mat = " << src;
	ss << " - rect = " << r;
	const std::string tmpex = ss.str();
	LOGD("My_ROI", tmpex.c_str());
	*/
	roi_ptr = src(r);
}

void My_ROI::draw_rectangle(Mat src) {
	rectangle(src, upper_corner, lower_corner, color, border_thickness);

}
