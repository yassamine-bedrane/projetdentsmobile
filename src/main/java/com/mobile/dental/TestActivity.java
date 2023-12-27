package com.mobile.dental;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TestActivity extends AppCompatActivity {
    private ImageView processedImageView;

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final int EXTRA_HEIGHT = 300;
    private Mat originalImage;

    private List<Point> selectedPoints = new ArrayList<>();
    private List<Point> edgePoints = new ArrayList<>();

    Mat colorImage;

    double taperAngleDeg;

    double taperAngleDeg2;
    private boolean pointsSelected = false;

    private int currentStep = 1;
    private List<MeasurementData> measurementDataList = new ArrayList<>();

    private String[] etapeStrings = {"Versants internes", "Versants externes", "Contre dépouille"};

    private int currentEtapeIndex = 0;
    private TextView etapeTextView;
    private Button nextButton;


    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test);
        if (OpenCVLoader.initDebug()) {
            Log.d("OpenCV", "OpenCV loaded successfully");
        } else {
            Log.e("OpenCV", "OpenCV initialization failed!");
            return;
        }

        etapeTextView = findViewById(R.id.etapeTextView);

        updateEtapeTextView();

        processedImageView = findViewById(R.id.selectedImageView);

        nextButton = findViewById(R.id.nextButton);

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("IMAGE_PATH")) {
            String imagePath = intent.getStringExtra("IMAGE_PATH");

            if (imagePath != null) {
                Bitmap bitmap = getBitmapFromContentUri(Uri.parse(imagePath));
                if (bitmap != null) {
                    processedImageView.setImageBitmap(bitmap);
                    processImage(Uri.parse(imagePath));
                } else {
                    Toast.makeText(TestActivity.this, "Failed to load image", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(TestActivity.this, "Empty image", Toast.LENGTH_SHORT).show();
            }
        }

        processedImageView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    handleTouch(event.getX(), event.getY());
                }
                return true;
            }
        });
    }

    private Bitmap getBitmapFromContentUri(Uri contentUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(contentUri);
            return BitmapFactory.decodeStream(inputStream);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    private void handleTouch(float x, float y) {
        // Convert touch coordinates to image coordinates
        Point imagePoint = touchToImageCoordinates(x, y);

        Point closestEdgePoint = findClosestEdgePoint(imagePoint);

        selectedPoints.add(closestEdgePoint);

        Mat colorImage = new Mat();
        Imgproc.cvtColor(originalImage, colorImage, Imgproc.COLOR_GRAY2BGR);

        for (Point selectedPoint : selectedPoints) {
            Imgproc.circle(colorImage, selectedPoint, 5, new Scalar(255, 0, 0), -1);
        }
        displayImage(colorImage);
        if (selectedPoints.size() == 4) {
            pointsSelected = true;
            calculateAndDisplayTaperAngles(selectedPoints);
        }
    }

    private Point findClosestEdgePoint(Point touchPoint) {
        double minDistance = Double.MAX_VALUE;
        Point closestPoint = null;

        for (Point edgePoint : edgePoints) {
            double distance = Math.sqrt(Math.pow(touchPoint.x - edgePoint.x, 2) + Math.pow(touchPoint.y - edgePoint.y, 2));
            if (distance < minDistance) {
                minDistance = distance;
                closestPoint = edgePoint;
            }
        }

        return closestPoint;
    }

    private Point touchToImageCoordinates(float x, float y) {
        // Get the width and height of the displayed image view
        int viewWidth = processedImageView.getWidth();
        int viewHeight = processedImageView.getHeight();

        // Get the dimensions of the original image
        int imageWidth = originalImage.cols();
        int imageHeight = originalImage.rows();

        // Calculate the scaling factors for width and height
        double xScale = imageWidth / (double) viewWidth;
        double yScale = imageHeight / (double) viewHeight;

        // Calculate the image coordinates
        double imageX = x * xScale;
        double imageY = y * yScale;

        return new Point(imageX, imageY);
    }


    private void processImage(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);

            byte[] imageBytes = readInputStream(inputStream);

            originalImage = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_UNCHANGED);
            int extraHeight = 300; // Adjust this value based on your requirements
            originalImage = extendImage(originalImage, extraHeight);

            Imgproc.cvtColor(originalImage, originalImage, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(originalImage, originalImage, new Size(9, 9), 0);
            Imgproc.Canny(originalImage, originalImage, 50, 150);
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
            Imgproc.dilate(originalImage, originalImage, kernel);

            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(originalImage, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

            for (MatOfPoint contour : contours) {
                for (Point point : contour.toArray()) {
                    edgePoints.add(point);
                }
            }

            // Hough Transform for Line Detection
            Mat lines = new Mat();
            Imgproc.HoughLinesP(originalImage, lines, 1, Math.PI / 180, 50, 50, 10);

            displayImage(originalImage);

            // Calculate and Store Angles
            List<Double> leftTaperAngles = new ArrayList<>();
            List<Double> rightTaperAngles = new ArrayList<>();
            for (int i = 0; i < lines.rows(); i++) {
                double[] line = lines.get(i, 0);
                double angleRadians = Math.atan2(line[3] - line[1], line[2] - line[0]);
                double angleDegrees = Math.toDegrees(angleRadians);

                // Store the angle values for later evaluation
                if (angleDegrees < 0) {
                    leftTaperAngles.add(angleDegrees);
                } else {
                    rightTaperAngles.add(angleDegrees);
                }
            }


        } catch (IOException e) {
            e.printStackTrace();
            showToast("Error processing image");
        }
    }

    private MeasurementData calculateAndDisplayTaperAngles(List<Point> selectedPoints) {
        // Ensure exactly 4 points are selected
        if (selectedPoints.size() != 4) {
            showToast("Please select 4 points");
            return null;
        }

        // Order the points based on x-coordinate (assuming left to right order)
        Collections.sort(selectedPoints, new Comparator<Point>() {
            @Override
            public int compare(Point p1, Point p2) {
                return Double.compare(p1.x, p2.x);
            }
        });

        Point leftTop = selectedPoints.get(0);
        Point leftBottom = selectedPoints.get(1);
        Point rightTop = selectedPoints.get(2);
        Point rightBottom = selectedPoints.get(3);

        double deltaY = Math.abs(leftTop.y - leftBottom.y);
        double deltaX = Math.abs(leftTop.x - leftBottom.x);
        double L = Math.hypot(deltaY, deltaX);

        double taperAngleRad = Math.atan(deltaY / deltaX);
        taperAngleDeg = 90 - Math.toDegrees(taperAngleRad);
        taperAngleDeg = roundTo3DecimalPlaces(taperAngleDeg);


        double deltaY2 = Math.abs(rightTop.y - rightBottom.y);
        double taperAngleRad2 = Math.atan(deltaY2 / deltaX);
        taperAngleDeg2 = 90 - Math.toDegrees(taperAngleRad2);
        taperAngleDeg2 = roundTo3DecimalPlaces(taperAngleDeg2);

        colorImage = new Mat();
        Imgproc.cvtColor(originalImage, colorImage, Imgproc.COLOR_GRAY2BGR);

        drawInfiniteLine(colorImage, leftTop, leftBottom, new Scalar(0, 255, 0), 2);

        drawInfiniteLine(colorImage, rightTop, rightBottom, new Scalar(0, 255, 0), 2);

        //Imgproc.line(colorImage, new Point(leftTop.x, 0), new Point(leftTop.x, colorImage.rows()), new Scalar(0, 0, 255), 2);
        //Imgproc.line(colorImage, new Point(rightBottom.x, 0), new Point(rightBottom.x, colorImage.rows()), new Scalar(0, 0, 255), 2);

        displayImage(colorImage);
        return new MeasurementData(convertMatToBase64(colorImage.clone()), taperAngleDeg, taperAngleDeg2);


    }


    private void displayImage(Mat image) {
        Bitmap processedBitmap = Bitmap.createBitmap(image.cols(), image.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(image, processedBitmap);
        processedImageView.setImageBitmap(processedBitmap);
    }


    private byte[] readInputStream(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, bytesRead);
        }

        return outputStream.toByteArray();
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    public void onDisplayResultsClick(View view) {
        if (pointsSelected) {
            showAnglePopup();
        } else {
            showToast("Please select 4 points before displaying results");
        }
    }

    private void showAnglePopup() {
        // Create a custom layout for the dialog
        View popupView = getLayoutInflater().inflate(R.layout.popup_layout, null);

        // Find TextViews in the layout
        // Modify the layout to handle multiple angles as needed
        // This example assumes a simple display with two angles
        TextView angle1TextView = popupView.findViewById(R.id.angle1TextView);
        TextView angle2TextView = popupView.findViewById(R.id.angle2TextView);

        // Display all stored measurement data
        // Display all stored measurement data if available
        StringBuilder anglesText = new StringBuilder();


        if (!measurementDataList.isEmpty()) {
            for (int i = 0; i < measurementDataList.size(); i++) {
                MeasurementData measurementData = measurementDataList.get(i);
                anglesText.append("Step ").append(i + 1).append(": \n");
                anglesText.append("Left Angle : ").append(measurementData.getTaperAngleDeg()).append(" degrees, \n");
                anglesText.append("Right Angle : ").append(measurementData.getTaperAngleDeg2()).append(" degrees \n");
                if (i < measurementDataList.size() - 1) {
                    anglesText.append("\n");
                }
            }


        }
        anglesText.append("\nStep ").append(measurementDataList.isEmpty() ? 1 : measurementDataList.size() + 1).append(": \n");
        anglesText.append("Left Angle : ").append(taperAngleDeg).append(" degrees, \n");
        anglesText.append("Right Angle : ").append(taperAngleDeg2).append(" degrees\n");

        // Set angle values from the latest MeasurementData
        angle1TextView.setText(anglesText.toString());

        // Display the updated color image from the latest MeasurementData
        //displayImage(measurementDataList.get(measurementDataList.size() - 1).getProcessedImage());

        // Create an AlertDialog
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        alertDialogBuilder.setView(popupView);

        // Add a button to dismiss the dialog
        alertDialogBuilder.setPositiveButton("OK", null);

        // Show the dialog
        AlertDialog alertDialog = alertDialogBuilder.create();
        alertDialog.show();

    }


    private double roundTo3DecimalPlaces(double value) {
        DecimalFormat decimalFormat = new DecimalFormat("#.###");
        return Double.parseDouble(decimalFormat.format(value));
    }

    public void onResetClick(View view) {
        selectedPoints.clear();
        taperAngleDeg = 0.0;
        taperAngleDeg2 = 0.0;
        pointsSelected = false;
        displayImage(originalImage);

    }

    private Mat extendImage(Mat image, int extraHeight) {
        int newHeight = image.rows() + extraHeight;
        Mat originalImage = new Mat(new Size(image.cols(), newHeight), image.type());

        // Draw a filled black rectangle at the top
        Imgproc.rectangle(originalImage, new Point(0, 0), new Point(image.cols(), extraHeight), new Scalar(0, 0, 0), -1);

        // Copy the original image to the bottom of the larger blank image
        image.copyTo(originalImage.submat(new Rect(0, extraHeight, image.cols(), image.rows())));

        return originalImage;
    }

    private void drawInfiniteLine(Mat image, Point p1, Point p2, Scalar color, int thickness) {
        double slope = (p2.y - p1.y) / (p2.x - p1.x);
        double intercept = p1.y - slope * p1.x;

        double extensionDistance = 100;
        Point extendedP1 = new Point(p1.x - extensionDistance, slope * (p1.x - extensionDistance) + intercept);
        Point extendedP2 = new Point(p2.x + extensionDistance, slope * (p2.x + extensionDistance) + intercept);

        Imgproc.line(image, p1, p2, color, thickness);

        Imgproc.line(image, extendedP1, extendedP2, color, thickness);

        Point bottomPoint1 = new Point(p1.x, image.rows());
        Point bottomPoint2 = new Point(p2.x, image.rows());


    }

    public void onNextClick(View view) {
        if (currentEtapeIndex == 1) {
            nextButton.setText("Save");
            nextButton.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        } else if (currentEtapeIndex == 2) {
            if (measurementDataList.isEmpty()) {
                showToast("Please complete previous steps before proceeding");
                return;
            }
            storeMeasurementData();


            MeasurementData measurementData1 = measurementDataList.get(0);
            MeasurementData measurementData2 = measurementDataList.get(1);
            MeasurementData measurementData3 = measurementDataList.get(2);

            String base64Image = measurementData1.getProcessedImage();
            double alpha1 = measurementData1.getTaperAngleDeg();
            double beta1 = measurementData1.getTaperAngleDeg2();
            double alpha2 = measurementData2.getTaperAngleDeg();
            double beta2 = measurementData2.getTaperAngleDeg2();
            double alpha3 = measurementData3.getTaperAngleDeg();
            double beta3 = measurementData3.getTaperAngleDeg2();

            makePostRequest(base64Image, alpha1, beta1, alpha2, beta2, alpha3, beta3);

            Intent intent = new Intent(this, ChooseImageActivity.class);
            startActivity(intent);
            finish();

        }

        if (pointsSelected) {
            storeMeasurementData();
            selectedPoints.clear();
            taperAngleDeg = 0.0;
            taperAngleDeg2 = 0.0;
            displayImage(originalImage);
            pointsSelected = false;
            currentEtapeIndex++;

            if (currentEtapeIndex < etapeStrings.length) {
                updateEtapeTextView();
            }
        } else {
            showToast("Please select 4 points before proceeding to the next step");
        }
    }


    private void storeMeasurementData() {
        MeasurementData measurementData = new MeasurementData(convertMatToBase64(colorImage), taperAngleDeg, taperAngleDeg2);
        measurementDataList.add(measurementData);
    }

    private void updateEtapeTextView() {
        String etapeText = "Étape " + (measurementDataList.size() + 1) + ": " + etapeStrings[currentEtapeIndex];
        etapeTextView.setText(etapeText);
    }

    public void onChooseAnotherImageClick(View view) {
        Intent intent = new Intent(this, ChooseImageActivity.class);
        startActivity(intent);
        finish();
    }

    public static String convertMatToBase64(Mat mat) {
        Bitmap bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(mat, bitmap);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();

        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    private void makePostRequest(String base64Image1,
                                 double alpha1_1, double beta1_1,
                                 double alpha1_2, double beta1_2,
                                 double alpha1_3, double beta1_3
    ) {
        String url = "http://128.10.3.192:8080/students/create";

        RequestQueue requestQueue = Volley.newRequestQueue(this);

        JSONObject requestBody = new JSONObject();
        try {
            // Assuming your server expects specific keys for the request
            requestBody.put("id", 1);

            // Create JSONArray for the "list" field
            JSONArray listArray = new JSONArray();



            // Create JSONObject for the first measurement data
            JSONObject measurementData1 = new JSONObject();
            measurementData1.put("id", 1);
            measurementData1.put("alpha1", alpha1_1);
            measurementData1.put("alpha2", alpha1_2);
            measurementData1.put("alpha3", alpha1_3);
            measurementData1.put("beta1", beta1_1);
            measurementData1.put("beta2", beta1_2);
            measurementData1.put("beta3", beta1_3);
            measurementData1.put("imageFront", "base64Image1");
            //Log.e("string",base64Image1);
            measurementData1.put("imageFront", base64Image1);

            //Log.e("json",measurementData1.toString());




            // Add the first measurement data to the list
            listArray.put(measurementData1);

            requestBody.put("list", listArray);

            // Add the remaining parameters
            requestBody.put("studentId", 3);
            requestBody.put("pwId", 2);

        } catch (JSONException e) {
            e.printStackTrace();
            // Log the error
            Log.e("JSONException", "Error creating JSON object", e);
            // Display a Toast message for the error
            showToast("Error creating JSON object");
            return;
        }

        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(
                Request.Method.POST,
                url,
                requestBody,
                new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject response) {
                        // Handle the response if needed
                        Log.d("VolleyResponse", response.toString());
                        // Display a Toast message for the response
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // Handle the error
                        Log.e("VolleyError", "Error during POST request", error);
                        // Display a Toast message for the error
                    }
                });

        // Add the request to the queue
        requestQueue.add(jsonObjectRequest);
    }
}




    class MeasurementData {
    private String processedImage;
    private double taperAngleDeg;
    private double taperAngleDeg2;

    public MeasurementData(String processedImage, double taperAngleDeg, double taperAngleDeg2) {
        this.processedImage = processedImage;
        this.taperAngleDeg = taperAngleDeg;
        this.taperAngleDeg2 = taperAngleDeg2;
    }


    public String getProcessedImage() {
        return processedImage;
    }

    public double getTaperAngleDeg() {
        return taperAngleDeg;
    }

    public double getTaperAngleDeg2() {
        return taperAngleDeg2;
    }

    public void setTaperAngleDeg(double taperAngleDeg) {
        this.taperAngleDeg = taperAngleDeg;
    }

    public void setTaperAngleDeg2(double taperAngleDeg2) {
        this.taperAngleDeg2 = taperAngleDeg2;
    }
    public void setProcessedImage(String processedImage) {
        this.processedImage = processedImage;
    }
}

