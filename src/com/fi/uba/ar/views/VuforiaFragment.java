package com.fi.uba.ar.views;

import java.util.ArrayList;
import org.opencv.core.Mat;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Switch;
import android.widget.Toast;
import com.qualcomm.vuforia.CameraDevice;
import com.qualcomm.vuforia.DataSet;
import com.qualcomm.vuforia.Frame;
import com.qualcomm.vuforia.ImageTargetBuilder;
import com.qualcomm.vuforia.ImageTracker;
import com.qualcomm.vuforia.Renderer;
import com.qualcomm.vuforia.State;
import com.qualcomm.vuforia.Trackable;
import com.qualcomm.vuforia.TrackableSource;
import com.qualcomm.vuforia.Tracker;
import com.qualcomm.vuforia.TrackerManager;
import com.qualcomm.vuforia.Vuforia;
import com.fi.uba.ar.MainApplication;
import com.fi.uba.ar.R;
import com.fi.uba.ar.controllers.VuforiaSessionControl;
import com.fi.uba.ar.controllers.VuforiaSession;
import com.fi.uba.ar.controllers.VuforiaRenderer;
import com.fi.uba.ar.exceptions.VuforiaException;
import com.fi.uba.ar.utils.CustomLog;
import com.fi.uba.ar.utils.MatUtils;

public class VuforiaFragment extends Fragment implements VuforiaSessionControl, OnClickListener
{
    private static final String LOGTAG = "VuforiaFragment";
    
    private VuforiaSession vuforiaAppSession;
    
    private VuforiaGLView mGlView;

    private VuforiaRenderer mRenderer;
//    
//    // The textures we will use for rendering:
//    private Vector<Texture> mTextures;
    
    // View overlays to be displayed in the Augmented View
    private ViewGroup mUILayout;
    private View mBottomBar;
    private View mCameraButton;
    
    // Alert dialog for displaying SDK errors
    private AlertDialog mDialog;
    
    int targetBuilderCounter = 1;
    
    DataSet dataSetUserDef = null;
    
    //private SampleAppMenu mSampleAppMenu;
    private ArrayList<View> mSettingsAdditionalViews;
    
    private boolean mFlash = false;
    private boolean mContAutofocus = false;
    private boolean mExtendedTracking = false;
    
    private View mFlashOptionView;
    
    //private LoadingDialogHandler loadingDialogHandler = new LoadingDialogHandler(this);
    
    boolean mIsDroidDevice = false;

	// The latest trackable source to be extracted from the Target Builder
    TrackableSource trackableSource;
    
    private boolean trackingStarted = false;
    
    private boolean buttonClicked = false;
    
    
    // Called when the activity first starts or needs to be recreated after
    // resuming the application or a configuration change.
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        //Log.d(LOGTAG, "onCreate");
        super.onCreate(savedInstanceState);
        
        vuforiaAppSession = new VuforiaSession(this);
        
        //XXX: que orientacion nos conviene para la camara??        
        vuforiaAppSession.initAR(getActivity(), ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
          
        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith("droid");
    }
    
  
    
