package com.h804562.geopose_client;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.engineio.client.Transport;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Manager;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

public class MainActivity extends AppCompatActivity {

    private TextView tv1;
    private TextView tv2;
    private TextView tv3;
    private TextView tv4;

    private static final String ANIMATE_VIEWER_EVENT = "animate viewer";
    private static final String START_MOBILE_CLIENT_EVENT = "start mobile client";
    private static final String EMULATOR_URL = "http://10.0.2.2:3000";
    private static final String DEVICE_URL = "http://192.168.6.152:3000";
    private final String TAG = "com.h804562.geopose_client";

    private Button start;
    private Button stopp;

    private Socket socket;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tv1 = findViewById(R.id.x);
        tv2 = findViewById(R.id.y);
        tv3 = findViewById(R.id.z);
        tv4 = findViewById(R.id.w);

        start = findViewById(R.id.start);
        stopp = findViewById(R.id.stopp);

        connectSocket();
        socket.on(Manager.EVENT_TRANSPORT, onEventTransport);
        socket.on(ANIMATE_VIEWER_EVENT, onAnimateViewer);
        LocalBroadcastManager.getInstance(this).registerReceiver(mMessageReceiver,
                new IntentFilter("OrientationUpdate"));
//        Intent intent = new Intent(this, SensorService.class);
//        startService(intent);
    }

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            float[] quat = intent.getFloatArrayExtra("quaternion");
            tv1.setText(String.valueOf(quat[1]));
            tv2.setText(String.valueOf(quat[2]));
            tv3.setText(String.valueOf(quat[3]));
            tv4.setText(String.valueOf(quat[0]));

            JSONObject payload = new JSONObject();
            try {
                payload.put("w", quat[0]);
                payload.put("x", quat[1]);
                payload.put("y", quat[2]);
                payload.put("z", quat[3]);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if(socket.connected()){
                socket.emit("sensor changed", payload);
            } else {
                socket.disconnect();
                socket.connect();
            }
        }

    };

    public void connectSocket() {
        {
            try {
//                socket = IO.socket(EMULATOR_URL);
                socket = IO.socket(DEVICE_URL);
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
                        startSensorService(null);
                    } else {
                        stopSensorService(null);
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

    public void startSensorService(View view) {
        Toast.makeText(this, "Clicked on Start", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SensorService.class);
        socket.emit(START_MOBILE_CLIENT_EVENT, true);
        startService(intent);
    }

    public void stopSensorService(View view) {
        Toast.makeText(this, "Clicked on Stop", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, SensorService.class);
        socket.emit(START_MOBILE_CLIENT_EVENT, false);
        stopService(intent);
    }

}
