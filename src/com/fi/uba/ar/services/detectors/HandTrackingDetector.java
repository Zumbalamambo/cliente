package com.fi.uba.ar.services.detectors;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.lang.Math;

import org.opencv.core.Core;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.core.Rect;

import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.model.HandGesture;

//import com.ipaulpro.afilechooser.utils.FileUtils;

import android.os.Environment;
import android.os.Handler;
import android.content.Intent;
import android.util.Log;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.widget.Toast;

//XXX: este articulo ayuda a entender la logica de los algoritmos usados
// para detectar la mano http://www.andol.info/hci/1984.htm
// No es exactamente el mismo que aca pero la logica principal es parecida

public class HandTrackingDetector {

	// Just for debugging
	private static final String TAG = "HandTrackingDetector";

	// Color Space used for hand segmentation
	private static final int COLOR_SPACE = Imgproc.COLOR_RGB2Lab;

	// Number of frames collected for each gesture in the training set
	private static final int GES_FRAME_MAX = 10;

	public final Object sync = new Object();

	// Mode that presamples hand colors
	public static final int SAMPLE_MODE = 0;

	// Mode that generates binary image
	public static final int DETECTION_MODE = 1;

	// Mode that displays color image together with contours, fingertips,
	// defect points and so on.
	public static final int TRAIN_REC_MODE = 2;

	// Mode that presamples background colors
	public static final int BACKGROUND_MODE = 3;

	// Mode that is started when user clicks the 'Add Gesture' button.
	public static final int ADD_MODE = 4;

	// Mode that is started when user clicks the 'Test' button.
	public static final int TEST_MODE = 5;

	// Mode that is started when user clicks 'App Test' in the menu.
	public static final int APP_TEST_MODE = 6;

	// Mode that is started when user clicks 'Data Collection' in the menu.
	public static final int DATA_COLLECTION_MODE = 0;

	// Mode that is started when user clicks 'Map Apps' in the menu.
	public static final int MAP_APPS_MODE = 1;

	// Number of frames used for prediction
	private static final int FRAME_BUFFER_NUM = 1;

	// Frame interval between two launching events
	private static final int APP_TEST_DELAY_NUM = 10;
	
	private static final int BOUNDARY_MARGIN = 5;
	
	

	private boolean isPictureSaved = false;
	private int appTestFrameCount = 0;

	private int testFrameCount = 0;
	private float[][] values = new float[FRAME_BUFFER_NUM][];
	private int[][] indices = new int[FRAME_BUFFER_NUM][];

	// onActivityResult request
	private static final int REQUEST_CODE = 6384;

	private static final int REQUEST_SELECTED_APP = 1111;

	private String diagResult = null;
	private Handler mHandler = new Handler();
	private static final String DATASET_NAME = "/train_data.txt";

	private String storeFolderName = null;
	private File storeFolder = null;
	private FileWriter fw = null;

	// Stores the mapping results from gesture labels to app intents
	private HashMap<Integer, Intent> table = new HashMap<Integer, Intent>();

	// private List<AppInfo> mlistAppInfo = null;
	// private MyCameraView mOpenCvCameraView;
	private MenuItem[] mResolutionMenuItems;
	private SubMenu mResolutionMenu;

	private List<android.hardware.Camera.Size> mResolutionList;

	// Initial mode is BACKGROUND_MODE to presample the colors of the hand
	private int mode = BACKGROUND_MODE;

	private int chooserMode = DATA_COLLECTION_MODE;

	private static final int SAMPLE_NUM = 7;

	private Point[][] samplePoints = null;
	private double[][] avgColor = null;
	private double[][] avgBackColor = null;

	private double[] channelsPixel = new double[4];
	private ArrayList<ArrayList<Double>> averChans = new ArrayList<ArrayList<Double>>();

	private double[][] cLower = new double[SAMPLE_NUM][3];
	private double[][] cUpper = new double[SAMPLE_NUM][3];
	private double[][] cBackLower = new double[SAMPLE_NUM][3];
	private double[][] cBackUpper = new double[SAMPLE_NUM][3];

	private Scalar lowerBound = new Scalar(0, 0, 0);
	private Scalar upperBound = new Scalar(0, 0, 0);
	private int squareLen;

	private Mat sampleColorMat = null;
	private List<Mat> sampleColorMats = null;

	private Mat[] sampleMats = null;

	private Mat rgbaMat = null;

	private Mat rgbMat = null;
	private Mat bgrMat = null;

	private Mat interMat = null;

	private Mat binMat = null;
	private Mat binTmpMat = null;
	private Mat binTmpMat2 = null;
	private Mat binTmpMat0 = null;
	private Mat binTmpMat3 = null;

	private Mat tmpMat = null;
	private Mat backMat = null;
	private Mat difMat = null;
	private Mat binDifMat = null;

	private Scalar mColorsRGB[] = null;

	// Stores all the information about the hand
	private HandGesture hg = null;

	private int imgNum;
	private int gesFrameCount;
	private int curLabel = 0;
	private int selectedLabel = -2;
	private int curMaxLabel = 0;
	private int selectedMappedLabel = -2;

	// Stores string representation of features to be written to train_data.txt
	private ArrayList<String> feaStrs = new ArrayList<String>();

	File sdCardDir = Environment.getExternalStorageDirectory();
	File sdFile = new File(sdCardDir, "AppMap.txt");

	// svm native
	private native int trainClassifierNative(String trainingFile,
			int kernelType, int cost, float gamma, int isProb, String modelFile);

	private native int doClassificationNative(float values[][],
			int indices[][], int isProb, String modelFile, int labels[], double probs[]);