    // Called when the activity will start interacting with the user.
    @Override
    public void onResume()
    {
        Log.d(LOGTAG, "onResume");
        super.onResume();
        
        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice)
        {
            //getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            getActivity().setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }
        
        try
        {
            vuforiaAppSession.resumeAR();
        } catch (VuforiaException e)
        {
            Log.e(LOGTAG, Log.getStackTraceString(e));
        }
        
        // Resume the GL view:
        if (mGlView != null)
        {
            mGlView.setVisibility(View.VISIBLE);
            mGlView.onResume();
        }
    }
    
    
    // Called when the system is about to start resuming a previous activity.
    @Override
    public void onPause()
    {
//        Log.d(LOGTAG, "onPause");
        super.onPause();
        
        if (mGlView != null)
        {
            mGlView.setVisibility(View.INVISIBLE);
            mGlView.onPause();
        }
        
        // Turn off the flash
        if (mFlashOptionView != null && mFlash)
        {
            // OnCheckedChangeListener is called upon changing the checked state
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
            {
                ((Switch) mFlashOptionView).setChecked(false);
            } else
            {
                ((CheckBox) mFlashOptionView).setChecked(false);
            }
        }
        
        try
        {
            vuforiaAppSession.pauseAR();
        } catch (VuforiaException e)
        {
            Log.e(LOGTAG, Log.getStackTraceString(e));
        }
    }
    
    
    // The final call you receive before your activity is destroyed.
    @Override
    public void onDestroy()
    {
//        Log.d(LOGTAG, "onDestroy");
        super.onDestroy();
        
        try
        {
            vuforiaAppSession.stopAR();
        } catch (VuforiaException e)
        {
            Log.e(LOGTAG, e.getString());
        }
        
//        // Unload texture:
//        mTextures.clear();
//        mTextures = null;
//        
        System.gc();
    }
    
    
    // Callback for configuration changes the activity handles itself
    @Override
    public void onConfigurationChanged(Configuration config)
    {
//        Log.d(LOGTAG, "onConfigurationChanged");
        super.onConfigurationChanged(config);
        
        vuforiaAppSession.onConfigurationChanged();
        
        // Removes the current layout and inflates a proper layout
        // for the new screen orientation
        
        if (mUILayout != null)
        {
            mUILayout.removeAllViews();
            ((ViewGroup) mUILayout.getParent()).removeView(mUILayout);
            
        }
        
//        addOverlayView(false);
    }
    
    
    // Shows error message in a system dialog box
    private void showErrorDialog()
    {
        if (mDialog != null && mDialog.isShowing())
            mDialog.dismiss();
        
        mDialog = new AlertDialog.Builder(getActivity()).create();
        DialogInterface.OnClickListener clickListener = new DialogInterface.OnClickListener()
        {
            public void onClick(DialogInterface dialog, int which)
            {
                dialog.dismiss();
            }
        };
        
        mDialog.setButton(DialogInterface.BUTTON_POSITIVE,
            getString(R.string.button_OK), clickListener);
        
        mDialog.setTitle(getString(R.string.target_quality_error_title));
        
        String message = getString(R.string.target_quality_error_desc);
        
        // Show dialog box with error message:
        mDialog.setMessage(message);
        mDialog.show();
    }
    
    
    // Shows error message in a system dialog box on the UI thread
    void showErrorDialogInUIThread()
    {
    	getActivity().runOnUiThread(new Runnable()
        {
            public void run()
            {
                showErrorDialog();
            }
        });
    }
    
    
    // Initializes AR application components.
    private void initApplicationAR()
    {
        // Do application initialization
//        refFreeFrame = new RefFreeFrame((UserDefinedTargets) getActivity(), vuforiaAppSession);
//        refFreeFrame.init();
        
        // Create OpenGL ES view:
        int depthSize = 16;
        int stencilSize = 0;
        boolean translucent = Vuforia.requiresAlpha();
        
        mGlView = new VuforiaGLView(getActivity());
        mGlView.init(translucent, depthSize, stencilSize);
        
        mRenderer = new VuforiaRenderer(this, vuforiaAppSession);

        mGlView.setRenderer(mRenderer);
        
        mRenderer.mIsActive = true;
        
        mUILayout.addView(mGlView);

        startUserDefinedTargets();
//        initializeBuildTargetModeViews();
        
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    	
    	 mUILayout = (ViewGroup)inflater.inflate(R.layout.vuforia_view_fragment, container, false);
    	 //mUILayout = (RelativeLayout) inflater.inflate(R.layout.camera_overlay_udt, container, false);    	
        
         mUILayout.setVisibility(View.VISIBLE);
        
        // If this is the first time that the application runs then the
        // uiLayout background is set to BLACK color, will be set to
        // transparent once the SDK is initialized and camera ready to draw
//        if (initLayout)
//        {
//            mUILayout.setBackgroundColor(Color.BLACK);
//        }
        
        // Adds the inflated layout to the view
//        getActivity().addContentView(mUILayout, new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
        
//        // Gets a reference to the bottom navigation bar
//        mBottomBar = mUILayout.findViewById(R.id.bottom_bar);
//        
//        // Gets a reference to the Camera button
//        mCameraButton = mUILayout.findViewById(R.id.camera_button);
//        mCameraButton.setOnClickListener(this);
//        
//        // Gets a reference to the loading dialog container
//        //loadingDialogHandler.mLoadingDialogContainer = mUILayout.findViewById(R.id.loading_layout);
//        
//        mUILayout.bringToFront();
        
       

        return mUILayout;
    }
    
