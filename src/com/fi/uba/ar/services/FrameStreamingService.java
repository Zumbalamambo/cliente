package com.fi.uba.ar.services;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Binder;
import android.os.IBinder;

import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.model.Configuration;
import com.fi.uba.ar.utils.CommonUtils;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.MatUtils;

// Implementacion basada en el FrameService
// Este servicio se conecta a un server remoto y envia los frames que tenga en cola
// codigo de conexion y envio de datos basado en el ejemplo:
// https://mayaposch.wordpress.com/2013/07/26/binary-network-protocol-implementation-in-java-using-byte-arrays/

// Otra alternativa que pensaba a esto seria usar un VideoWriter y despues ver el video generado
// pero parece que esta clase no esta para java/android
// https://github.com/Itseez/opencv/issues/4666
// parece que en master agregaron el cambio https://github.com/Itseez/opencv/pull/5255
// y que si compilamos de ahi en lugar de bajar los paquetes armados, entonces podriamos usar el videowriter
// igual esto es todo muuuy reciente y es en opencv 3.0.0

// https://stackoverflow.com/questions/21446292/using-opencv-output-as-webcam
// en una de la respuestas sugieren enviar el frame.data a un server como hacemos aca, pero hace un reshape primero
public class FrameStreamingService extends Service {

	public class LocalBinder extends Binder {
		public FrameStreamingService getService() {            
            return FrameStreamingService.this;
        }
    }
	
	private final IBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(Intent intent) {
		CustomLog.d(TAG, "FrameStreamingService.LocalBinder.onBind llamado");
		return mBinder;
	}
	//---------------------------------------------
	
	private class FrameConsumerLoop implements Runnable {

		private boolean keepRunning = true;

		@Override
		public void run() {
			
			while (keepRunning) {
				try {
					//Thread.sleep(100);
										
					if (!connected) {
						// intentamos conectar y seguimos
						connect();
						continue;
					}
					
					// si opencv todavia no fue cargado y recibimos un frame para procesar
			    	// simplemente no hacemos nada porque la app no va a funcionar
					//CustomLog.d(TAG, "MainActivity =  " + MainApplication.getInstance().getMainActivity());
			    	if (! MainApplication.getInstance().getMainActivity().isOpenCVLoaded())
			    		continue;
			    	
			    	// enviamos todos los frames que tengamos hasta el momento
			        // hago un drain para sacar todo a modo de prueba para ver si es que el poll estaba bloqueando 
			    	ArrayList<Mat> matList = new ArrayList<Mat>();
			    	framesDeque.drainTo(matList);
			    	int cantFrames = matList.size();
			    	CustomLog.d(TAG, "Hay " + cantFrames + " frames en cola para enviar. Enviando.....");
			    	
			    	for (Mat m: matList) {
			    		if (m != null) {
			    			// Fuerzo a que la convierta en GRAY
							sendFrame(m, true);
							Thread.sleep(50);
						}
			    	}
			    	
			    	/*
			    	for (int c = 0; c < cantFrames; c++) {
						frame = framesDeque.poll(); // sera que el poll las frena?
						if (frame != null) {
							sendFrame(frame);
							//sendTestFrame();
						}
					}
					*/
			    	
				} catch (InterruptedException e) {
					CustomLog.e(TAG, "FrameConsumerLoop capturo una InterruptedException\n" + e.toString());
					keepRunning = false;
				}
			}
		}

		public void stop() {
			keepRunning = false;
		}

	}
	
	
	//---------------------------------------------
	// A partir de aca estan las cosas de la clase
	protected static final String TAG = "FrameStreamingService";	
	
	// para mantener una referencia al thread que ejecuta el loop principal
    private FrameConsumerLoop mainLoop ;
    private Thread mainLoopThread;
    
    private LinkedBlockingDeque<Mat> framesDeque;
    
    Socket mSocket = null;
    