	// SVM training which outputs a file named as "model" in MyDataSet
	private void train() {
		// Svm training
		int kernelType = 2; // Radial basis function
		int cost = 4; // Cost
		int isProb = 0;
		float gamma = 0.001f; // Gamma
		String trainingFileLoc = storeFolderName + DATASET_NAME;
		String modelFileLoc = storeFolderName + "/model";
		Log.i("Store Path", modelFileLoc);

		if (trainClassifierNative(trainingFileLoc, kernelType, cost, gamma, isProb, modelFileLoc) == -1) {
			Log.d(TAG, "training err");
			// finish(); //XXX esto llamaba Activity.finish()
		}

		Toast.makeText(MainApplication.getInstance().getAppContext(), "Training is done", 2000).show();
	}

	public void initLabel() {

		File file[] = storeFolder.listFiles();

		int maxLabel = 0;
		for (int i = 0; i < file.length; i++) {

			String fullName = file[i].getName();

			final int dotId = fullName.lastIndexOf('.');
			if (dotId > 0) {
				String name = fullName.substring(0, dotId);
				String extName = fullName.substring(dotId + 1);
				if (extName.equals("jpg")) {
					int curName = Integer.valueOf(name);
					if (curName > maxLabel)
						maxLabel = curName;
				}
			}
		}

		curLabel = maxLabel;
		curMaxLabel = curLabel;

	}

	public void nextState() {
		// Mode flow: 
		// BACKGROUND_MODE --> SAMPLE_MODE --> DETECTION_MODE <--> TRAIN_REC_MODE
		String toastStr = null;
		
		if (mode == BACKGROUND_MODE) {
			toastStr = "First background sampled!";
			rgbaMat.copyTo(backMat);
			mode = SAMPLE_MODE;
			
		} else if (mode == SAMPLE_MODE) {
			mode = DETECTION_MODE;
			toastStr = "Sampling Finished!";

		} else if (mode == DETECTION_MODE) {
			mode = TRAIN_REC_MODE;
			toastStr = "Binary Display Finished!";
			preTrain();

		} else if (mode == TRAIN_REC_MODE) {
			mode = DETECTION_MODE;
			toastStr = "train finished!";
		}
		
		displayToastMsg(toastStr);

	}

