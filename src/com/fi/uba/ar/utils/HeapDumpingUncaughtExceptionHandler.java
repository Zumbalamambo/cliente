package com.fi.uba.ar.utils;

import android.os.Debug;

import java.io.File;
import java.io.IOException;

//https://www.novoda.com/blog/debugging-memory-leaks-on-android-for-beginners-programmatic-heap-dumping-part-1/
public class HeapDumpingUncaughtExceptionHandler implements Thread.UncaughtExceptionHandler {
    private static final String HPROF_DUMP_BASENAME = "LeakingApp.dalvik-hprof";
    private final String dataDir;

    public HeapDumpingUncaughtExceptionHandler(String dataDir) {
        this.dataDir = dataDir;
    }

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        String absPath = new File(dataDir, HPROF_DUMP_BASENAME).getAbsolutePath();
        if(ex.getClass().equals(OutOfMemoryError.class)) {
            try {
                Debug.dumpHprofData(absPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ex.printStackTrace();
    }
}