    //XXX: creo que esto ya no se usa mas y toda la logica esta en renderFrame
    public boolean detectMarker() {
    	boolean found = false;
    	
    	// Para obtener una imagen de los frames de la camara de vuforia necesitamos el objeto state
    	State state = Renderer.getInstance().begin();    	
    	
        Frame frame = state.getFrame();
        Mat m = MatUtils.vuforiaFrameToMat(frame, true); // necesitamos la imagen en gris
        if (m != null)
        	found = MainApplication.getInstance().getMainController().detectMarkers(m);        	

        Renderer.getInstance().end();
		CustomLog.d(LOGTAG, "detectMarker returning = " + found);
    	return found;
    }
     
    public void scanForMarker()
    {
    	mRenderer.doMarkerScanOnNextFrame();
    }
    
    public void doColorSampling()
    {
    	mRenderer.doColorSamplingOnNextFrame();
    }
    
    public void startTrackerMarker() {
    	
        if (isUserDefinedTargetsRunning())
        {
            // Shows the loading dialog
            //loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG);
            trackingStarted = false;
            // Builds the new target
            startBuild();
            buttonClicked = true;
            // es probable que no se cree instantaneamente asi que este metodo de checkeo lo seguimos llamando en un renderFrame
            checkForNewTrackable();
            
            //TODO: tendriamos que deshabilitar el boton para no permitir detectar otro codigo QR 
            // hasta que se "cierre" lo que se esta trackeando ahora
            
        }
    }
    
    public void checkForNewTrackable() {
    	//CustomLog.d(LOGTAG, "checkForNewTrackable - buttonClicked = " + buttonClicked);
    	// verificamos si hay de hecho un nuevo target creado
        TrackerManager trackerManager = TrackerManager.getInstance();
        ImageTracker imageTracker = (ImageTracker) (trackerManager.getTracker(ImageTracker.getClassType()));            
        ImageTargetBuilder targetBuilder = imageTracker.getImageTargetBuilder();
        TrackableSource newTrackableSource = targetBuilder.getTrackableSource();
        if (newTrackableSource != null && buttonClicked) {
        	this.trackableSource = newTrackableSource;
            Log.d(LOGTAG, "Built target, reactivating dataset with new target");
            this.doStartTrackers();
            buttonClicked = false;
        }
    	
    }
    
