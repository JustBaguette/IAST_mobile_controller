package com.example.iast;

import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import io.github.controlwear.virtual.joystick.android.JoystickView;
import androidx.appcompat.widget.SwitchCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;

import tech.gusavila92.websocketclient.WebSocketClient;

public class MainActivity extends AppCompatActivity {

    WebSocketClient webSocketClient = null;
    int max = 40;
    int min = 0;
    EditText ip;

    TextView angles, speeds;
    String ip_string;
    Button ipChange;
    int previ;
    int prevag;
    Boolean isConnected = false;

    private SwitchCompat mobSwitch;

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        angles = findViewById(R.id.textView_angle_left);
        speeds = findViewById(R.id.textView_strength_left);

        JoystickView joystick = findViewById(R.id.joystickView);
        ipChange = findViewById(R.id.ipChange);
        ip = findViewById(R.id.ip);
        mobSwitch = findViewById(R.id.mobile);

        ipChange.setOnClickListener(view -> {
            ip_string = ip.getText().toString();
            createWebSocketClient();
        });

        joystick.setOnMoveListener((int angle, int strength) -> {
            int newang = angle - 90;
            if (angle > 0 && angle < 90) {
                newang = 270 + angle;
            }
            int i = strength * (max - min) / 100;

            angles.setText(newang+ "°");
            speeds.setText(i +"%");

            Log.i("JoystickControl", "Angle: " + newang + "°, Speed: " + i + "%");

            if (previ != i || prevag != newang) {
                JSONObject jo = new JSONObject();
                try {
                    jo.put("d", "m");
                    jo.put("sp", i);
                    jo.put("m", newang);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                previ = i;
                prevag = newang;
                if (isConnected) {
                    webSocketClient.send(jo.toString());
                }
            }
        });

        mobSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !isConnected) {
                createWebSocketClient();
            } else if (!isChecked && isConnected) {
                webSocketClient.close();  // Close the WebSocket connection when mobSwitch is turned off
                isConnected = false;
            }
        });
    }

    public void createWebSocketClient() {
        if (isConnected) return;  // Exit if already connected

        URI uri;
        try {
            uri = new URI(ip_string);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        webSocketClient = new WebSocketClient(uri) {

            @Override
            public void onOpen() {
                isConnected = true;
                Log.i("WebSocket", "Session is starting");
                JSONObject jo = new JSONObject();
                try {
                    jo.put("isf", "t");
                    jo.put("d", "m");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                this.send(jo.toString());
                runOnUiThread(() -> mobSwitch.setChecked(true));
            }

            @Override
            public void onTextReceived(String s) {
                Log.i("WebSocket", "Message received");
                JSONObject jo;
                JSONObject dev = null;
                String type = null;

                try {
                    jo = new JSONObject(s);

                    // Check if the key "conection" exists before accessing it
                    if (jo.has("conection")) {
                        String connection = jo.getString("conection");

                        if (jo.has("devices")) {
                            String devices = jo.getString("devices");
                            dev = new JSONObject(devices);
                        }

                        switch (connection) {
                            case "true":
                                type = "You are Connected";
                                break;
                            case "newConnection":
                                type = "New device Connected";
                                break;
                            case "alreadyConnected":
                                type = "You are already Connected";
                                break;
                            case "lost":
                                type = "A device lost Connection";
                                break;
                            default:
                                type = "Unknown connection status";
                                break;
                        }
                    } else {
                        Log.e("WebSocket", "No value for 'conection' in received JSON.");
                        type = "Connection status unknown"; // Handle missing connection status
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                JSONObject finalDev = dev;
                String finalType = type != null ? type : "Unknown status";

                runOnUiThread(() -> {
                    try {
                        if (finalDev != null && "true".equals(finalDev.getString("mobile"))) {
                            mobSwitch.setChecked(true);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onBinaryReceived(byte[] data) { }

            @Override
            public void onPingReceived(byte[] data) { }

            @Override
            public void onPongReceived(byte[] data) { }

            @Override
            public void onException(Exception e) {
                Log.e("WebSocket", Objects.requireNonNull(e.getMessage()));
            }

            @Override
            public void onCloseReceived() {
                Log.i("WebSocket", "Closed");
                isConnected = false;
                runOnUiThread(() -> mobSwitch.setChecked(false));

                JSONObject jo = new JSONObject();
                try {
                    jo.put("isf", "f");
                    jo.put("device", "m");
                    jo.put("target", "ESP");
                    jo.put("conection", "message");
                    jo.put("message", 5);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        };

        webSocketClient.setConnectTimeout(10000);
        webSocketClient.setReadTimeout(600000);
        webSocketClient.connect();
    }
}
