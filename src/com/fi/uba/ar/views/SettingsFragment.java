package com.fi.uba.ar.views;

import com.fi.uba.ar.R;
import com.fi.uba.ar.utils.CustomLog;

import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceFragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public class SettingsFragment extends PreferenceFragment {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
        
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, parent, savedInstanceState);
        // le ponemos un fondo de color fijo para que no quede
        // transparente y se vea la imagen de la camara de fondo
        view.setBackgroundColor(Color.WHITE);
        return view;
    }
    
}