    // Creates a texture given the filename
//    Texture createTexture(String nName)
//    {
//        return Texture.loadTextureFromApk(nName, getAssets());
//    }

    
    // Callback function called when the target creation finished
    void targetCreated()
    {
        // Hides the loading dialog
//        loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
        
//        if (refFreeFrame != null)
//        {
//            refFreeFrame.reset();
//        }
        
    }
    
    
    // Initialize views
    private void initializeBuildTargetModeViews()
    {
        // Shows the bottom bar
        mBottomBar.setVisibility(View.VISIBLE);
        mCameraButton.setVisibility(View.VISIBLE);
    }
    
    
//    @Override
//    public boolean onTouchEvent(MotionEvent event)
//    {
//        // Process the Gestures
//        if (mSampleAppMenu != null && mSampleAppMenu.processEvent(event))
//            return true;
//        
//        return mGestureDetector.onTouchEvent(event);
//    }
    
    
    boolean startUserDefinedTargets()
    {
        Log.d(LOGTAG, "startUserDefinedTargets");
        
        TrackerManager trackerManager = TrackerManager.getInstance();
        ImageTracker imageTracker = (ImageTracker) (trackerManager.getTracker(ImageTracker.getClassType()));
        if (imageTracker != null)
        {
            ImageTargetBuilder targetBuilder = imageTracker.getImageTargetBuilder();
            
            if (targetBuilder != null)
            {
                // if needed, stop the target builder
                if (targetBuilder.getFrameQuality() != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE)
                    targetBuilder.stopScan();
                
                imageTracker.stop();
                
                targetBuilder.startScan();
                
            }
        } else
            return false;
        
        return true;
    }
    
    
    boolean isUserDefinedTargetsRunning()
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ImageTracker imageTracker = (ImageTracker) trackerManager.getTracker(ImageTracker.getClassType());
        
        if (imageTracker != null)
        {
            ImageTargetBuilder targetBuilder = imageTracker.getImageTargetBuilder();
            if (targetBuilder != null)
            {
                Log.e(LOGTAG, "Quality> " + targetBuilder.getFrameQuality());
                return (targetBuilder.getFrameQuality() != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE) ? true
                    : false;
            }
        }
        
