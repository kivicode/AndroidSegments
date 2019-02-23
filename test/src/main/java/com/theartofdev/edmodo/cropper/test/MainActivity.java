package com.theartofdev.edmodo.cropper.test;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.test.R;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvException;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mOpenCVCallBack)) {
            Log.e("Load OpenCV", "Cannot connect to OpenCV Manager");
        }
    }

    /**
     * Start pick image activity with chooser.
     */
    public void onSelectImageClick(View view) {
        CropImage.activity(null).setGuidelines(CropImageView.Guidelines.ON).start(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // handle result of CropImageActivity
        if (requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE) {
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode == RESULT_OK) {
                Uri img = result.getUri();

                Mat mat = uri2mat(img);
                Mat newMat = process(mat, true);
                Bitmap bitmap = mat2bmp(newMat);
                ((ImageView) findViewById(R.id.quick_start_cropped_image)).setImageBitmap(bitmap);
                Toast.makeText(
                        this, "Cropping successful, Sample: " + result.getSampleSize(), Toast.LENGTH_LONG)
                        .show();
            } else if (resultCode == CropImage.CROP_IMAGE_ACTIVITY_RESULT_ERROR_CODE) {
                Toast.makeText(this, "Cropping failed: " + result.getError(), Toast.LENGTH_LONG).show();
            }
        }
    }

    Mat bmp2mat(Bitmap bmp) {
        Mat src = new Mat();
        Utils.bitmapToMat(bmp, src);
        return src;
    }

    Bitmap mat2bmp(Mat mat) {
        Bitmap bmp = null;
        Mat tmp = new Mat(mat.height(), mat.width(), CvType.CV_8U, new Scalar(4));
        try {
            //Imgproc.cvtColor(seedsImage, tmp, Imgproc.COLOR_RGB2BGRA);
            Imgproc.cvtColor(mat, tmp, Imgproc.COLOR_BGR2RGBA, 4);
            bmp = Bitmap.createBitmap(tmp.cols(), tmp.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(tmp, bmp);
        } catch (CvException e) {
            Log.d("Exception", e.getMessage());
        }
        return bmp;
    }

    Mat uri2mat(Uri img) {
        Bitmap bitmap = null;
        try {
            bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), img);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bmp2mat(bitmap);
    }

    private BaseLoaderCallback mOpenCVCallBack = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS: {
                    Log.i("Load OpenCV", "OpenCV loaded successfully");
                }
                break;
                default: {
                    super.onManagerConnected(status);
                }
                break;
            }
        }
    };


    Mat process(Mat orig, boolean working) {

        int x = 100;
        int y = 100;
        int w = 100;
        int h = 100;
        w = 600;
        h = 300;

        if (working) {
            float exp = (float) 1.75;
            int blur = 15;
            int threshold = 85;
            int adjustment = 4;
            int erode = 7;
            int iterations = 3;

            Mat expose = new Mat();
            Scalar S = new Scalar(exp, exp, exp);
            Core.multiply(orig, S, expose);

            Mat gray = new Mat();
            Imgproc.cvtColor(expose, gray, Imgproc.COLOR_BGR2GRAY);

            Mat cropped = new Mat();
            Size s = new Size(blur, (int) 1.5 * blur);
            Imgproc.GaussianBlur(gray, cropped, s, 10);

            Mat cropped_threshold = new Mat();
            Imgproc.adaptiveThreshold(cropped, cropped_threshold, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, threshold, adjustment);
            Imgproc.cvtColor(cropped, cropped, Imgproc.COLOR_GRAY2BGR);

            Mat karnel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(erode, erode));
            Mat inverse = new Mat();
            Imgproc.erode(cropped_threshold, inverse, karnel, iterations);
            Imgproc.GaussianBlur(inverse, inverse, new Size(9, 9), 0);

            List<MatOfPoint> contours = new ArrayList<>();
            Mat hr = new Mat();
            Imgproc.findContours(inverse, contours, hr, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_NONE);

//        float avgWidth = 0;
            float maxWidth = 0;
//        float avgHeight = 0;
//        float avgArea = 0;
//        for (Mat contour : contours) {
//            float area = (float) Imgproc.contourArea(contour);
//            avgArea += area;
//        }
//        avgArea /= contours.size();
            List<MatOfPoint> potetial_digits = new ArrayList<>();
            for (MatOfPoint contour : contours) {
                Rect sizes = Imgproc.boundingRect(contour);
                if (sizes.width / sizes.height <= .7 && sizes.width * sizes.height > 900 && sizes.height > sizes.width) {
                    potetial_digits.add(contour);
//                avgWidth += sizes.width;
//                avgHeight += sizes.height;
                    if (sizes.width > maxWidth) {
                        maxWidth = sizes.width;
                    }
                }
            }
            contours.clear();
//        avgWidth /= potentialdigits.size();
//        avgHeight /= potentialdigits.size();

            if (potetial_digits.size() > 0) {
                for (MatOfPoint cnt : potetial_digits) {
                    Rect sizes = Imgproc.boundingRect(cnt);
                    if (sizes.height > maxWidth) {
                        Point from = new Point(sizes.x + sizes.width, sizes.y);
                        Point to = new Point(sizes.x + sizes.width - (int) maxWidth, sizes.y + sizes.height);
                        Imgproc.rectangle(orig, from, to, new Scalar(255), 3);
                        int[] segments = new int[7];
                        for (int i = 0; i < 7; i++) {
                            try {
                                Mat mask = getMask(i, inverse, sizes.x, sizes.y, (int) maxWidth, (int) sizes.height);
                                Mat segment = new Mat();
                                System.err.println(mask);
                                Core.bitwise_or(inverse, inverse, segment, mask);
                                float num = Core.countNonZero(segment);
                                float total = Core.countNonZero(mask);
                                float percent = (float) num / total;
                                percent = (float) Math.round(percent * 1000) / 100;
                                if (percent > 5.5) {
                                    segments[i] = 1;
                                } else {
                                    segments[i] = 0;
                                }
                            } catch (Exception ignored) {
                                ignored.printStackTrace();
                            }
                        }
                        Imgproc.putText(orig, Arrays.toString(segments), new Point(from.x - sizes.width, from.y + sizes.height), Core.FONT_HERSHEY_COMPLEX, 1, new Scalar(0, 0, 255), 2);
                    }
                }
            }
//            return inverse;
        }

