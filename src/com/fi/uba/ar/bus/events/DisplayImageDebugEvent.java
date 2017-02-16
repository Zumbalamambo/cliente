package com.fi.uba.ar.bus.events;

import android.graphics.Bitmap;

public class DisplayImageDebugEvent {
	
	public Bitmap bitmap;
	
	public DisplayImageDebugEvent(Bitmap bm) {
		this.bitmap = bm;
	}

}
