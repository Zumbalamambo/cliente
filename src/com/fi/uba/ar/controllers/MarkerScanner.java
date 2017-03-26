package com.fi.uba.ar.controllers;

import java.util.ArrayList;

import com.fi.uba.ar.utils.CustomLog;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

/*
 * Scanner de codigo QR para detectar la informacion de un marker
 */
public class MarkerScanner {

	private static ImageScanner scanner;

	static {
		scanner = new ImageScanner();
		scanner.setConfig(0, Config.X_DENSITY, 3);
		scanner.setConfig(0, Config.Y_DENSITY, 3);
	}

	public static ArrayList<String> scan(int width, int height, byte[] data) {
		CustomLog.d("MarkerScanner", "scan called. width = " + width + " - height = " + height + " - data = " + data);
		ArrayList<String> found_data = new ArrayList<String>();
//		Image barcode = new Image(width, height, "Y800");
		Image barcode = new Image(width, height, "GREY");
		
		barcode.setData(data);

		int result = scanner.scanImage(barcode);
		CustomLog.d("MarkerScanner", "result = " + result);

		if (result != 0) {
			SymbolSet syms = scanner.getResults();
			for (Symbol sym : syms) {
				String s = sym.getData();
				CustomLog.d("MarkerScanner", "data = " + s);
				found_data.add(s); 
			}
		}

		return found_data;
	}
}
