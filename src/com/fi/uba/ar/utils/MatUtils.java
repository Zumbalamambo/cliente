package com.fi.uba.ar.utils;

import java.nio.ByteBuffer;
import java.util.ArrayList;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import android.graphics.Bitmap;

import com.qualcomm.vuforia.Frame;
import com.qualcomm.vuforia.Image;
import com.qualcomm.vuforia.PIXEL_FORMAT;

public class MatUtils {
	
	private static final String LOG_TAG = new Object(){}.getClass().getEnclosingClass().getName();

	public static void dumpMatInfo(Mat mat) {
		CustomLog.d(LOG_TAG, "Mat type = " + mat.type());
		CustomLog.d(LOG_TAG, "Mat cols = " + mat.cols() + " - Mat rows = " + mat.rows());
	}
	
	public static byte[] matToBytes(Mat mat) {	
		// http://answers.opencv.org/question/10344/opencv-java-load-image-to-gui/
		// http://answers.opencv.org/question/6281/is-there-a-way-to-grab-all-the-pixels-of-a-matrix/
		int cols = mat.cols();
		int rows = mat.rows();		
		int elemSize = (int) mat.elemSize();
		byte[] data = new byte[cols * rows * elemSize];
		mat.get(0, 0, data);
		return data;

	}
	
	//XXX: un dato interesante http://answers.opencv.org/question/5597/cvtype-meaning/
	// http://stackoverflow.com/questions/13428689/whats-the-difference-between-cvtype-values-in-opencv
	// https://stackoverflow.com/questions/12335663/getting-enum-names-e-g-cv-32fc1-of-opencv-image-types
	// http://www.theimagingsource.com/en_US/support/documentation/icimagingcontrol-class/PixelformatRGB565.htm
	
	// la creacion de la Mat esta tomada del codigo del metodo:
	// org.opencv.android.JavaCameraView.initializeCamera(int, int)
	// ahi se puede ver cuando define
	// org.opencv.android.JavaCameraView.mFrameChain
	// luego en org.opencv.android.JavaCameraView.onPreviewFrame(byte[], Camera)
	// cuando le setea la data desde en preview que le llega de la camara de android
	public static Mat matFromBytes(int mFrameWidth, int mFrameHeight, byte[] data, boolean grey, int type) {
		// XXX: lo que aun no se bien es porque al crear el mat le da mayor
		// altura que el valor original.
		/*
		CustomLog.d("matFromBytes", "CvType.CV_8UC1 = " + CvType.CV_8UC1);
		CustomLog.d("matFromBytes", "CvType.CV_8UC2 = " + CvType.CV_8UC2);
		CustomLog.d("matFromBytes", "CvType.CV_8UC3 = " + CvType.CV_8UC3);
		CustomLog.d("matFromBytes", "CvType.CV_8UC4 = " + CvType.CV_8UC4);
		*/
		// CV_8UC1 = grey
		// CV_8UC4 = RGBA
		// CV_8UC2 = RGB565
		if (type == -1)
			type = CvType.CV_8UC2;
		
		//int type = CvType.CV_8UC3;
		//int type = 16;
		if (grey)
			type = CvType.CV_8UC1;
		
		//Mat m = new Mat(mFrameHeight + (mFrameHeight / 2), mFrameWidth, type);
		//CustomLog.d(LOG_TAG, "matFromBytes - height = " + mFrameHeight + " - width = " + mFrameWidth + " - data len = " + data.length);
		Mat m = new Mat(mFrameHeight, mFrameWidth, type); 
		m.put(0, 0, data);	
		return m;		
	}
	
	public static Bitmap matToBitmap(Mat mat) {
		//Then convert the processed Mat to Bitmap
		Bitmap resultBitmap = null;
		if (mat != null) {
			resultBitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
			if (resultBitmap != null)
				Utils.matToBitmap(mat, resultBitmap);
		}
		return resultBitmap;
	}
	
	public static Mat bitmapToMat(Bitmap bm) {
		//Bitmap bmp32 = bm.copy(Bitmap.Config.ARGB_8888, true);
		Bitmap bmp32 = bm.copy(Bitmap.Config.RGB_565, true);		
		Mat imgMat = new Mat ( bm.getHeight(), bm.getWidth(), CvType.CV_8UC2, new Scalar(0));
		Utils.bitmapToMat(bmp32, imgMat);
		return imgMat;
	}
	
	// https://stackoverflow.com/questions/10191871/converting-bitmap-to-bytearray-android
	public static byte[] bitmapToByteArray(Bitmap bm) {
		//calculate how many bytes our image consists of.
		int bytes = bm.getByteCount();
		
		//or we can calculate bytes this way. Use a different value than 4 if you don't use 32bit images.
		//int bytes = b.getWidth()*b.getHeight()*4; 

		ByteBuffer buffer = ByteBuffer.allocate(bytes); //Create a new buffer
		bm.copyPixelsToBuffer(buffer); //Move the byte data to the buffer
		return buffer.array(); //Get the underlying array containing the data.
	}
	
