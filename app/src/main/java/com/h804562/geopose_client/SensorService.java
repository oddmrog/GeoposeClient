package com.h804562.geopose_client;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;



import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.util.Date;


public class SensorService extends Service implements SensorEventListener {

    private final String TAG = "SENSOR_SERVICE";

    private static final long TIMEOUT = 50;

    private HandlerThread handlerThread;
    private Handler serviceHandler;
    private Looper serviceLooper;
    private boolean isRunning;
    private SensorManager sensorManager;
    private Sensor rotationSensor;
    private RunningStat[] runningStat;

    private long currentTime;
    private long previousTime;
    private long timer;

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            // convert timestampt to milliseconds
            long currentTime = (new Date()).getTime()
                    + (event.timestamp - System.nanoTime()) / 1000000L;

            if(previousTime == 0){
                previousTime = currentTime;
            }


            runningStat[0].push(event.values[0]);
            runningStat[1].push(event.values[1]);
            runningStat[2].push(event.values[2]);
            runningStat[3].push(event.values[3]);

            if(currentTime - previousTime > TIMEOUT){
                float xMean = (float)runningStat[0].mean();
                float yMean = (float)runningStat[1].mean();
                float zMean = (float)runningStat[2].mean();

                float[] quaternion = new float[4];
                float[] rv = new float[]{xMean, yMean, zMean};

                SensorManager.getQuaternionFromVector(quaternion, rv);
                Intent intent = new Intent("OrientationUpdate");
                intent.putExtra("quaternion", quaternion);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);

                runningStat[0].clear();
                runningStat[1].clear();
                runningStat[2].clear();
                runningStat[3].clear();
                previousTime = currentTime;
            }


        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }



    @Override
    public void onCreate() {
        handlerThread = new HandlerThread("Sensor thread", Process.THREAD_PRIORITY_DEFAULT);
        handlerThread.start();
        serviceLooper = handlerThread.getLooper();
        serviceHandler = new Handler(serviceLooper);
        initRunningStat();

//        serviceHandler.post(runningStat);
        sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_UI, serviceHandler);

        Toast.makeText(this, "on created", Toast.LENGTH_SHORT).show();

    }

    private void initRunningStat() {
        isRunning = true;
        if (runningStat == null) {
            runningStat = new RunningStat[4];
            runningStat[0] = new RunningStat(4);
            runningStat[1] = new RunningStat(4);
            runningStat[2] = new RunningStat(4);
            runningStat[3] = new RunningStat(4);
        } else {
            runningStat[0].clear();
            runningStat[1].clear();
            runningStat[2].clear();
            runningStat[3].clear();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sensorManager.unregisterListener(this);
        serviceHandler.removeCallbacksAndMessages(null);
        serviceHandler.getLooper().quitSafely();
        Toast.makeText(this, "Destroy", Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Service has started!");
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
        return START_STICKY;
    }



    private Runnable runningStats = new Runnable() {
        @Override
        public void run() {
            while(isRunning){

                RunningStat[] stats = new RunningStat[0];
                try {
                        stats = SensorUtils.sensorStats(getApplicationContext(), Sensor.TYPE_ROTATION_VECTOR, TIMEOUT);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                float xMean = (float)stats[0].mean();
                float yMean = (float)stats[1].mean();
                float zMean = (float)stats[2].mean();
                float wMean = (float)stats[3].mean();

                float[] quaternion = new float[4];
                float[] rotationVector = new float[]{xMean, yMean, zMean};
                double[] quat = new double[] {xMean, yMean, zMean, wMean};
                SensorManager.getQuaternionFromVector(quaternion, rotationVector);

                JSONObject data = new JSONObject();
                try {
                        data.put("w", quat[0]);
                        data.put("x", quat[1]);
                        data.put("y", quat[2]);
                        data.put("z", quat[3]);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                Intent intent = new Intent("OrientationUpdate");
                intent.putExtra("data", quat );
                intent.putExtra("quaternion", quaternion);
                LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
            }
        }

    };


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


}

