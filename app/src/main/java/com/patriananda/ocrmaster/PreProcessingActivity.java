package com.patriananda.ocrmaster;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
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

import java.io.File;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preprocessing);

        imageView = findViewById(R.id.imageView);

        Uri selectedImageUri = getIntent().getData();
        try {
            final InputStream imageStream = getContentResolver().openInputStream(Objects.requireNonNull(selectedImageUri));
            originalBitmap = BitmapFactory.decodeStream(imageStream);

            imageView.setImageBitmap(originalBitmap.copy(originalBitmap.getConfig(), true));
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(PreProcessingActivity.this, "Cannot load the image.", Toast.LENGTH_LONG).show();
        }

        Button ocrButton = findViewById(R.id.ocrButton);
        ocrButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), PostProcessingActivity.class);
                startActivity(intent);
            }
        });
    }

    public void onClickGrayScaleButton(View view) {
        imageView.setImageBitmap(convertImage(originalBitmap.copy(originalBitmap.getConfig(), true)));
    }


    public void onClickBinarizeButton(View view) {
        Bitmap finalSelectedBitmap = ((BitmapDrawable)imageView.getDrawable()).getBitmap();
        Bitmap binarizedImage = convertToMutable(finalSelectedBitmap);

        final AlertDialog.Builder dialog = new AlertDialog.Builder(this)
                .setTitle("Processing Image")
                .setMessage("Loading...");
        final AlertDialog alert = dialog.create();
        alert.show();

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

        // Hide loading dialog after some seconds
        final Handler handler  = new Handler();
        final Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (alert.isShowing()) {
                    alert.dismiss();
                }
            }
        };
        handler.postDelayed(runnable, 10000);

        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                handler.removeCallbacks(runnable);
            }
        });
    }

    public void onClickSegmentationButton(View view) {
    }

    public void onClickOriginalButton(View view) {
        imageView.setImageBitmap(originalBitmap.copy(originalBitmap.getConfig(), true));
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

}
