package com.mobile.dental;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.MatOfFloat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int PICK_IMAGE_REQUEST = 1;
    private ImageView processedImageView;

    Mat originalImage;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (OpenCVLoader.initDebug()) {
            showToast("OpenCV loaded successfully");
        } else {
            showToast("OpenCV initialization failed!");
            return;
        }


        Button pickImageButton = findViewById(R.id.pickImageButton);
        Button sendRequestButton = findViewById(R.id.sendRequestButton);

        pickImageButton.setOnClickListener(this);
        sendRequestButton.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.pickImageButton) {
            openImagePicker();
        } else if (v.getId() == R.id.sendRequestButton) {
            makePostRequest();
        }
    }


    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri selectedImageUri = data.getData();
            processImage(selectedImageUri);
            findViewById(R.id.pickImageButton).setVisibility(View.GONE);
        }
    }

    private void processImage(Uri imageUri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(imageUri);

            byte[] imageBytes = readInputStream(inputStream);

            originalImage = Imgcodecs.imdecode(new MatOfByte(imageBytes), Imgcodecs.IMREAD_UNCHANGED);

            Imgproc.cvtColor(originalImage, originalImage, Imgproc.COLOR_BGR2GRAY);
            Imgproc.GaussianBlur(originalImage, originalImage, new Size(9, 9), 0);
            Imgproc.Canny(originalImage, originalImage, 50, 150);
            Mat kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(3, 3));
            Imgproc.dilate(originalImage, originalImage, kernel);

            List<MatOfPoint> contours = new ArrayList<>();
            Mat hierarchy = new Mat();
            Imgproc.findContours(originalImage, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);



            Mat lines = new Mat();
            Imgproc.HoughLinesP(originalImage, lines, 1, Math.PI / 180, 50, 50, 10);




        } catch (IOException e) {
            e.printStackTrace();
            showToast("Error processing image");
        }
    }

    private void sendImageRequest() {
        String url = "http://192.168.1.3:8088/testimage/upload";
        final String imageData = "4AAQSkZJRgABAQEAAAAAAAD/";

        RequestQueue requestQueue = Volley.newRequestQueue(this);

        StringRequest stringRequest = new StringRequest(Request.Method.POST, url,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        // Handle the response
                        Log.d("VolleyResponse", response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // Handle the error
                        Log.e("VolleyError", "Error occurred", error);
                    }
                }) {
            @Override
            public byte[] getBody() {
                return imageData.getBytes();
            }

            @Override
            public String getBodyContentType() {
                // Set the content type as plain text
                return "text/plain";
            }
        };

        // Add the request to the RequestQueue
        requestQueue.add(stringRequest);
    }








    private byte[] matToByteArray(Mat mat) {
        MatOfByte matOfByte = new MatOfByte();
        Imgcodecs.imencode(".png", mat, matOfByte);
        return matOfByte.toArray();
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


    private void makePostRequest(
    ) {
        String url = "http://128.10.5.28:8088/students/create";

        RequestQueue requestQueue = Volley.newRequestQueue(this);


        String base64Image = "";
        double alpha1 = 45.0;  // Replace with your actual value
        double beta1 = 30.0;   // Replace with your actual value
        double alpha2 = 60.0;  // Replace with your actual value
        double beta2 = 25.0;   // Replace with your actual value
        double alpha3 = 35.0;  // Replace with your actual value
        double beta3 = 40.0;   // Replace with your actual value



        JSONObject requestBody = new JSONObject();
        try {
            // Assuming your server expects specific keys for the request
            requestBody.put("id", 1);

            // Create JSONArray for the "list" field
            JSONArray listArray = new JSONArray();

            // Create JSONObject for the first measurement data
            JSONObject measurementData1 = new JSONObject();
            measurementData1.put("id", 1);
            measurementData1.put("alpha1", alpha1);
            measurementData1.put("alpha2", alpha1);
            measurementData1.put("alpha3", alpha1);
            measurementData1.put("beta1", beta1);
            measurementData1.put("beta2", beta1);
            measurementData1.put("beta3", beta1);
            measurementData1.put("imageFront", base64Image);

            // Add the first measurement data to the list
            listArray.put(measurementData1);

            requestBody.put("list", listArray);

            // Add the remaining parameters
            requestBody.put("studentId", 36);
            requestBody.put("pwId", 17);

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
                        showToast("Request successful");
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // Handle the error
                        Log.e("VolleyError", "Error during POST request", error);
                        // Display a Toast message for the error
                        showToast("Error during POST request");
                    }
                });

        // Add the request to the queue
        requestQueue.add(jsonObjectRequest);
    }


}
