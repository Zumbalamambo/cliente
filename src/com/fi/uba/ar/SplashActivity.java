package com.fi.uba.ar;

import com.fi.uba.ar.controllers.MainActivity;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;


public class SplashActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}