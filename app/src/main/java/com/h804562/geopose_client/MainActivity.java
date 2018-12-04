package com.h804562.geopose_client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.Manifest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.Transport;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Manager;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity {

    private TextView tvX;
    private TextView tvY;
    private TextView tvZ;
    private TextView tvW;
    private TextView tvLat;
    private TextView tvLng;
    private TextView tvHeight;
    private Button start;
    private Button stopp;
    private Socket socket;

    private static final int MOCK_HEIGHT = 55;
    private static final String ANIMATE_VIEWER_EVENT = "animate viewer";
    private static final String START_MOBILE_CLIENT_EVENT = "start mobile client";
    private static final String EMULATOR_URL = "http://10.0.2.2:3000";
    private static final String DEVICE_URL = "http://192.168.6.152:3000";
    private final String TAG = "MainActivity";

    /**
     * permissions request code
     */
    private final static int REQUEST_CODE_ASK_PERMISSIONS = 1;

    /**
     * Permissions that need to be explicitly requested from end user.
     */
    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[] {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkPermissions();
        setContentView(R.layout.activity_main);
        tvX = findViewById(R.id.x);
        tvY = findViewById(R.id.y);
        tvZ = findViewById(R.id.z);
        tvW = findViewById(R.id.w);
        tvLat = findViewById(R.id.lat);
        tvLng = findViewById(R.id.lng);
        tvHeight = findViewById(R.id.height);

        start = findViewById(R.id.start);
        stopp = findViewById(R.id.stopp);
        connectSocket(DEVICE_URL);
//        connectSocket(EMULATOR_URL);
        socket.on(Manager.EVENT_TRANSPORT, onEventTransport);
        socket.on(ANIMATE_VIEWER_EVENT, onAnimateViewer);
        IntentFilter filter = new IntentFilter("OrientationUpdate");
        filter.addAction("LocationUpdate");
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,filter);
    }


    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()){
                case "OrientationUpdate":
                    float[] quaternion = intent.getFloatArrayExtra("quaternion");
                    setAndEmitOrientation(quaternion);
                    break;
                case "LocationUpdate":
                    Log.d(TAG, "got location");
                    Bundle b = intent.getExtras();
                    Location location = (Location) b.get(LocationManager.KEY_LOCATION_CHANGED);
                    setAndEmitLocation(location);
                default:
                    break;
            }

        }

    };

    private void setAndEmitOrientation(float[] quat) {
        tvX.setText("x: " + String.valueOf(quat[1]));
        tvY.setText("y: " + String.valueOf(quat[2]));
        tvZ.setText("z: " + String.valueOf(quat[3]));
        tvW.setText("w: " + String.valueOf(quat[0]));
        JSONObject payload = new JSONObject();
        try {
            payload.put("w", quat[0]);
            payload.put("x", quat[1]);
            payload.put("y", quat[2]);
            payload.put("z", quat[3]);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        socket.emit("sensor changed", payload);

    }

    private void setAndEmitLocation(Location location) {
        double lat = location.getLatitude();
        double lng = location.getLongitude();
        double altitude = location.getAltitude();
        double accuracy = location.getAccuracy();
        tvLat.setText("lat: " + String.valueOf(lat));
        tvLng.setText("lng: " + String.valueOf(lng));
        tvHeight.setText("height: " + String.valueOf(altitude));
        JSONObject payload = new JSONObject();
        try {
            payload.put("lat", lat);
            payload.put("lng", lng);
            payload.put("height", altitude);
            payload.put("accuracy", accuracy);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        socket.emit("location changed", payload);
    }

    public void connectSocket(String url) {
        {
            try {
                socket = IO.socket(url);
            } catch (URISyntaxException e) {
                Log.d("tag", e.getMessage());
            }
        }
        socket.connect();
    }

    private Emitter.Listener onEventTransport = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Transport transport = (Transport) args[0];
            transport.on(Transport.EVENT_ERROR, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Exception e = (Exception) args[0];
                    Log.e("tag", "Transport error " + e);
                    e.printStackTrace();
                    e.getCause().printStackTrace();
                }
            });
        }
    };

    private Emitter.Listener onAnimateViewer = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
                runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    boolean willAnimate = (boolean)args[0];
                    if(willAnimate){
                        startGeoposeService(null);
                    } else {
                        stopGeoposeService(null);
                    }
                }
            });
        }
    };


    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mMessageReceiver);
        socket.disconnect();

    }

    public void startGeoposeService(View view) {
        Toast.makeText(this, "Clicked on Start", Toast.LENGTH_SHORT).show();
        Intent sensorService = new Intent(this, SensorService.class);
        Intent locationService = new Intent(this, LocationService.class);
        socket.emit(START_MOBILE_CLIENT_EVENT, true);
        startService(sensorService);
        startService(locationService);
    }

    public void stopGeoposeService(View view) {
        Toast.makeText(this, "Clicked on Stop", Toast.LENGTH_SHORT).show();
        Intent sensorService = new Intent(this, SensorService.class);
        Intent locationService = new Intent(this, LocationService.class);
        socket.emit(START_MOBILE_CLIENT_EVENT, false);
        stopService(sensorService);
        stopService(locationService);
    }

    /**
     * Checks the dynamically-controlled permissions and requests missing permissions from end user.
     * Source: HERE MAPS SDK Documentation.
     */
    protected void checkPermissions() {
        final List<String> missingPermissions = new ArrayList<String>();
        // check all required dynamic permissions
        for (final String permission : REQUIRED_SDK_PERMISSIONS) {
            final int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            // request all missing permissions
            final String[] permissions = missingPermissions
                    .toArray(new String[missingPermissions.size()]);
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_ASK_PERMISSIONS);
        } else {
            final int[] grantResults = new int[REQUIRED_SDK_PERMISSIONS.length];
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
            onRequestPermissionsResult(REQUEST_CODE_ASK_PERMISSIONS, REQUIRED_SDK_PERMISSIONS,
                    grantResults);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_PERMISSIONS:
                for (int index = permissions.length - 1; index >= 0; --index) {
                    if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                        // exit the app if one permission is not granted
                        Toast.makeText(this, "Required permission '" + permissions[index]
                                + "' not granted, exiting", Toast.LENGTH_LONG).show();
                        finish();
                        return;
                    }
                }
                // all permissions were granted -> do stuff
                break;
        }
    }

}