	/* Checks if external storage is available for read and write */
	public boolean isExternalStorageWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			return true;
		}
		return false;
	}

	// Called when user clicks "Add Gesture" button
	// Prepare train_data.txt file and set the mode to be ADD_MODE
	public void addNewGesture(View view) {

		if (mode == TRAIN_REC_MODE) {
			if (storeFolder != null) {
				File myFile = new File(storeFolderName + DATASET_NAME);

				if (myFile.exists()) {

				} else {
					try {
						myFile.createNewFile();
					} catch (Exception e) {
						Toast.makeText(
								MainApplication.getInstance().getAppContext(),
								"Failed to create dataset at " + myFile,
								Toast.LENGTH_SHORT).show();
					}
				}

				try {
					fw = new FileWriter(myFile, true);

					feaStrs.clear();

					if (selectedLabel == -2)
						curLabel = curMaxLabel + 1;
					else {
						curLabel++;
						selectedLabel = -2;
					}

					gesFrameCount = 0;
					mode = ADD_MODE;

				} catch (FileNotFoundException e) {
					e.printStackTrace();
					Log.i(TAG,
							"******* File not found. Did you"
									+ " add a WRITE_EXTERNAL_STORAGE permission to the manifest?");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		} else {
			Toast.makeText(MainApplication.getInstance().getAppContext(),
					"Please do it in TRAIN_REC mode", Toast.LENGTH_SHORT)
					.show();
		}

	}

	// Write the strings of features to the file train_data.txt
	// Save the screenshot of the gesture
	public void doAddNewGesture() {
		try {
			for (int i = 0; i < feaStrs.size(); i++) {
				fw.write(feaStrs.get(i));
			}
			fw.close();

		} catch (Exception e) {
		}

		savePicture();

		if (curLabel > curMaxLabel) {
			curMaxLabel = curLabel;
		}

	}

	public void doDeleteGesture(int label) {

	}
	
	private void displayToastMsg(final String msg) {
		Runnable toastRunnable = new Runnable() {
			@Override
			public void run() {
				{					
					Toast.makeText(
							MainApplication.getInstance().getAppContext(),
							msg,
							Toast.LENGTH_SHORT).show();
				}
			}
		};

		mHandler.post(toastRunnable);
		
	}

	// All the trained gestures jpg files and SVM training model, train_data.txt
	// are stored in ExternalStorageDirectory/MyDataSet
	// If MyDataSet doesn't exist, then it will be created in this function
	public void preTrain() {
		if (!isExternalStorageWritable()) {
			displayToastMsg("External storage is not writable!");

		} else if (storeFolder == null) {
			storeFolderName = Environment.getExternalStorageDirectory() + "/MyDataSet";
			storeFolder = new File(storeFolderName);
			boolean success = true;
			if (!storeFolder.exists()) {
				success = storeFolder.mkdir();
			}
			if (success) {
				// Do something on success

			} else {
				displayToastMsg("Failed to create directory " + storeFolderName);				
				storeFolder = null;
				storeFolderName = null;
			}
		}

		if (storeFolder != null) {
			initLabel();
		}

	}

	// Called when user clicks "Train" button
	public void train(View view) {
		train();
	}

	// Called when user clicks "Test" button
	public void test(View view) {
		if (mode == TRAIN_REC_MODE)
			mode = TEST_MODE;
		else if (mode == TEST_MODE) {
			mode = TRAIN_REC_MODE;
		}
	}

	boolean savePicture() {
		Mat img;

		if (((mode == BACKGROUND_MODE) || 
			 (mode == SAMPLE_MODE) || 
			 (mode == TRAIN_REC_MODE)) || 
			 (mode == ADD_MODE) || 
			 (mode == TEST_MODE)) {
			Imgproc.cvtColor(rgbaMat, bgrMat, Imgproc.COLOR_RGBA2BGR, 3);
			img = bgrMat;
		} else if (mode == DETECTION_MODE) {
			img = binMat;
		} else
			img = null;

		if (img != null) {
			if (!isExternalStorageWritable()) {
				displayToastMsg("External storage is not writable!");
				return false;
			}

			File path;
			String filename;
			if (mode != ADD_MODE) {
				path = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
				filename = "image_" + imgNum + ".jpg";
			} else {
				path = storeFolder;
				filename = curLabel + ".jpg";
			}

			imgNum++;
			File file = new File(path, filename);

			Boolean bool = false;
			filename = file.toString();

			bool = Highgui.imwrite(filename, img);

			if (bool == true) {
				displayToastMsg("Saved as " + filename);
				Log.d(TAG, "Succeed writing image to" + filename);
			} else
				Log.d(TAG, "Fail writing image to external storage");

			return bool;
		}

		return false;
	}

	// Just initialize boundaries of the first sample
	void initCLowerUpper(double cl1, double cu1, double cl2, double cu2, double cl3, double cu3) {
		cLower[0][0] = cl1;
		cUpper[0][0] = cu1;
		cLower[0][1] = cl2;
		cUpper[0][1] = cu2;
		cLower[0][2] = cl3;
		cUpper[0][2] = cu3;
	}

	void initCBackLowerUpper(double cl1, double cu1, double cl2, double cu2, double cl3, double cu3) {
		cBackLower[0][0] = cl1;
		cBackUpper[0][0] = cu1;
		cBackLower[0][1] = cl2;
		cBackUpper[0][1] = cu2;
		cBackLower[0][2] = cl3;
		cBackUpper[0][2] = cu3;
	}

	public void releaseCVMats() {
		releaseCVMat(sampleColorMat);
		sampleColorMat = null;

		if (sampleColorMats != null) {
			for (int i = 0; i < sampleColorMats.size(); i++) {
				releaseCVMat(sampleColorMats.get(i));

			}
		}
		sampleColorMats = null;

		if (sampleMats != null) {
			for (int i = 0; i < sampleMats.length; i++) {
				releaseCVMat(sampleMats[i]);
			}
		}
		sampleMats = null;

		releaseCVMat(rgbMat);
		rgbMat = null;

		releaseCVMat(bgrMat);
		bgrMat = null;

		releaseCVMat(interMat);
		interMat = null;

		releaseCVMat(binMat);
		binMat = null;

		releaseCVMat(binTmpMat0);
		binTmpMat0 = null;

		releaseCVMat(binTmpMat3);
		binTmpMat3 = null;

		releaseCVMat(binTmpMat2);
		binTmpMat2 = null;

		releaseCVMat(tmpMat);
		tmpMat = null;

		releaseCVMat(backMat);
		backMat = null;

		releaseCVMat(difMat);
		difMat = null;

		releaseCVMat(binDifMat);
		binDifMat = null;
	}

	public void releaseCVMat(Mat img) {
		if (img != null)
			img.release();
	}

	public void init() {

		samplePoints = new Point[SAMPLE_NUM][2];
		for (int i = 0; i < SAMPLE_NUM; i++) {
			for (int j = 0; j < 2; j++) {
				samplePoints[i][j] = new Point();
			}
		}

		avgColor = new double[SAMPLE_NUM][3];
		avgBackColor = new double[SAMPLE_NUM][3];

		for (int i = 0; i < 3; i++)
			averChans.add(new ArrayList<Double>());

		// HLS
		// initCLowerUpper(7, 7, 80, 80, 80, 80);

		// RGB
		// initCLowerUpper(30, 30, 30, 30, 30, 30);

		// HSV
		// initCLowerUpper(15, 15, 50, 50, 50, 50);
		// initCBackLowerUpper(5, 5, 80, 80, 100, 100);

		// Ycrcb
		// initCLowerUpper(40, 40, 10, 10, 10, 10);

		// Lab
		initCLowerUpper(50, 50, 10, 10, 10, 10);
		initCBackLowerUpper(50, 50, 3, 3, 3, 3);

		if (sampleColorMat == null)
			sampleColorMat = new Mat();

		if (sampleColorMats == null)
			sampleColorMats = new ArrayList<Mat>();

		if (sampleMats == null) {
			sampleMats = new Mat[SAMPLE_NUM];
			for (int i = 0; i < SAMPLE_NUM; i++)
				sampleMats[i] = new Mat();
		}

		if (rgbMat == null)
			rgbMat = new Mat();

		if (bgrMat == null)
			bgrMat = new Mat();

		if (interMat == null)
			interMat = new Mat();

		if (binMat == null)
			binMat = new Mat();

		if (binTmpMat == null)
			binTmpMat = new Mat();

		if (binTmpMat2 == null)
			binTmpMat2 = new Mat();

		if (binTmpMat0 == null)
			binTmpMat0 = new Mat();

		if (binTmpMat3 == null)
			binTmpMat3 = new Mat();

		if (tmpMat == null)
			tmpMat = new Mat();

		if (backMat == null)
			backMat = new Mat();

		if (difMat == null)
			difMat = new Mat();

		if (binDifMat == null)
			binDifMat = new Mat();

		if (hg == null)
			hg = new HandGesture();

		mColorsRGB = new Scalar[] { 
				new Scalar(255, 0, 0, 255),
				new Scalar(0, 255, 0, 255), 
				new Scalar(0, 0, 255, 255) 
		};

	}

	
	private void fixupInternalMats(Mat inputFrame) {

		rgbaMat = inputFrame;
		
		// XXX: la mat que nos llega desde vuforia esta en formato BGR
		// para que todo el codigo este acorde al original transformamos a RGBA
		Imgproc.cvtColor(inputFrame, rgbaMat, Imgproc.COLOR_BGR2RGBA);

		//Core.flip(rgbaMat, rgbaMat, 1); // flips around y axis
		Core.flip(rgbaMat, rgbaMat, 0); // flips around x axis - this is necessary because we don't want a mirrored image
		
		Imgproc.GaussianBlur(rgbaMat, rgbaMat, new Size(5, 5), 5, 5);

		Imgproc.cvtColor(rgbaMat, rgbMat, Imgproc.COLOR_RGBA2RGB);

		// Convert original RGB colorspace to the colorspace indicated by COLOR_SPACE
		Imgproc.cvtColor(rgbaMat, interMat, COLOR_SPACE);
	}
	
	//XXX: codigo original hacia:
	// Called when each frame data gets received
	// inputFrame contains the data for each frame
	// Mode flow: 
	// BACKGROUND_MODE --> SAMPLE_MODE --> DETECTION_MODE <--> TRAIN_REC_MODE
	
	//XXX: en el caso original este metodo en realidad era el que se llamaba por cada frame
	// de opencv, lo que aseguraba un flujo constante y al momento de tocar la pantalla
	// se llamaba a lo que ahora es nextState y asi se tomaba el ultimo (o frame actual digamos)
	// para cada accion.
	// Ahora esto se hace de forma asincronica con lo cual no podemos estar seguros que al momento
	// de tocar el boton realmente se este tomando lo mismo que esta en la pantalla ya que eso 
	// es de Vuforia y el servicio en background quizas no envio los frames a esta funcion.
	// Lo que vamos a hacer entonces es que el sampling del color de fondo y color de mano se hagan sync
	// y mientras tanto aca vamos a descartar todo hasta que esas 2 cosas ya se hayan hecho
	
	//TODO: el metodo de analizar los defect points no sirve para el caso en el que hay un unico dedo!
	// esto ya estaba contemplado en la version nativa orginal
	
	public Mat detect(Mat inputFrame) {
		if ((mode == BACKGROUND_MODE) || (mode == SAMPLE_MODE))
			return null;
		
		fixupInternalMats(inputFrame);

		hg.resetInternalValues();
		
		/*
		if (mode == BACKGROUND_MODE) { 
			// First mode which presamples background colors
			preSampleBack(rgbaMat);
		}
		else if (mode == SAMPLE_MODE) { 
			// Second mode which presamples the colors of the hand
			preSampleHand(rgbaMat);

		} else */ 
		
		if (mode == DETECTION_MODE) { 
			// Third mode which generates the binary image containing the
			// segmented hand represented by white color
			produceBinImg(interMat, binMat);
			return binMat;

		} else if (
				(mode == TRAIN_REC_MODE) || 
				(mode == ADD_MODE) || 
				(mode == TEST_MODE) || 
				(mode == APP_TEST_MODE) ) {

			produceBinImg(interMat, binMat);

			makeContours();

			String entry = hg.featureExtraction(rgbaMat, curLabel);

			// Collecting the frame data of a certain gesture and storing it in
			// the file train_data.txt.
			// This mode stops when the number of frames processed equals
			// GES_FRAME_MAX
			if (mode == ADD_MODE) {
				gesFrameCount++;
				Core.putText(rgbaMat, Integer.toString(gesFrameCount),
						new Point(10, 10), Core.FONT_HERSHEY_SIMPLEX, 0.6,
						Scalar.all(0));

				feaStrs.add(entry);

				if (gesFrameCount == GES_FRAME_MAX) {

					Runnable runnableShowBeforeAdd = new Runnable() {
						@Override
						public void run() {
							{
								// showDialogBeforeAdd("Add or not",
								// "Add this new gesture labeled as " + curLabel
								// + "?");
								Toast.makeText(
										MainApplication.getInstance()
												.getAppContext(),
										"Add this new gesture labeled as "
												+ curLabel + "?",
										Toast.LENGTH_SHORT).show();
							}
						}
					};

					mHandler.post(runnableShowBeforeAdd);

					try {
						synchronized (sync) {
							sync.wait();
						}
					} catch (Exception e) {
					}

					mode = TRAIN_REC_MODE;
				}
			} else if ((mode == TEST_MODE) || (mode == APP_TEST_MODE)) {
				Double[] doubleValue = hg.features.toArray(new Double[hg.features.size()]);
				values[0] = new float[doubleValue.length];
				indices[0] = new int[doubleValue.length];

				for (int i = 0; i < doubleValue.length; i++) {
					values[0][i] = (float) (doubleValue[i] * 1.0f);
					indices[0][i] = i + 1;
				}

				int isProb = 0;

				String modelFile = storeFolderName + "/model";
				int[] returnedLabel = { 0 };
				double[] returnedProb = { 0.0 };

				// Predicted labels are stored in returnedLabel
				// Since currently prediction is made for each frame, only
				// returnedLabel[0] is useful.
				int r = doClassificationNative(values, indices, isProb, modelFile, returnedLabel, returnedProb);

				if (r == 0) {

					if (mode == TEST_MODE)
						Core.putText(rgbaMat, 
								Integer.toString(returnedLabel[0]), new Point(15, 15),
								Core.FONT_HERSHEY_SIMPLEX, 0.6, mColorsRGB[0]);
					
					else if (mode == APP_TEST_MODE) { // Launching other apps
						Core.putText(rgbaMat,
								Integer.toString(returnedLabel[0]), new Point(15, 15),
								Core.FONT_HERSHEY_SIMPLEX, 0.6, mColorsRGB[2]);

						if (returnedLabel[0] != 0) {

							if (appTestFrameCount == APP_TEST_DELAY_NUM) {

								// Call other apps according to the predicted label
								// This is done every APP_TEST_DELAY_NUM frames
								// callAppByLabel(returnedLabel[0]);
							} else {
								appTestFrameCount++;
							}
						}
					}
				}
			}

		}

		if (isPictureSaved) {
			savePicture();
			isPictureSaved = false;
		}
		
		//XXX: a modo de testing vamos a tomar los fingerTips y enviarlos
		// a algun lugar para que puedan ser dibujados en el HandDebugView
		return rgbaMat;

	}
	
	public void doColorSampling(Mat inputFrame) {		
		fixupInternalMats(inputFrame);
		
		if (mode == BACKGROUND_MODE) { 			
			// First mode which presamples background colors
			preSampleBack(rgbaMat);

		} else if (mode == SAMPLE_MODE) { 			
			// Second mode which presamples the colors of the hand
			preSampleHand(rgbaMat);
			
		} else
			return;
		
		nextState();
	}
	
    // devuelve true si ya se hizo sampling de fondo y mano
    public boolean isColorSamplingDone() {
    	return ( (mode != BACKGROUND_MODE) && (mode != SAMPLE_MODE) );
    }

	
	// Presampling hand colors.
	// Output is avgColor, which is essentially a 7 by 3 matrix storing the
	// colors sampled by seven squares
	void preSampleHand(Mat img) {
		int cols = img.cols();
		int rows = img.rows();
		squareLen = rows / 20;
		Scalar color = mColorsRGB[2]; // Blue Outline

		samplePoints[0][0].x = cols / 2;
		samplePoints[0][0].y = rows / 4;
		samplePoints[1][0].x = cols * 5 / 12;
		samplePoints[1][0].y = rows * 5 / 12;
		samplePoints[2][0].x = cols * 7 / 12;
		samplePoints[2][0].y = rows * 5 / 12;
		samplePoints[3][0].x = cols / 2;
		samplePoints[3][0].y = rows * 7 / 12;
		samplePoints[4][0].x = cols / 1.5;
		samplePoints[4][0].y = rows * 7 / 12;
		samplePoints[5][0].x = cols * 4 / 9;
		samplePoints[5][0].y = rows * 3 / 4;
		samplePoints[6][0].x = cols * 5 / 9;
		samplePoints[6][0].y = rows * 3 / 4;

		for (int i = 0; i < SAMPLE_NUM; i++) {
			samplePoints[i][1].x = samplePoints[i][0].x + squareLen;
			samplePoints[i][1].y = samplePoints[i][0].y + squareLen;
		}

		for (int i = 0; i < SAMPLE_NUM; i++) {
			Core.rectangle(img, samplePoints[i][0], samplePoints[i][1], color, 1);
		}

		for (int i = 0; i < SAMPLE_NUM; i++) {
			for (int j = 0; j < 3; j++) {
				avgColor[i][j] = (interMat.get(
						(int) (samplePoints[i][0].y + squareLen / 2),
						(int) (samplePoints[i][0].x + squareLen / 2)))[j];
			}
		}

	}

	// Presampling background colors.
	// Output is avgBackColor, which is essentially a 7 by 3 matrix storing the
	// colors sampled by seven squares
	void preSampleBack(Mat img) {
		int cols = img.cols();
		int rows = img.rows();
		squareLen = rows / 20;
		Scalar color = mColorsRGB[2]; // Blue Outline

		samplePoints[0][0].x = cols / 6;
		samplePoints[0][0].y = rows / 3;
		samplePoints[1][0].x = cols / 6;
		samplePoints[1][0].y = rows * 2 / 3;
		samplePoints[2][0].x = cols / 2;
		samplePoints[2][0].y = rows / 6;
		samplePoints[3][0].x = cols / 2;
		samplePoints[3][0].y = rows / 2;
		samplePoints[4][0].x = cols / 2;
		samplePoints[4][0].y = rows * 5 / 6;
		samplePoints[5][0].x = cols * 5 / 6;
		samplePoints[5][0].y = rows / 3;
		samplePoints[6][0].x = cols * 5 / 6;
		samplePoints[6][0].y = rows * 2 / 3;

		for (int i = 0; i < SAMPLE_NUM; i++) {
			samplePoints[i][1].x = samplePoints[i][0].x + squareLen;
			samplePoints[i][1].y = samplePoints[i][0].y + squareLen;
		}

		for (int i = 0; i < SAMPLE_NUM; i++) {
			Core.rectangle(img, samplePoints[i][0], samplePoints[i][1], color, 1);
		}

		for (int i = 0; i < SAMPLE_NUM; i++) {
			for (int j = 0; j < 3; j++) {
				avgBackColor[i][j] = (interMat.get(
						(int) (samplePoints[i][0].y + squareLen / 2),
						(int) (samplePoints[i][0].x + squareLen / 2)))[j];
			}
		}

	}

	void boundariesCorrection() {
		for (int i = 1; i < SAMPLE_NUM; i++) {
			for (int j = 0; j < 3; j++) {
				cLower[i][j] = cLower[0][j];
				cUpper[i][j] = cUpper[0][j];

				cBackLower[i][j] = cBackLower[0][j];
				cBackUpper[i][j] = cBackUpper[0][j];
			}
		}

		for (int i = 0; i < SAMPLE_NUM; i++) {
			for (int j = 0; j < 3; j++) {
				if (avgColor[i][j] - cLower[i][j] < 0)
					cLower[i][j] = avgColor[i][j];

				if (avgColor[i][j] + cUpper[i][j] > 255)
					cUpper[i][j] = 255 - avgColor[i][j];

				if (avgBackColor[i][j] - cBackLower[i][j] < 0)
					cBackLower[i][j] = avgBackColor[i][j];

				if (avgBackColor[i][j] + cBackUpper[i][j] > 255)
					cBackUpper[i][j] = 255 - avgBackColor[i][j];
			}
		}
	}

	//XXX: parece que no la llama nadie...
	void cropBinImg(Mat imgIn, Mat imgOut) {
		imgIn.copyTo(binTmpMat3);

		Rect boxRect = makeBoundingBox(binTmpMat3);
		Rect finalRect = null;

		if (boxRect != null) {
			Mat roi = new Mat(imgIn, boxRect);
			int armMargin = 2;

			Point tl = boxRect.tl();
			Point br = boxRect.br();

			int colNum = imgIn.cols();
			int rowNum = imgIn.rows();

			int wristThresh = 10;

			List<Integer> countOnes = new ArrayList<Integer>();

			if (tl.x < armMargin) {
				double rowLimit = br.y;
				int localMinId = 0;
				for (int x = (int) tl.x; x < br.x; x++) {
					int curOnes = Core.countNonZero(roi.col(x));
					int lstTail = countOnes.size() - 1;
					if (lstTail >= 0) {
						if (curOnes < countOnes.get(lstTail)) {
							localMinId = x;
						}
					}

					if (curOnes > (countOnes.get(localMinId) + wristThresh))
						break;

					countOnes.add(curOnes);
				}

				Rect newBoxRect = new Rect(new Point(localMinId, tl.y), br);
				roi = new Mat(imgIn, newBoxRect);

				Point newtl = newBoxRect.tl();
				Point newbr = newBoxRect.br();

				int y1 = (int) newBoxRect.tl().y;
				while (Core.countNonZero(roi.row(y1)) < 2) {
					y1++;
				}

				int y2 = (int) newBoxRect.br().y;
				while (Core.countNonZero(roi.row(y2)) < 2) {
					y2--;
				}
				finalRect = new Rect(new Point(newtl.x, y1), new Point(newbr.x, y2));
			} else if (br.y > rowNum - armMargin) {
				double rowLimit = br.y;

				int scanCount = 0;
				int scanLength = 8;
				int scanDelta = 8;
				int y;
				for (y = (int) br.y - 1; y > tl.y; y--) {
					int curOnes = Core.countNonZero((roi.row(y - (int) tl.y)));
					int lstTail = countOnes.size() - 1;
					if (lstTail >= 0) {
						countOnes.add(curOnes);

						if (scanCount % scanLength == 0) {
							int curDelta = curOnes - countOnes.get(scanCount - 5);
							if (curDelta > scanDelta)
								break;
						}

					} else
						countOnes.add(curOnes);

					scanCount++;
				}

				finalRect = new Rect(tl, new Point(br.x, y + scanLength));

			}

			if (finalRect != null) {
				roi = new Mat(imgIn, finalRect);
				roi.copyTo(tmpMat);
				imgIn.copyTo(imgOut);
				imgOut.setTo(Scalar.all(0));
				roi = new Mat(imgOut, finalRect);
				tmpMat.copyTo(roi);
			}

		}

	}

	void adjustBoundingBox(Rect initRect, Mat img) {

	}

	// Generates binary image containing user's hand
	void produceBinImg(Mat imgIn, Mat imgOut) {
		int colNum = imgIn.cols();
		int rowNum = imgIn.rows();
		int boxExtension = 0;

		boundariesCorrection();

		produceBinHandImg(imgIn, binTmpMat);

		produceBinBackImg(imgIn, binTmpMat2);

		Core.bitwise_and(binTmpMat, binTmpMat2, binTmpMat);
		binTmpMat.copyTo(tmpMat);
		binTmpMat.copyTo(imgOut);

		Rect roiRect = makeBoundingBox(tmpMat);
		
		//XXX: la funcion adjust no hace nada...
		adjustBoundingBox(roiRect, binTmpMat);

		if (roiRect != null) {
			roiRect.x = Math.max(0, roiRect.x - boxExtension);
			roiRect.y = Math.max(0, roiRect.y - boxExtension);
			roiRect.width = Math.min(roiRect.width + boxExtension, colNum);
			roiRect.height = Math.min(roiRect.height + boxExtension, rowNum);

			Mat roi1 = new Mat(binTmpMat, roiRect);
			Mat roi3 = new Mat(imgOut, roiRect);
			imgOut.setTo(Scalar.all(0));

			roi1.copyTo(roi3);

			Mat element = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, new Size(3, 3));
			Imgproc.dilate(roi3, roi3, element, new Point(-1, -1), 2);

			Imgproc.erode(roi3, roi3, element, new Point(-1, -1), 2);

		}

		// cropBinImg(imgOut, imgOut);

	}

	// Generates binary image thresholded only by sampled hand colors
	void produceBinHandImg(Mat imgIn, Mat imgOut) {
		for (int i = 0; i < SAMPLE_NUM; i++) {
			lowerBound.set(new double[] { 
					avgColor[i][0] - cLower[i][0],
					avgColor[i][1] - cLower[i][1],
					avgColor[i][2] - cLower[i][2] });
			upperBound.set(new double[] { 
					avgColor[i][0] + cUpper[i][0],
					avgColor[i][1] + cUpper[i][1],
					avgColor[i][2] + cUpper[i][2] });

			Core.inRange(imgIn, lowerBound, upperBound, sampleMats[i]);

		}

		imgOut.release();
		sampleMats[0].copyTo(imgOut);

		for (int i = 1; i < SAMPLE_NUM; i++) {
			Core.add(imgOut, sampleMats[i], imgOut);
		}

		Imgproc.medianBlur(imgOut, imgOut, 3);
	}

	// Generates binary image thresholded only by sampled background colors
	void produceBinBackImg(Mat imgIn, Mat imgOut) {
		for (int i = 0; i < SAMPLE_NUM; i++) {
			lowerBound.set(new double[] {
					avgBackColor[i][0] - cBackLower[i][0],
					avgBackColor[i][1] - cBackLower[i][1],
					avgBackColor[i][2] - cBackLower[i][2] });
			upperBound.set(new double[] {
					avgBackColor[i][0] + cBackUpper[i][0],
					avgBackColor[i][1] + cBackUpper[i][1],
					avgBackColor[i][2] + cBackUpper[i][2] });

			Core.inRange(imgIn, lowerBound, upperBound, sampleMats[i]);
		}

		imgOut.release();
		sampleMats[0].copyTo(imgOut);

		for (int i = 1; i < SAMPLE_NUM; i++) {
			Core.add(imgOut, sampleMats[i], imgOut);
		}

		Core.bitwise_not(imgOut, imgOut);

		Imgproc.medianBlur(imgOut, imgOut, 7);

	}

	//XXX: no se usa en ningun lugar
	void dilate(Mat img) {
		int cols = img.cols();
		int rows = img.rows();

		Mat element = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));

		Mat roi = new Mat(img, new Rect(cols / 4, rows / 6, cols / 2, rows * 2 / 3));

		Imgproc.dilate(roi, roi, element);

	}

	void makeContours() {
		hg.contours.clear();
		// https://opencv-python-tutroals.readthedocs.org/en/latest/py_tutorials/py_imgproc/py_contours/py_contours_begin/py_contours_begin.html
		// https://opencv-python-tutroals.readthedocs.org/en/latest/py_tutorials/py_imgproc/py_contours/py_contours_hierarchy/py_contours_hierarchy.html
		Imgproc.findContours(binMat, hg.contours, hg.hie, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);

		// Find biggest contour and return the index of the contour, which is hg.cMaxId
		hg.findBiggestContour();

		if (hg.cMaxId > -1) {

			hg.approxContour.fromList(hg.contours.get(hg.cMaxId).toList());
			Imgproc.approxPolyDP(hg.approxContour, hg.approxContour, 2, true);
			hg.contours.get(hg.cMaxId).fromList(hg.approxContour.toList());

			// hg.contours.get(hg.cMaxId) represents the contour of the hand
			// dibuja el contorno de la mano
			//XXX: no nos iteresa dibujar, asi que lo comentamos para ver si mejora la performance
			//Imgproc.drawContours(rgbaMat, hg.contours, hg.cMaxId, mColorsRGB[0], 1);

			// Palm center is stored in hg.inCircle, radius of the inscribed
			// circle is stored in hg.inCircleRadius
			hg.findInscribedCircle(rgbaMat);

			hg.boundingRect = Imgproc.boundingRect(hg.contours.get(hg.cMaxId));

			Imgproc.convexHull(hg.contours.get(hg.cMaxId), hg.hullIndices, false);

			hg.hullPoints.clear();
			for (int i = 0; i < hg.contours.size(); i++)
				hg.hullPoints.add(new MatOfPoint());

			int[] handConvexHullIndices = hg.hullIndices.toArray();
			List<Point> convexHullPointList = new ArrayList<Point>();
			Point[] contourPts = hg.contours.get(hg.cMaxId).toArray();

			for (int i = 0; i < handConvexHullIndices.length; i++) {
				convexHullPointList.add(contourPts[handConvexHullIndices[i]]);
				// dibuja un pequenio circulo en los puntos extremos del
				// contorno que corresponden al convex hull
				//XXX: no nos iteresa dibujar, asi que lo comentamos para ver si mejora la performance
				//Core.circle(rgbaMat, contourPts[handConvexHullIndices[i]], 2, new Scalar(241, 247, 45), 1);
			}

			// hg.hullP.get(hg.cMaxId) returns the locations of the points in
			// the convex hull of the hand
			hg.hullPoints.get(hg.cMaxId).fromList(convexHullPointList);
			convexHullPointList.clear();

			// muevo estos clear al metodo
			// com.fi.uba.ar.model.HandGesture.resetInternalValues()
			/*
			 * hg.fingerTips.clear(); hg.defectPoints.clear();
			 * hg.defectPointsOrdered.clear(); hg.fingerTipsOrdered.clear();
			 * hg.defectIdAfter.clear();
			 */

			// XXX: confirmo que estas condiciones hacen que no detecte cuando hay 1 unico dedo
			if ((contourPts.length >= 5) && 
				//XXX: esto esta solo procesando cuando hay al menos 5 puntos del convex hull? 
				(handConvexHullIndices.length >= 5) && 
				hg.detectIsHand(rgbaMat)) {

				// https://opencv-python-tutroals.readthedocs.org/en/latest/py_tutorials/py_imgproc/py_contours/py_contours_more_functions/py_contours_more_functions.html
				// aclara mucho sobre los convexityDefects
				Imgproc.convexityDefects(hg.contours.get(hg.cMaxId), hg.hullIndices, hg.defects);
				List<Integer> defectsList = hg.defects.toList();

				Point prevPoint = null;

				for (int i = 0; i < defectsList.size(); i++) {
					int id = i % 4;
					Point defectFarthestPoint;

					// hace esto para procesar de a 4 valores por vez
					// Esto es asi porque hg.defects es MatOfInt4
					// esto nos da info de como es el contenido
					// https://stackoverflow.com/questions/18073239/opencv-java-convexity-defects-computer-vision
					/*
					 * struct CvConvexityDefect { 
					 * 		CvPoint* start; // point of the contour where the defect begins 
					 * 		CvPoint* end; // point of the contour where the defect ends 
					 * 		CvPoint* depth_point; // the farthest from the convex hull point within the defect 
					 * 		float depth; // distance between the farthest point and the convex hull 
					 * };
					 */

					if (id == 2) { // Defect point

						// XXX: parece que este es el codigo con el que se
						// calcula la posicion de cada dedo
						// pero no me queda claro bien porque tambien
						// HandGesture.extractFeatures es la que dibuja cada dedo

						// http://creat-tabu.blogspot.ro/2013/08/opencv-python-hand-gesture-recognition.html
						// en ese articulo se muestra bien los defect points

						// XXX: comparando el codigo del chino en parte es igual
						// al inicio de la funcion fingertips
						// Pero tiene otra logica para determinar si son dedos o no
						double depth = (double) defectsList.get(i + 1) / 256.0;

						defectFarthestPoint = contourPts[defectsList.get(i)];

						Point defectStartPoint = contourPts[defectsList .get(i - 2)];
						Point vec0 = new Point(	defectStartPoint.x - defectFarthestPoint.x, 
												defectStartPoint.y - defectFarthestPoint.y);

						Point defectEndPoint = contourPts[defectsList .get(i - 1)];
						Point vec1 = new Point(	defectEndPoint.x - defectFarthestPoint.x, 
												defectEndPoint.y - defectFarthestPoint.y);

						double dot = vec0.x * vec1.x + vec0.y * vec1.y;

						// esto basicamente es el teorema de pitagoras
						// (https://es.wikipedia.org/wiki/Teorema_de_Pit%C3%A1goras)
						double lenth0 = Math.sqrt(vec0.x * vec0.x + vec0.y * vec0.y);
						double lenth1 = Math.sqrt(vec1.x * vec1.x + vec1.y * vec1.y);

						// esta usando esto?
						// https://es.wikipedia.org/wiki/Teorema_del_coseno
						double cosTheta = dot / (lenth0 * lenth1);

						// Esto esta filtrando cuales son los defect points que
						// se consideran "validos" por asi decirlo
						// necesito entender porque esta usando estos valores y
						// quizas hay que ajustarlos para mejorar la deteccion
						// cuando los dedos no estan tan separados
						/*
						 * if ((depth > hg.palmCircleRadius * 0.7) && 
						 * 		(cosTheta >= -0.7) && 
						 * 		(!isClosedToBoundary(defectStartPoint, rgbaMat)) && 
						 * 		(!isClosedToBoundary(defectEndPoint, rgbaMat))) {
						 */
						
						// Segun la explicacion dada en 
						// https://eaglesky.github.io/blog/2014/03/27/color-based-hand-gesture-recognition-on-android/
						// acerca de como se descartan los defect points, las condiciones de descarte son:
						// 1 - depth <= CircleRadius * 0.7
						// 2 - cos(angle) <= -0.7
						// 3 - isCloseToBoundary == true
						// 4 - index of defect > 4
						
						// Pareceria que estos chequeos son los de aca abajo en el if, pero esta haciendo comparaciones
						// opuestas ya que en realidad no descarta sino que solo guarda los que cumplen las condiciones
						
						if ( // esta primera condicion parece comparar si la distancia es mayor que la mitad del radio de la palma y en ese caso puede
							(depth > hg.palmCircleRadius * 0.5) 
							&& (cosTheta >= -0.2) // XXX: el chino compara el angulo con < 0.2
							// descarta si los puntos estan muy cerca de los bordes de la imagen
							&& (!isCloseToBoundary(defectStartPoint, rgbaMat))
							&& (!isCloseToBoundary(defectEndPoint, rgbaMat))) {

							hg.defectIdAfter.add((i));

							Point finVec0 = new Point(defectStartPoint.x - hg.palmCircleCenter.x, 
													  defectStartPoint.y - hg.palmCircleCenter.y);
							double finAngle0 = Math.atan2(finVec0.y, finVec0.x);
							
							Point finVec1 = new Point(defectEndPoint.x - hg.palmCircleCenter.x, 
													  defectEndPoint.y - hg.palmCircleCenter.y);
							double finAngle1 = Math.atan2(finVec1.y, finVec1.x);

							// XXX: no se porque hace esto aca y pone los fingerTipsOrdered que pone...
							// suele poner mas de 5 elementos
							// y ademas el lugar donde dibuja los punto en cada
							// dedo y las lineas desde el centro de la palma para cada dedo
							// lo que se ve en la Mat lo hace en
							// com.fi.uba.ar.model.HandGesture.featureExtraction(Mat, int)

							// Esta funcion "makeContours" se ejecuta justo antes que featureExtraction

							// segun lo que veo, fingerTipsOrdered no contiene lo
							// mismo que fingerTips (que se llena con datos en featureExtraction)
							// A veces tienen los mismos puntos o algunos
							// iguales, pero fingerTipsOrdered suele tener siempre otros mas
							// y muchas veces mas de 5... no tiene mucho sentido

							// XXX: este codigo hace exactamente lo mismo en el if y el else. porque??
							if (hg.fingerTipsOrdered.size() == 0) {
								hg.fingerTipsOrdered.put(finAngle0, defectStartPoint);
								hg.fingerTipsOrdered.put(finAngle1, defectEndPoint);
							} else {
								hg.fingerTipsOrdered.put(finAngle0, defectStartPoint);
								hg.fingerTipsOrdered.put(finAngle1, defectEndPoint);
							}
						}
					}
				}
			}
		}

		if (hg.detectIsHand(rgbaMat)) {
			//XXX: no nos iteresa dibujar, asi que lo comentamos para ver si mejora la performance
			// hg.boundingRect represents four coordinates of the bounding box.			
			//Core.rectangle(rgbaMat, hg.boundingRect.tl(), hg.boundingRect.br(), mColorsRGB[1], 2);
			// dibuja el convex hull de la mano
			//Imgproc.drawContours(rgbaMat, hg.hullPoints, hg.cMaxId, mColorsRGB[2]);
		}
	}

	boolean isCloseToBoundary(Point pt, Mat img) {
		if ((pt.x > BOUNDARY_MARGIN) && 
			(pt.y > BOUNDARY_MARGIN) && 
			(pt.x < img.cols() - BOUNDARY_MARGIN) && 
			(pt.y < img.rows() - BOUNDARY_MARGIN)) {
			return false;
		}
		return true;
	}

	Rect makeBoundingBox(Mat img) {
		hg.contours.clear();
		Imgproc.findContours(img, hg.contours, hg.hie, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);
		hg.findBiggestContour();

		if (hg.cMaxId > -1) {
			hg.boundingRect = Imgproc.boundingRect(hg.contours.get(hg.cMaxId));
		}

		if (hg.detectIsHand(rgbaMat)) {
			return hg.boundingRect;
		} else
			return null;
	}

	public static boolean deleteDir(File dir) {
		if (dir != null && dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}

		// The directory is now empty so delete it
		return dir.delete();
	}
	
	public HandGesture getHandGesture() {
		return hg;
	}

}
