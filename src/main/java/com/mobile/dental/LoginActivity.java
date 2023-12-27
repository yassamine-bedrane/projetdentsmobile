package com.mobile.dental;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;
import org.json.JSONException;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class LoginActivity extends AppCompatActivity {

    EditText username;
    EditText password;
    Button loginButton;
    RequestQueue requestQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        loginButton = findViewById(R.id.loginButton);

        requestQueue = Volley.newRequestQueue(this);

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Make a request to the server to check credentials
                String url = "http://128.10.3.192:8080/students/credentials";
                makeLoginRequest(url);
            }
        });
    }

    private void makeLoginRequest(String url) {
        // Create a JSON array request
        JsonArrayRequest jsonArrayRequest = new JsonArrayRequest(
                Request.Method.GET,
                url,
                null,
                new Response.Listener<JSONArray>() {
                    @Override
                    public void onResponse(JSONArray response) {
                        // Parse the JSON response and check credentials
                        checkCredentials(response);
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // Handle errors (e.g., network error)
                        Toast.makeText(LoginActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                        Log.e("VolleyError", error.toString());
                    }
                }
        );

        requestQueue.add(jsonArrayRequest);
    }

    private void checkCredentials(JSONArray credentialsArray) {
        try {
            String enteredUsername = username.getText().toString();
            String enteredPassword = password.getText().toString();

            for (int i = 0; i < credentialsArray.length(); i++) {
                JSONArray credential = credentialsArray.getJSONArray(i);

                String storedUsername = credential.getString(0);
                String storedHashedPassword = credential.getString(1);

                if (enteredUsername.equals(storedUsername) && BCrypt.verifyer().verify(enteredPassword.toCharArray(), storedHashedPassword).verified) {

                    Intent intent = new Intent(LoginActivity.this, ChooseImageActivity.class);
                    startActivity(intent);
                    finish();
                    return;
                }
            }

            Toast.makeText(LoginActivity.this, "Login Failed!", Toast.LENGTH_SHORT).show();

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

}
