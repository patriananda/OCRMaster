package com.patriananda.ocrmaster;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;


public class PreProcessingActivity extends AppCompatActivity {
    ImageView imageView;
    private static final boolean TRANSPARENT_IS_BLACK = false;
    private static final double SPACE_BREAKING_POINT = 13.0/30.0;
    Bitmap originalBitmap = null;
    private static final String TAG = "MainActivity";
    ViewDialog loadingDialog;
    Handler handler = new Handler();
    private Runnable processImage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_preprocessing);
        imageView = findViewById(R.id.imageView);
        loadingDialog = new ViewDialog(this);
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        Uri selectedImageUri = getIntent().getData();
        try {
            final InputStream imageStream = getContentResolver().openInputStream(Objects.requireNonNull(selectedImageUri));
            originalBitmap = BitmapFactory.decodeStream(imageStream);

            imageView.setImageBitmap(originalBitmap.copy(originalBitmap.getConfig(), true));

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(PreProcessingActivity.this, "Cannot load the image.", Toast.LENGTH_LONG).show();
        }
    }

    public void changeButtonState(boolean[] buttons) {
        Button btn = findViewById(R.id.grayscaleButton);
        btn.setEnabled(buttons[0]);
        btn = findViewById(R.id.binarizeButton);
        btn.setEnabled(buttons[1]);
        btn = findViewById(R.id.segmentationButton);
        btn.setEnabled(buttons[2]);
        btn = findViewById(R.id.originalButton);
        btn.setEnabled(buttons[3]);
        btn = findViewById(R.id.ocrButton);
        btn.setEnabled(buttons[4]);
    }

    public void onClickGrayScaleButton(View view) {
        processImage = new Runnable() {
            public void run() {
                try {
                    Mat oriMat = new Mat();
                    Utils.bitmapToMat(originalBitmap,oriMat);
                    Mat grayMat = new Mat();
                    Imgproc.cvtColor(oriMat, grayMat, Imgproc.COLOR_BGR2GRAY);
                    Bitmap grayBitmap = originalBitmap.copy(originalBitmap.getConfig(), true);
                    Utils.matToBitmap(grayMat, grayBitmap);
                    imageView.setImageBitmap(grayBitmap);
                    changeButtonState(new boolean[]{false, true, false, true, false});
                    loadingDialog.hideDialog();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        showLoading();
    }

    public void onClickBinarizeButton(View view) {
        processImage = new Runnable() {
            public void run() {
                try {
                    Bitmap finalSelectedBitmap = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
                    Bitmap binarizedImage = convertToMutable(finalSelectedBitmap);

                    for (int i = 0; i < binarizedImage.getWidth(); i++) {
                        for (int c = 0; c < binarizedImage.getHeight(); c++) {
                            if (shouldBeBlack(binarizedImage.getPixel(i, c))) {
                                binarizedImage.setPixel(i, c, Color.BLACK);
                            } else {
                                binarizedImage.setPixel(i, c, Color.WHITE);
                            }
                        }
                    }

                    imageView.setImageBitmap(binarizedImage);
                    changeButtonState(new boolean[]{false, false, true, true, false});
                    loadingDialog.hideDialog();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        showLoading();
    }

    public void onClickSegmentationButton(View view) {
        processImage = new Runnable() {
            public void run() {
                try {
                    AssetManager assetManager = getAssets();
                    String[] files;

                    files = assetManager.list("hijaiyah");
                    assert files != null;

                    Bitmap binarizedBitmap = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
                    Rect[] allDetections = new Rect[0];
                    Mat imageMatrix = new Mat();
                    Utils.bitmapToMat(binarizedBitmap, imageMatrix);

                    for (String fileName : files) {
                        System.out.println("File: " + fileName);
                        InputStream inputStream = getAssets().open("hijaiyah/" + fileName);
                        File appDir = getApplicationContext().getFilesDir();
                        File haarDir = new File(appDir, "haar_cascade");

                        if (!haarDir.exists()) // Jika belum ada folder "haar_cascade", maka
                            haarDir.mkdir(); // buat folder "haar_cascade"

                        // cari file dengan nama sesuai di asset folder
                        File cascadeFile = getFileStreamPath(fileName);

                        if (!cascadeFile.exists()) { // Jika belum ada file yang dimaksud, maka
                            cascadeFile = new File(haarDir, fileName); // siapkan file baru
                            FileOutputStream outputStream = new FileOutputStream(cascadeFile);

                            // buat file baru pada folder "haar_cascade"
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }

                            inputStream.close();
                            outputStream.close();
                        }

                        CascadeClassifier cascadeDetector = new CascadeClassifier(cascadeFile.getAbsolutePath());

                        if (cascadeDetector.empty()) {
                            Toast.makeText(PreProcessingActivity.this, "Error loading face cascade: " + cascadeFile.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                            return;
                        }

//                      Mat greyedImageMatrix = new Mat();
//                      Imgproc.cvtColor(imageMatrix, greyedImageMatrix, Imgproc.COLOR_BGR2GRAY);
//                      Imgproc.equalizeHist(greyedImageMatrix, greyedImageMatrix);

                        MatOfRect detectionRect = new MatOfRect();
                        cascadeDetector.detectMultiScale(imageMatrix, detectionRect);
                        Rect[] currentDetection = detectionRect.toArray();

                        int fal = currentDetection.length;        //determines length of firstArray
                        int sal = allDetections.length;   //determines length of secondArray
                        Rect[] result = new Rect[fal + sal];  //resultant array of size first array and second array
                        System.arraycopy(currentDetection, 0, result, 0, fal);
                        System.arraycopy(allDetections, 0, result, fal, sal);

                        allDetections = result;
                        System.out.println("curr detect: " + currentDetection.length);
                    }

                    // Draw a bounding box around each face.
                    for (Rect value : allDetections) {
                        Imgproc.rectangle(imageMatrix, new Point(value.x, value.y), new Point(value.x + value.width, value.y + value.height), new Scalar(255, 0, 0));
                        Imgproc.putText(imageMatrix, "dema", new Point(value.x, value.y), Imgproc.FONT_HERSHEY_DUPLEX, 1.0, new Scalar(0, 255, 255));
                    }

                    Bitmap segmentedImage = Bitmap.createBitmap(imageMatrix.cols(), imageMatrix.rows(), Bitmap.Config.ARGB_8888);
                    Utils.matToBitmap(imageMatrix, segmentedImage);
                    ByteArrayOutputStream fOut = new ByteArrayOutputStream();
                    segmentedImage.compress(Bitmap.CompressFormat.JPEG, 100, fOut);
                    byte[] bitmapData = fOut.toByteArray();

                    imageView.setImageBitmap(BitmapFactory.decodeByteArray(bitmapData, 0, bitmapData.length));
                    changeButtonState(new boolean[]{false, false, false, true, true});
                    loadingDialog.hideDialog();
                } catch (CvException e) {
                    Log.d(TAG, Objects.requireNonNull(e.getMessage()));
                } catch (IOException e) {
                    Log.d(TAG, Objects.requireNonNull(e.getMessage()));
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        showLoading();
    }

    public void onClickOriginalButton(View view) {
        processImage = new Runnable() {
            public void run() {
                try {
                    imageView.setImageBitmap(originalBitmap.copy(originalBitmap.getConfig(), true));
                    changeButtonState(new boolean[]{true, false, false, false, false});
                    loadingDialog.hideDialog();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        showLoading();
    }

    public void onClickProcessOCR(View view) {
        processImage = new Runnable() {
            public void run() {
                try {
                    Intent intent = new Intent(getApplicationContext(), PostProcessingActivity.class);
                    intent.putExtra("OCR_RESULT", "بسم الله");

                    loadingDialog.hideDialog();
                    startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        showLoading();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
            Log.v(TAG,"Permission: "+permissions[0]+ "was "+grantResults[0]);
            //resume tasks needing this permission
        }
    }

    public boolean isStoragePermissionGranted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.v(TAG,"Permission is granted");
                return true;
            } else {

                Log.v(TAG,"Permission is revoked");
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                return false;
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.v(TAG,"Permission is granted");
            return true;
        }
    }

    public static Bitmap convertImage(Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    private static boolean shouldBeBlack(int pixel) {
        int alpha = Color.alpha(pixel);
        int redValue = Color.red(pixel);
        int blueValue = Color.blue(pixel);
        int greenValue = Color.green(pixel);
        if(alpha == 0x00) //if this pixel is transparent let me use TRANSPARENT_IS_BLACK
            return TRANSPARENT_IS_BLACK;
        // distance from the white extreme
        double distanceFromWhite = Math.sqrt(Math.pow(0xff - redValue, 2) + Math.pow(0xff - blueValue, 2) + Math.pow(0xff - greenValue, 2));
        // distance from the black extreme //this should not be computed and might be as well a function of distanceFromWhite and the whole distance
        double distanceFromBlack = Math.sqrt(Math.pow(0x00 - redValue, 2) + Math.pow(0x00 - blueValue, 2) + Math.pow(0x00 - greenValue, 2));
        // distance between the extremes //this is a constant that should not be computed :p
        double distance = distanceFromBlack + distanceFromWhite;
        // distance between the extremes
        return ((distanceFromWhite/distance)>SPACE_BREAKING_POINT);
    }

    public Bitmap convertToMutable(Bitmap imgIn) {
        if(!isStoragePermissionGranted()) {
            return imgIn;
        }

        try {
            //this is the file going to use temporally to save the bytes.
            // This file will not be a image, it will store the raw image data.
            File file = new File(Environment.getExternalStorageDirectory() + File.separator + "temp.tmp");

            //Open an RandomAccessFile
            //Make sure you have added uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
            //into AndroidManifest.xml file
            RandomAccessFile randomAccessFile = new RandomAccessFile(file, "rw");

            // get the width and height of the source bitmap.
            int width = imgIn.getWidth();
            int height = imgIn.getHeight();
            Bitmap.Config type = imgIn.getConfig();

            //Copy the byte to the file
            //Assume source bitmap loaded using options.inPreferredConfig = Config.ARGB_8888;
            FileChannel channel = randomAccessFile.getChannel();
            MappedByteBuffer map = channel.map(FileChannel.MapMode.READ_WRITE, 0, imgIn.getRowBytes()*height);
            imgIn.copyPixelsToBuffer(map);
            //recycle the source bitmap, this will be no longer used.
            imgIn.recycle();
            System.gc();// try to force the bytes from the imgIn to be released

            //Create a new bitmap to load the bitmap again. Probably the memory will be available.
            imgIn = Bitmap.createBitmap(width, height, type);
            map.position(0);
            //load it back from temporary
            imgIn.copyPixelsFromBuffer(map);
            //close the temporary file and channel , then delete that also
            channel.close();
            randomAccessFile.close();

            // delete the temp file
            if (file.delete()) {
                System.out.println("File deleted");
            } else {
                System.out.println("Cannot delete the file");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return imgIn;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(processImage);
    }

    public void showLoading() {
        loadingDialog.showDialog();
        handler.postDelayed(processImage, 200);
    }
}