        return false;
    }
    
    
    void startBuild()
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ImageTracker imageTracker = (ImageTracker) trackerManager.getTracker(ImageTracker.getClassType());
        
        if (imageTracker != null)
        {
            ImageTargetBuilder targetBuilder = imageTracker.getImageTargetBuilder();
            if (targetBuilder != null)
            {
                // Uncomment this block to show and error message if
                // the frame quality is Low
                //if (targetBuilder.getFrameQuality() == ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_LOW)
                //{
                //     showErrorDialogInUIThread();
                //}
                
                String name;
                do
                {
                    name = "UserTarget-" + targetBuilderCounter;
                    Log.d(LOGTAG, "TRYING " + name);
                    targetBuilderCounter++;
                } while (!targetBuilder.build(name, 320.0f));
                
//                refFreeFrame.setCreating();
            }
        }
    }
    
    
    void updateRendering()
    {
        DisplayMetrics metrics = new DisplayMetrics();
        getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
//        refFreeFrame.initGL(metrics.widthPixels, metrics.heightPixels);
    }
    
    
    @Override
    public boolean doInitTrackers()
    {
        // Indicate if the trackers were initialized correctly
        boolean result = true;
        
        // Initialize the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        Tracker tracker = trackerManager.initTracker(ImageTracker.getClassType());
        if (tracker == null)
        {
            Log.d(LOGTAG, "Failed to initialize ImageTracker.");
            result = false;
        } else
        {
            Log.d(LOGTAG, "Successfully initialized ImageTracker.");
        }
        
        return result;
    }
    
    
    @Override
    public boolean doLoadTrackersData()
    {
        // Get the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ImageTracker imageTracker = (ImageTracker) trackerManager.getTracker(ImageTracker.getClassType());
        if (imageTracker == null)
        {
            Log.d(
                LOGTAG,
                "Failed to load tracking data set because the ImageTracker has not been initialized.");
            return false;
        }
        
        // Create the data set:
        dataSetUserDef = imageTracker.createDataSet();
        if (dataSetUserDef == null)
        {
            Log.d(LOGTAG, "Failed to create a new tracking data.");
            return false;
        }
        
        if (!imageTracker.activateDataSet(dataSetUserDef))
        {
            Log.d(LOGTAG, "Failed to activate data set.");
            return false;
        }
        
        Log.d(LOGTAG, "Successfully loaded and activated data set.");
        return true;
    }
    
    
    @Override
    public boolean doStartTrackers()
    {
        // Indicate if the trackers were started correctly
        boolean result = true;
        
        Tracker imageTracker = TrackerManager.getInstance().getTracker(ImageTracker.getClassType());
        if (imageTracker != null)
            imageTracker.start();
        
        return result;
    }
    
    
    @Override
    public boolean doStopTrackers()
    {
        // Indicate if the trackers were stopped correctly
        boolean result = true;
        
        Tracker imageTracker = TrackerManager.getInstance().getTracker(ImageTracker.getClassType());
        if (imageTracker != null)
            imageTracker.stop();
        
        return result;
    }
    
    
    @Override
    public boolean doUnloadTrackersData()
    {
        // Indicate if the trackers were unloaded correctly
        boolean result = true;
        
        // Get the image tracker:
        TrackerManager trackerManager = TrackerManager.getInstance();
        ImageTracker imageTracker = (ImageTracker) trackerManager.getTracker(ImageTracker.getClassType());
        if (imageTracker == null)
        {
            result = false;
            Log.d(
                LOGTAG,
                "Failed to destroy the tracking data set because the ImageTracker has not been initialized.");
        }
        
        if (dataSetUserDef != null)
        {
            if (imageTracker.getActiveDataSet() != null
                && !imageTracker.deactivateDataSet(dataSetUserDef))
            {
                Log.d(
                    LOGTAG,
                    "Failed to destroy the tracking data set because the data set could not be deactivated.");
                result = false;
            }
            
            if (!imageTracker.destroyDataSet(dataSetUserDef))
            {
                Log.d(LOGTAG, "Failed to destroy the tracking data set.");
                result = false;
            }
            
            Log.d(LOGTAG, "Successfully destroyed the data set.");
            dataSetUserDef = null;
        }
        
        return result;
    }
    
    
    @Override
    public boolean doDeinitTrackers()
    {
        // Indicate if the trackers were deinitialized correctly
        boolean result = true;
        
//        if (refFreeFrame != null)
//            refFreeFrame.deInit();
        
        TrackerManager tManager = TrackerManager.getInstance();
        tManager.deinitTracker(ImageTracker.getClassType());
        
        return result;
    }
    
    
    @Override
    public void onInitARDone(VuforiaException exception)
    {
        
        if (exception == null)
        {
            initApplicationAR();
            
            // Activate the renderer
//            mRenderer.mIsActive = true;
            
            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
//            addContentView(mGlView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            
            // Sets the UILayout to be drawn in front of the camera
            mUILayout.bringToFront();
            
            // Hides the Loading Dialog
//            loadingDialogHandler.sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);
            
            // Sets the layout background to transparent
            mUILayout.setBackgroundColor(Color.TRANSPARENT);
            
            try
            {
                vuforiaAppSession.startAR(CameraDevice.CAMERA.CAMERA_DEFAULT);
            } catch (VuforiaException e)
            {
                Log.e(LOGTAG, "startAR Exception! - " + e.getString());
            }
                      
            boolean result = CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);
            
            if (result)
                mContAutofocus = true;
            else
                Log.e(LOGTAG, "Unable to enable continuous autofocus");
                        
            setSampleAppMenuAdditionalViews();
//            mSampleAppMenu = new SampleAppMenu(this, this,
//                "User Defined Targets", mGlView, mUILayout,
//                mSettingsAdditionalViews);
//            setSampleAppMenuSettings();
            
        } else
        {
            Log.e(LOGTAG, exception.getString());
            getActivity().finish();
        }
    }
    
    
    @Override
    public void onQCARUpdate(State state)
    {
        TrackerManager trackerManager = TrackerManager.getInstance();
        ImageTracker imageTracker = (ImageTracker) trackerManager.getTracker(ImageTracker.getClassType());
        
//        if (refFreeFrame.hasNewTrackableSource())
        if (this.trackableSource != null)
        {
            Log.d(LOGTAG, "Attempting to transfer the trackable source to the dataset");
            
            // Deactivate current dataset
            imageTracker.deactivateDataSet(imageTracker.getActiveDataSet());
            
            // Clear the oldest target if the dataset is full or the dataset
            // already contains five user-defined targets.
            if (dataSetUserDef.hasReachedTrackableLimit()
                || dataSetUserDef.getNumTrackables() >= 5)
                dataSetUserDef.destroy(dataSetUserDef.getTrackable(0));
            
            if (mExtendedTracking && dataSetUserDef.getNumTrackables() > 0)
            {
                // We need to stop the extended tracking for the previous target
                // so we can enable it for the new one
                int previousCreatedTrackableIndex = 
                    dataSetUserDef.getNumTrackables() - 1;
                
                dataSetUserDef.getTrackable(previousCreatedTrackableIndex).stopExtendedTracking();
            }
            
            // Add new trackable source
//            Trackable trackable = dataSetUserDef.createTrackable(refFreeFrame.getNewTrackableSource());
            Trackable trackable = dataSetUserDef.createTrackable(this.trackableSource);
            this.trackableSource = null;
            
            // Reactivate current dataset
            imageTracker.activateDataSet(dataSetUserDef);
            
            if (mExtendedTracking)
            {
                trackable.startExtendedTracking();
            }
            
            trackingStarted = true;
        }
    }
    
    final public static int CMD_BACK = -1;
    final public static int CMD_EXTENDED_TRACKING = 1;
    final public static int CMD_AUTOFOCUS = 2;
    final public static int CMD_FLASH = 3;
    final public static int CMD_CAMERA_FRONT = 4;
    final public static int CMD_CAMERA_REAR = 5;
    
    
    // This method sets the additional views to be moved along with the GLView
    private void setSampleAppMenuAdditionalViews()
    {
        mSettingsAdditionalViews = new ArrayList<View>();
        mSettingsAdditionalViews.add(mBottomBar);
    }
    
    
    // This method sets the menu's settings