    String mServer = "127.0.0.1"; //TODO: deberia ser tomado dinamicamente de la configuracion
    int mServerPort = 5555;
    private boolean connected = false;
    
    final int maxConnectionAttempts = 10; //TODO: deberia ser configurable
    
    int nConnectionAttempts = 0;
    
    OutputStream mOutputStream;
    InputStream mInputStream;
    BufferedOutputStream mBufferedOutputStream;
    
    @Override
    public void onCreate() {
    	framesDeque = new LinkedBlockingDeque<Mat>();
    	
    	connect();
    	
    	mainLoop = new FrameConsumerLoop();    	
    	mainLoopThread = new Thread(mainLoop);
    	mainLoopThread.start();
    	
    	CustomLog.d(TAG, "FrameStreamingService creado - Socket connected: " + connected);
    	
    }
    
    @Override
    public void onDestroy () {
    	mainLoop.stop();
    	try {
			mainLoopThread.join();
		} catch (InterruptedException e) {
			CustomLog.e(TAG, "error esperando a mainLoopThread a que termine\n" + e.toString());
		}
    }
    
    private void connect() {
    	//XXX: por ahora asumimos que el server esta en el mismo lugar que la webapp pero puerto fijo en 5555
    	String url = MainApplication.getInstance().getConfigManager().getValue(Configuration.SERVER_URL);
    	    	
    	CustomLog.d(TAG, "Seteando conexion... Server: " + mServer + ":" + mServerPort);
    	
    	nConnectionAttempts++;
    	
    	try {
    		mServer = new URL(url).getHost();
            mSocket = new Socket(mServer, mServerPort);
            mOutputStream = this.mSocket.getOutputStream();
            mBufferedOutputStream = new BufferedOutputStream(mOutputStream);
            mInputStream = this.mSocket.getInputStream();
            connected = true;
        } catch (Exception ee) {
            CustomLog.e(TAG, "Error al setear conexion. Server: " + mServer + ":" + mServerPort + "\n" + ee.toString());
            if (nConnectionAttempts >= maxConnectionAttempts) {
            	mainLoop.stop();
            	CustomLog.e(TAG, "Se supero la cantidad maxima de intentos de conexion a Server: " + mServer + ":" + mServerPort + "\nParando loop ...");
            }
        }
    }
    
    public void enqueueFrame(Mat m) {
    	try {
    		if (connected && (m.size().area() > 0)) {
    			framesDeque.add(m);
    		} 
    	} catch (Exception ex) {
    		CustomLog.e(TAG, "Error adding frame to framesDeqeue");
    	}
    }
    
	private void sendFrame(Mat frame, boolean convertToGrey) {	
				
		try {
			if (convertToGrey)
				Imgproc.cvtColor(frame, frame, Imgproc.COLOR_BGR2GRAY);
		} catch(Exception e){}
		
		// tomado de https://stackoverflow.com/questions/21446292/using-opencv-output-as-webcam
		// a ver si mejora algo
		//frame = (frame.reshape(0,1)); // to make it continuous
		//XXX: usar el reshape crasheo el server...
		
		//CustomLog.d(TAG, "mat = " + frame);
		
		int mat_width = frame.width();
		int mat_height = frame.height();
		
		byte[] data = MatUtils.matToBytes(frame);
		int dataLength = data.length;
		
		//CustomLog.d(TAG, "antes de comprimir - data length = " + dataLength);
		
		if (dataLength == 0) {
			//CustomLog.d(TAG, "bailing...");
			return;
		}
		
		data = CommonUtils.compressBytes(data);
		
		dataLength = data.length;
		
		//CustomLog.d(TAG, "enviando frame al server - comprimido - data length = " + dataLength);
		
		if ((mat_width == 0) || (mat_height == 0))
			CustomLog.w(TAG, "ES RARO!!! MAT HEIGHT O WIDTH EN CERO!!!!");
		
		if (dataLength > 0) {
			//byte[] packetCode = intToByteArray(1);
			//byte[] msgSize = intToByteArray(dataLength);
			
			byte[] packetCode = ByteBuffer.allocate(4).putInt(1).array();			
			byte[] frameType = ByteBuffer.allocate(4).putInt(frame.type()).array();
			byte[] frameHeight = ByteBuffer.allocate(4).putInt(mat_height).array();
			byte[] frameWidth = ByteBuffer.allocate(4).putInt(mat_width).array();
			byte[] frameDataLength = ByteBuffer.allocate(4).putInt(dataLength).array();
			
			try {
				mBufferedOutputStream.write(packetCode);
				mBufferedOutputStream.write(frameType);
				mBufferedOutputStream.write(frameHeight);
				mBufferedOutputStream.write(frameWidth);
				mBufferedOutputStream.write(frameDataLength);
				mBufferedOutputStream.write(data);
				mBufferedOutputStream.flush();
				//CustomLog.d(TAG, "frame enviado...");
			} catch (IOException e1) {
				CustomLog.e(TAG, "Error enviando data al server\n" + e1.toString());
			}
		}
	}
	
