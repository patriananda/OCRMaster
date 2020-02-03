package com.patriananda.ocrmaster;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.widget.Toast;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;

import java.util.List;

class ObjectDetection {
    ObjectDetection() {
        super();
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
    }

    Bitmap run(Bitmap bitmapSrc, String filenameFaceCascade, String filenameEyesCascade) {
        CascadeClassifier faceCascade = new CascadeClassifier();
        CascadeClassifier eyesCascade = new CascadeClassifier();

        if (!faceCascade.load(filenameFaceCascade)) {
            System.err.println("--(!)Error loading face cascade: " + filenameFaceCascade);
            return bitmapSrc;
        }

        if (!eyesCascade.load(filenameEyesCascade)) {
            System.err.println("--(!)Error loading eyes cascade: " + filenameEyesCascade);
            return bitmapSrc;
        }

        Mat mat = new Mat();
        Bitmap bmp32 = bitmapSrc.copy(Bitmap.Config.ARGB_8888, true);
        Utils.bitmapToMat(bmp32, mat);
        Mat segmentedResult = detectAndDisplay(mat, faceCascade, eyesCascade);

        Bitmap bm = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(segmentedResult, bm);

        return bm;
    }

    private Mat detectAndDisplay(Mat frame, CascadeClassifier faceCascade, CascadeClassifier eyesCascade) {
        Mat frameGray = new Mat();
        Imgproc.cvtColor(frame, frameGray, Imgproc.COLOR_BGR2GRAY);
        Imgproc.equalizeHist(frameGray, frameGray);

        Bitmap bm = Bitmap.createBitmap(frameGray.cols(), frameGray.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(frameGray, bm);

        // -- Detect faces
        MatOfRect faces = new MatOfRect();
        faceCascade.detectMultiScale(frameGray, faces);
        List<Rect> listOfFaces = faces.toList();
        for (Rect face : listOfFaces) {
            Point center = new Point(face.x + face.width / 2, face.y + face.height / 2);
            Imgproc.ellipse(frame, center, new Size(face.width / 2, face.height / 2), 0, 0, 360,
                    new Scalar(255, 0, 255));
            frameGray = frameGray.submat(face);
            // -- In each face, detect eyes
        }

        MatOfRect eyes = new MatOfRect();
        eyesCascade.detectMultiScale(frameGray, eyes);
        List<Rect> listOfEyes = eyes.toList();
        for (Rect eye : listOfEyes) {
            Point eyeCenter = new Point(eye.x + eye.width / 2, eye.y + eye.height / 2);
            int radius = (int) Math.round((eye.width + eye.height) * 0.25);
            Imgproc.circle(frame, eyeCenter, radius, new Scalar(255, 0, 0), 4);
            frameGray = frameGray.submat(eye);
        }

        return frame;
    }
}
