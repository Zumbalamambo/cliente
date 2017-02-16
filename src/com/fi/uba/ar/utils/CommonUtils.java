package com.fi.uba.ar.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class CommonUtils {
	
	private static final String LOG_TAG = new Object(){}.getClass().getEnclosingClass().getName();
	
	final private static char[] hexArray = "0123456789ABCDEF".toCharArray();
	
	public static String bytesToHex(byte[] bytes) {
	    char[] hexChars = new char[bytes.length * 2];
	    for ( int j = 0; j < bytes.length; j++ ) {
	        int v = bytes[j] & 0xFF;
	        hexChars[j * 2] = hexArray[v >>> 4];
	        hexChars[j * 2 + 1] = hexArray[v & 0x0F];
	    }
	    return new String(hexChars);
	}
	
	public static void copyFloatToDoubleArray(float[] src, double[] dst)
	{
		for(int mI = 0; mI < 16; mI++)
		{
			dst[mI] = src[mI];
		}
	}
	
	public static void copyDoubleToFloatArray(double[] src, float[] dst)
	{
		for(int mI = 0; mI < 16; mI++)
		{
			dst[mI] = (float) src[mI]; //XXX: esto perdera precision?
		}
	}
	
	// tomado de http://www.java2s.com/Code/Java/File-Input-Output/Compressbytearray.htm
	public static byte[] compressBytes(byte[] b) {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			GZIPOutputStream zos = new GZIPOutputStream(baos);
			zos.write(b);
			zos.close();
			return baos.toByteArray();
		} catch (Exception e) {
			CustomLog.e(LOG_TAG, "Error al comprimir bytes\n" + e);
			return new byte[0];
		}
	}

	public static byte[] uncompressBytes(byte[] b) throws IOException {
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ByteArrayInputStream bais = new ByteArrayInputStream(b);
			GZIPInputStream zis = new GZIPInputStream(bais);
			byte[] tmpBuffer = new byte[256];
			int n;
			while ((n = zis.read(tmpBuffer)) >= 0)
				baos.write(tmpBuffer, 0, n);
			zis.close();
			return baos.toByteArray();
		} catch (Exception e) {
			CustomLog.e(LOG_TAG, "Error al comprimir bytes\n" + e);
			return new byte[0];
		}
	}

}