//        Imgproc.rectangle(orig, new Point((orig.width() - w) / 2, (orig.height() - h) / 2), new Point(w + (orig.width() - w) / 2, h + (orig.height() - h) / 2), new Scalar(0, 255, 0), 3);
        return orig;
    }

    Mat getMask(int id, Mat orig, int x, int y, int w, int h) {
        Mat mask = Mat.zeros(orig.rows(), orig.cols(), CvType.CV_8UC1);
        Point from = new Point(0, 0);
        Point to = new Point(0, 0);
        switch (id) {
            case 0:
                from = new Point(15, 0);
                to = new Point(w - 15, 15);
                break;
            case 1:
                from = new Point(0, 15);
                to = new Point(15, (h / 2.0) - (15 / 2.0));
                break;
            case 2:
                from = new Point(w - 15, 15);
                to = new Point(w, (h / 2.0) - (15 / 2.0));
                break;
            case 3:
                from = new Point(15, (h / 2.0) - (15 / 2.0) - 1);
                to = new Point(w - 15, (h / 2.0) + (15 / 2.0) + 1);
                break;
            case 4:
                from = new Point(0, (h / 2.0) + (15 / 2.0));
                to = new Point(15, h - 15);
                break;
            case 6:
                to = new Point(w - 15, (h / 2.0) + (15 / 2.0));
                break;
            case 5:
                from = new Point(15, h);
                to = new Point(w - 15, h - 15);
                break;
        }
        Imgproc.rectangle(mask, new Point(from.x + x, from.y + y), new Point(to.x + x, to.y + y), new Scalar(255), -1);
        return mask;
    }
}
