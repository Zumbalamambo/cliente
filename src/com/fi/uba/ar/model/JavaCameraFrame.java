package com.fi.uba.ar.model;

import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

//clase copiada exactamente igual que org.opencv.android.JavaCameraView.JavaCameraFrame
// y lo hacemos poruqe esa es privada

public class JavaCameraFrame implements CvCameraViewFrame {	
	private Mat mYuvFrameData;
    private Mat mRgba;
    private int mWidth;
    private int mHeight;
    
    public Mat gray() {
        return mYuvFrameData.submat(0, mHeight, 0, mWidth);
    }

    public Mat rgba() {    	
        Imgproc.cvtColor(mYuvFrameData, mRgba, Imgproc.COLOR_YUV2RGBA_NV21, 4);
        return mRgba;
    }

    public JavaCameraFrame(Mat Yuv420sp, int width, int height) {
        super();
        mWidth = width;
        mHeight = height;
        mYuvFrameData = Yuv420sp;
        mRgba = new Mat();
    }

    public void release() {
        mRgba.release();
    }

	@Override
	public String toString() {
		return "JavaCameraFrame [mYuvFrameData=" + mYuvFrameData + ", mRgba="
				+ mRgba + ", mWidth=" + mWidth + ", mHeight=" + mHeight + "]";
	}

    
};