	public void sendFrame(Bitmap frame) {	
		//CustomLog.d(TAG, "bitmap = " + frame);		
		
		byte[] data = MatUtils.bitmapToByteArray(frame);
		int dataLength = data.length;
		
		//CustomLog.d(TAG, "antes de comprimir - data length = " + dataLength);
		
		if (dataLength == 0) {
			//CustomLog.d(TAG, "bailing...");
			return;
		}
		
		data = CommonUtils.compressBytes(data);
		
		dataLength = data.length;
		
		//CustomLog.d(TAG, "enviando frame al server - comprimido - data length = " + dataLength);
				
		if (dataLength > 0) {
			//byte[] packetCode = intToByteArray(1);
			//byte[] msgSize = intToByteArray(dataLength);
			
			byte[] packetCode = ByteBuffer.allocate(4).putInt(2).array();			
			byte[] frameDataLength = ByteBuffer.allocate(4).putInt(dataLength).array();
			
			try {
				mBufferedOutputStream.write(packetCode);
				mBufferedOutputStream.write(frameDataLength);
				mBufferedOutputStream.write(data);
				mBufferedOutputStream.flush();
				//CustomLog.d(TAG, "frame enviado...");
			} catch (IOException e1) {
				CustomLog.e(TAG, "Error enviando data al server\n" + e1.toString());
			}
		}
	}

	private void sendTestFrame() {		
		byte[] data = new byte[100];
			
		int dataLength = data.length;
		
		for (int x = 0; x < dataLength; x++)
			data[x] = 'A';
		
		CustomLog.d(TAG, "enviando TEST frame al server - data length = " + dataLength);
		
		if (dataLength > 0) {
			//byte[] packetCode = intToByteArray(1);
			//byte[] msgSize = intToByteArray(dataLength);
			
			byte[] packetCode = ByteBuffer.allocate(4).putInt(99).array();
			byte[] msgSize = ByteBuffer.allocate(4).putInt(dataLength).array();

			try {
				mBufferedOutputStream.write(packetCode);
				mBufferedOutputStream.write(msgSize);
				mBufferedOutputStream.write(data);
				mBufferedOutputStream.flush();
			} catch (IOException e1) {
				CustomLog.e(TAG, "Error enviando data al server\n" + e1.toString());
			}
		}
	}
	
    private void clearQueue() {    	
    	while (framesDeque.peek() != null) {
    		Mat frame = framesDeque.poll();
    		frame.release();
    	}    	
    }
    
    // Writes provided 4-byte integer to a 4 element byte array in Little-Endian order.
    public static final byte[] intToByteArray(int value) {
        return new byte[] {
            (byte)(value & 0xff),
            (byte)(value >> 8 & 0xff),
            (byte)(value >> 16 & 0xff),
            (byte)(value >>> 24)
        };
    }
}
