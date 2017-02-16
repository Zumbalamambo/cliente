package com.fi.uba.ar.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;

import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.R;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

// ideas taken from http://jmsliu.com/1954/android-save-downloading-file-locally.html 

public class FileUtils {

	private final static String TAG = "FileUtils";

	public static File getCacheFolder() {
		File cacheDir = null;
		if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
			cacheDir = new File(Environment.getExternalStorageDirectory(), "cachefolder");
			if (!cacheDir.isDirectory()) {
				cacheDir.mkdirs();
			}
		}
		if (!cacheDir.isDirectory()) {
			// get system cache folder
			cacheDir = MainApplication.getInstance().getAppContext().getCacheDir();
		}
		return cacheDir;
	}

	public static File getInternalStorageFolder() {
		Context ctx = MainApplication.getInstance().getAppContext();
		return ctx.getFilesDir();
	}

	private static boolean writeToFile(File file, byte[] data) {
		try {
			FileOutputStream os = new FileOutputStream(file);
			os.write(data, 0, data.length);
			os.close();
		} catch (IOException e) {
			e.printStackTrace();
			CustomLog.e(TAG, "writeToFile (" + file.getAbsolutePath() + ")failed\n" + e.getMessage());
			return false;
		}
		return true;
	}

	public static boolean writeFileToCache(String filename, byte[] data) {
		File outputFile = new File(getCacheFolder(), filename);
		return writeToFile(outputFile, data);
	}

	public static boolean writeFileToInternalStorage(String filename,
			byte[] data) {
		File outputFile = new File(getInternalStorageFolder(), filename);
		return writeToFile(outputFile, data);
	}

	private static byte[] readFile(File file) {
		try {
			InputStream fileInputStream = new FileInputStream(file);
			return IOUtils.toByteArray(fileInputStream);
		} catch (Exception e) {
			e.printStackTrace();
			CustomLog.e(TAG, "readFile(" + file.getAbsolutePath() + ") failed\n" + e.getMessage());
		}
		return null;
	}

	public static byte[] readFileFromCache(String filename) {
		File cacheFile = new File(getCacheFolder(), filename);
		return readFile(cacheFile);
	}

	public static byte[] readFileFromInternalStorage(String filename) {
		File internalFile = new File(getInternalStorageFolder(), filename);
		return readFile(internalFile);
	}

	// http://stackoverflow.com/questions/3382996/how-to-unzip-files-programmatically-in-android
	//XXX: esto podria ser una vulerabilidad? http://rotlogix.com/2015/11/12/zipinputstream-armageddon/
	public static File unpackZip(File zipFile) {
		CustomLog.d(TAG, "unzipping file = " + zipFile.getAbsolutePath());
		InputStream is;
		ZipInputStream zis;
		String zip_filename = FilenameUtils.getBaseName(zipFile.getAbsolutePath());
		String output_path = FilenameUtils.getPath(zipFile.getAbsolutePath());
		File output_dir = null;
		CustomLog.d(TAG, "zip_filename = " + zip_filename);
		CustomLog.d(TAG, "output_path = " + output_path);
		try {
			// we create a directory in the output path with the same name as
			// the zip file
			// we make sure it exists
			output_dir = new File(output_path + zip_filename);
			output_dir.mkdirs();
			CustomLog.d(TAG, "created dir = " + output_dir.getAbsolutePath());
			
			String filename;
			is = new FileInputStream(zipFile);
			zis = new ZipInputStream(new BufferedInputStream(is));
			ZipEntry ze;
			byte[] buffer = new byte[1024];
			int count;

			while ((ze = zis.getNextEntry()) != null) {

				filename = ze.getName();
				String ext = FilenameUtils.getExtension(filename);
				CustomLog.d(TAG, "ze = " + ze);
				CustomLog.d(TAG, "filename = " + filename);
				CustomLog.d(TAG, "extension = " + ext);
				CustomLog.d(TAG, "FilenameUtils.getPath = " + FilenameUtils.getPath(filename));				
				CustomLog.d(TAG, "ze.isDirectory() = " + ze.isDirectory());
				// Need to create directories if not exists, or
				// it will generate an Exception...
				if (ze.isDirectory()) {					
					File fmd = new File(output_dir, filename);
					if (!fmd.exists()) {
						fmd.mkdirs();
						CustomLog.d(TAG, "created dir = " + fmd.getAbsolutePath());
					}
					continue;
				} else //XXX: estoy probando para ver si cambiando la extension levanta bien el mtl porque vi excepciones al buscar el material
					if (ext.contentEquals("mtl")) {
						filename = filename.replace(".mtl", "_mtl");
					}
				
				// por algun motivo si el zip tiene un subdirectorio y todos sus archivos adentro
				// el ze.isDirectory no nos funciona y nos da un archivo directamente, con lo cual 
				// despues nos falto crear el subdirectorio principal. Con esto tratamos de asegurar que exista
				if (!FilenameUtils.getPath(filename).isEmpty()) {					
					File fmd = new File(output_dir, FilenameUtils.getPath(filename));
					if (!fmd.exists()) {
						fmd.mkdirs();
						CustomLog.d(TAG, "created dir = " + fmd.getAbsolutePath());
					}
				}
				
				File fz = new File(output_dir, filename);
				CustomLog.d(TAG, "creating FileOutputStream for file = " + fz.getAbsolutePath());
				FileOutputStream fout = new FileOutputStream(fz);

				while ((count = zis.read(buffer)) != -1) {
					fout.write(buffer, 0, count);
				}

				fout.close();
				zis.closeEntry();
			}

			zis.close();
		} catch (IOException e) {
			e.printStackTrace();
			CustomLog.e(TAG, "unpackZip(" + zipFile.getAbsolutePath() + ") failed\n" + e.getMessage());
		}

		return output_dir;
	}
}
