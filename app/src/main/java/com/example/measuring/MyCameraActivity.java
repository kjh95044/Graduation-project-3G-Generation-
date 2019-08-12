package com.example.measuring;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import org.opencv.android.JavaCameraView;

import java.io.FileOutputStream;

public class MyCameraActivity extends JavaCameraView implements android.hardware.Camera.PictureCallback {
    static {
        System.loadLibrary("navite-lib");
    }
    private static final String TAG = "OpenCV";
    private String mPictureFileName;

    public MyCameraActivity(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void takePicture(final String FileName) {
        Log.i(TAG, "Taking picture");
        this.mPictureFileName = FileName;
        mCamera.setPreviewCallback(null);

        // PictureCallback is implemented by the current class
        mCamera.takePicture(null, null, this);
    }

    @Override
    public void onPictureTaken(byte[] data, android.hardware.Camera camera) {
        Log.i(TAG, "Saving a bitmap to file");
        mCamera.startPreview();
        mCamera.setPreviewCallback(this);

        // Write an image in a file
        try {
            FileOutputStream output = new FileOutputStream(mPictureFileName);
            output.write(data);
            output.close();
        } catch(java.io.IOException e) {
            Log.e("PictureDemo", "Exception in PhotoCallback", e);
        }
    }
}