	private static void dumpVuforiaFrameImageData(Frame frame) {
		
		Image image = null;
		CustomLog.d("Image", "El frame contiene " + frame.getNumImages() + " imagenes");
		
		for (int i = 0; i < frame.getNumImages(); ++i) {	
			image = frame.getImage(i);
			
			String format = "NOT FOUND";
			switch (image.getFormat()) {
				case 0:
					format = "UNKNOWN";
					break;
				
				case 1:
					format = "RGB565";
					break;
					
				case 2:
					format = "RGB888";
					break;
					
				case 4:
					format = "GRAYSCALE";
					break;
					
				case 8:
					format = "YUV";
					break;
					
				case 16:
					format = "RGBA8888";
					break;
				
				case 32:
					format = "INDEXED";
					break;
			}
			CustomLog.d("Image", "---------------------------------------");
			CustomLog.d("Image", "Image format: " + format);
			CustomLog.d("Image", "Image width: " + image.getWidth());
			CustomLog.d("Image", "Image height: " + image.getHeight());
			CustomLog.d("Image", "Image stride: " + image.getStride());
			

		}
		CustomLog.d("Image", "---------------------------------------");
	}
	
	// https://developer.vuforia.com/library/articles/Solution/How-To-Access-the-Camera-Image-the-Native-APIs
	// https://developer.vuforia.com/forum/faq/android-how-can-i-access-camera-image
	// https://stackoverflow.com/questions/19652290/color-detection-on-vuforia-frames-with-opencv-for-android
	// http://stackoverflow.com/questions/18952283/id-like-to-know-the-meaning-of-cv-error
	public static Mat vuforiaFrameToMat(Frame frame, boolean grey) {
		
		//dumpVuforiaFrameImageData(frame);
		
		ArrayList<Image> image_list = new ArrayList<Image>();
		Image image = null;
		
		int format = grey ? PIXEL_FORMAT.GRAYSCALE : PIXEL_FORMAT.RGB565;
		
		// Vuforia nos provee de un frame varios formatos, y para cada uno de ellos varias resoluciones
		// asi que tomamos todas las resoluciones para el formato buscado y usamos la de mayor resolucion [?]
		for (int i = 0; i < frame.getNumImages(); ++i) {
			
			image = frame.getImage(i);
			
			// el primer elemento de la lista es siempre el de mayor resolucion
			// asi que si el formato es el que queremos simplemente cortamos el loop
			if (image.getFormat() == format) {			
				//image_list.add(image);
				return vuforiaImageToMat(image, grey);			
			}
		}
		
		return null;
	}
	
	public static Mat vuforiaImageToMat(Image image, boolean grey) {

		ByteBuffer pixels = image.getPixels();
		byte[] pixelArray = new byte[pixels.remaining()];
		pixels.get(pixelArray, 0, pixelArray.length);
		int imageWidth = image.getWidth();
		int imageHeight = image.getHeight();
		
		Mat m = MatUtils.matFromBytes(imageWidth, imageHeight, pixelArray, grey, -1);
		
		if (!grey) {
			//Bitmap bm = createBitmapFromImagePixels(pixelArray, imageWidth, imageHeight);
			//m = bitmapToMat(bm);
			//CustomLog.d(LOG_TAG, "vuforiaFrameToMat - Mat from bitmap = " + m);
			Imgproc.cvtColor(m, m, Imgproc.COLOR_BGR5652BGR); // esto le da 3 canales?
			
			// bitmapToMat nos devuelve una mat con 4 canales
			//Imgproc.cvtColor(m, m, Imgproc.COLOR_BGRA2BGR); // esto le da 3 canales?
			
			// Vuforia nos esta dando una imagen con resolucion de 720p (1,280 × 720)
			// y hacemos un resize 480p (720 × 480) para que la data a procesar sea menor
			//CustomLog.d(LOG_TAG, "vuforiaImageToMat - size original (Width x Height) (" + imageWidth + "x" + imageHeight + ")");
			//Mat result = new Mat(480, 720, CvType.CV_8UC2);
			Mat result = new Mat(480, 720, CvType.CV_8UC3);
			//Size s = new Size(720, 480);
			Imgproc.resize(m, result, result.size());
			//CustomLog.d(LOG_TAG, "vuforiaFrameToMat - result Mat = " + result);
			return result;
		}
		
		//CustomLog.d(LOG_TAG, "vuforiaFrameToMat - result Mat = " + m);
		return m;
		
		//return MatUtils.matFromBytes(imageWidth, imageHeight, pixelArray, grey);
	}
	
	public static Bitmap vuforiaFrameToBitmap(Frame frame) {
		for (int i = 0; i < frame.getNumImages(); ++i) {
			
			Image image = frame.getImage(i);
			
			if (image.getFormat() == PIXEL_FORMAT.RGB565) {			
				ByteBuffer pixels = image.getPixels();
				byte[] pixelArray = new byte[pixels.remaining()];
				pixels.get(pixelArray, 0, pixelArray.length);
				int imageWidth = image.getWidth();
				int imageHeight = image.getHeight();
				return createBitmapFromImagePixels(pixelArray, imageWidth, imageHeight);
			}
		}
		return null;
	}
	
	public static Bitmap createBitmapFromImagePixels(byte[] buffer, int width, int height)  {
	    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
	    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(buffer));  
	    return bitmap;
	}

}