//    private void setSampleAppMenuSettings()
//    {
//        SampleAppMenuGroup group;
//        
//        group = mSampleAppMenu.addGroup("", false);
//        group.addTextItem(getString(R.string.menu_back), -1);
//        
//        group = mSampleAppMenu.addGroup("", true);
//        group.addSelectionItem(getString(R.string.menu_extended_tracking),
//            CMD_EXTENDED_TRACKING, false);
//        group.addSelectionItem(getString(R.string.menu_contAutofocus),
//            CMD_AUTOFOCUS, mContAutofocus);
//        mFlashOptionView = group.addSelectionItem(
//            getString(R.string.menu_flash), CMD_FLASH, false);
//        
//        CameraInfo ci = new CameraInfo();
//        boolean deviceHasFrontCamera = false;
//        boolean deviceHasBackCamera = false;
//        for (int i = 0; i < Camera.getNumberOfCameras(); i++)
//        {
//            Camera.getCameraInfo(i, ci);
//            if (ci.facing == CameraInfo.CAMERA_FACING_FRONT)
//                deviceHasFrontCamera = true;
//            else if (ci.facing == CameraInfo.CAMERA_FACING_BACK)
//                deviceHasBackCamera = true;
//        }
//        
//        if (deviceHasBackCamera && deviceHasFrontCamera)
//        {
//            group = mSampleAppMenu.addGroup(getString(R.string.menu_camera),
//                true);
//            group.addRadioItem(getString(R.string.menu_camera_front),
//                CMD_CAMERA_FRONT, false);
//            group.addRadioItem(getString(R.string.menu_camera_back),
//                CMD_CAMERA_REAR, true);
//        }
//        
//        mSampleAppMenu.attachMenu();
//    }
    
    
//    @Override
    public boolean menuProcess(int command)
    {
        boolean result = true;
        
        switch (command)
        {
            case CMD_BACK:
            	getActivity().finish();
                break;
            
            case CMD_FLASH:
                result = CameraDevice.getInstance().setFlashTorchMode(!mFlash);
                
                if (result)
                {
                    mFlash = !mFlash;
                } else
                {
                    showToast(getString(mFlash ? R.string.menu_flash_error_off
                        : R.string.menu_flash_error_on));
                    Log.e(LOGTAG,
                        getString(mFlash ? R.string.menu_flash_error_off
                            : R.string.menu_flash_error_on));
                }
                break;
            
            case CMD_AUTOFOCUS:
                
                if (mContAutofocus)
                {
                    result = CameraDevice.getInstance().setFocusMode(
                        CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL);
                    
                    if (result)
                    {
                        mContAutofocus = false;
                    } else
                    {
                        showToast(getString(R.string.menu_contAutofocus_error_off));
                        Log.e(LOGTAG,
                            getString(R.string.menu_contAutofocus_error_off));
                    }
                } else
                {
                    result = CameraDevice.getInstance().setFocusMode(
                        CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO);
                    
                    if (result)
                    {
                        mContAutofocus = true;
                    } else
                    {
                        showToast(getString(R.string.menu_contAutofocus_error_on));
                        Log.e(LOGTAG,
                            getString(R.string.menu_contAutofocus_error_on));
                    }
                }
                
                break;
            
            case CMD_EXTENDED_TRACKING:
                if (dataSetUserDef.getNumTrackables() > 0)
                {
                    int lastTrackableCreatedIndex = 
                        dataSetUserDef.getNumTrackables() - 1;
                    
                    Trackable trackable = dataSetUserDef
                        .getTrackable(lastTrackableCreatedIndex);
                    
                    if (!mExtendedTracking)
                    {
                        if (!trackable.startExtendedTracking())
                        {
                            Log.e(LOGTAG,
                                "Failed to start extended tracking target");
                            result = false;
                        } else
                        {
                            Log.d(LOGTAG,
                                "Successfully started extended tracking target");
                        }
                    } else
                    {
                        if (!trackable.stopExtendedTracking())
                        {
                            Log.e(LOGTAG,
                                "Failed to stop extended tracking target");
                            result = false;
                        } else
                        {
                            Log.d(LOGTAG,
                                "Successfully stopped extended tracking target");
                        }
                    }
                }
                
                if (result)
                    mExtendedTracking = !mExtendedTracking;
                
                break;
            
            case CMD_CAMERA_FRONT:
            case CMD_CAMERA_REAR:
                
                // Turn off the flash
                if (mFlashOptionView != null && mFlash)
                {
                    // OnCheckedChangeListener is called upon changing the
                    // checked state
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                    {
                        ((Switch) mFlashOptionView).setChecked(false);
                    } else
                    {
                        ((CheckBox) mFlashOptionView).setChecked(false);
                    }
                }
                
                vuforiaAppSession.stopCamera();
                
                try
                {
                    vuforiaAppSession
                        .startAR(command == CMD_CAMERA_FRONT ? CameraDevice.CAMERA.CAMERA_FRONT
                            : CameraDevice.CAMERA.CAMERA_BACK);
                } catch (VuforiaException e)
                {
                    showToast(e.getString());
                    Log.e(LOGTAG, e.getString());
                    result = false;
                }
                doStartTrackers();
                break;
        
        }
        
        return result;
    }
    
    
    private void showToast(String text)
    {
        Toast.makeText(getActivity(), text, Toast.LENGTH_SHORT).show();
    }


	@Override
	public void onClick(View v) {
		scanForMarker();		
	}

	public boolean isTrackableDefined() {
		return (this.trackableSource != null);
	}
	
	public boolean isTrackingStarted() {
		return this.trackingStarted;
	}
    
}
