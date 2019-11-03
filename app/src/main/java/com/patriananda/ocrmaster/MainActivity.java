package com.patriananda.ocrmaster;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "takePictureProcess";
    private static final int OPEN_GALLERY = 101;
    private static final int TAKE_PICTURE = 102;
    private Camera mCamera;
    private PreviewActivity mPreview;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initCameraInstance();
    }

    private void initCameraInstance() {
        checkRequiredPermission();
        if (mThread == null) {
            mThread = new CameraHandlerThread();
        }
        synchronized (mThread) {
            mThread.openCamera();
        }
    }

    private void checkRequiredPermission() {
    //  ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, ALL_PERMISSIONS);
        if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                //return;
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, OPEN_GALLERY);
            }
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, TAKE_PICTURE);
        }
    }

    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            File pictureFile = getOutputMediaFile();
            if (pictureFile == null){
                Log.d(TAG, "Error creating media file, check storage permissions");
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);

                fos.write(data);
                fos.close();

                Intent nextIntent = new Intent(getApplicationContext(), PreProcessingActivity.class);
                nextIntent.setData(Uri.fromFile(pictureFile));
                startActivity(nextIntent);
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
        }
    };

    // Add a listener to the Capture button
    public void takePicture(View v) {
        // get an image from the camera
        mCamera.takePicture(null, null, mPicture);
    }

    // Add a listener to the Open Gallery button
    public void openGallery(View v) {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, OPEN_GALLERY);
    }

    /** A safe way to get an instance of the Camera object. */
    private void createCameraPreview() {
        try {
            mCamera = Camera.open(1); // attempt to get a Camera instance

            // Create our Preview view and set it as the content of our activity.
            mPreview = new PreviewActivity(this, mCamera);
            FrameLayout preview = findViewById(R.id.camera_preview);
            preview.addView(mPreview);
        }
        catch (RuntimeException e) {
            // Camera is not available (in use or does not exist)
        }
    }


    private CameraHandlerThread mThread = null;

    private class CameraHandlerThread extends HandlerThread {
        Handler mHandler;

        CameraHandlerThread() {
            super("CameraHandlerThread");
            start();
            mHandler = new Handler(getLooper());
        }

        synchronized void notifyCameraOpened() {
            notify();
        }

        void openCamera() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    createCameraPreview();
                    notifyCameraOpened();
                }
            });
            try {
                wait();
            }
            catch (InterruptedException e) {
                Log.w(TAG, "wait was interrupted");
            }
        }
    }

    /** Create a File for saving an image or video */
    private static File getOutputMediaFile() {
        // To be safe, we should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "OCRMaster");
        // This location works best if we want the created images to be shared
        // between applications and persist after our app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                "IMG_"+ timeStamp + ".jpg");

        return mediaFile;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) {
            return;
        }

        Intent nextIntent = new Intent(getApplicationContext(), PreProcessingActivity.class);
        Uri imageUri = null;

        if (requestCode == OPEN_GALLERY) {
            imageUri = data.getData();
        }

        nextIntent.setData(imageUri);
        startActivity(nextIntent);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initCameraInstance();
        } else {
            System.exit(1);
        }
    }
}