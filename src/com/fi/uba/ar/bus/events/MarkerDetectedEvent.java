package com.fi.uba.ar.bus.events;

import com.fi.uba.ar.model.Marker;

@Deprecated
public class MarkerDetectedEvent {
	
	public Marker marker;
	
	public MarkerDetectedEvent(Marker m) {
		marker = m;
	}
}
