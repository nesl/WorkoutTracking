package edu.ucla.nesl.android.workoutbenchmark;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Date;

public class BGSigMotionService extends Service {
    private static final String TAG = "Wear:BGSigMoService";
    private SensorManager mSensorManager;
    private TriggerEventListener mTriggerEventListener;
    private Sensor sigMoSensor;
    private PrintWriter monitorLogger;
    private boolean flagThreadRun = true;
    private static boolean motionFlag = false;
    private static PowerManager.WakeLock wakeLock;

    public BGSigMotionService() {

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sigmo_akelook");
        wakeLock.acquire();

        String dateStr = BGDataCollectionService.formatForFile.format(new Date());
        String loggerFileName = "/sdcard/battery_" + dateStr + ".txt";

        try {
            monitorLogger = new PrintWriter(loggerFileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        startMeasurement();
        monitorThread.start();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private void startMeasurement() {
        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));
        sigMoSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);
        motionFlag = true;
        mTriggerEventListener = new TriggerEventListener() {
            @Override
            public void onTrigger(TriggerEvent event) {
                Log.i(TAG, event.values[0] + "");
                if (motionFlag) {
                    mSensorManager.requestTriggerSensor(mTriggerEventListener, sigMoSensor);
                }
            }
        };

        mSensorManager.requestTriggerSensor(mTriggerEventListener, sigMoSensor);
        Log.i(TAG, "Registered sigmo");
    }

    private void stopMeasurement() {
        motionFlag = false;
        wakeLock.release();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopMeasurement();
        flagThreadRun = false;
    }

    private Thread monitorThread = new Thread() {
        @Override
        public void run() {
            while (flagThreadRun) {
                long now = System.currentTimeMillis();
                Intent batteryIntent = getApplicationContext().registerReceiver(null,
                        new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int batteryLevel = batteryIntent.getIntExtra("level", -1);

                monitorLogger.write(String.format("%d,%d\n", now, batteryLevel));
                monitorLogger.flush();
                Log.i(TAG, "Battery level=" + batteryLevel);

                try {
                    sleep(60000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    };
}
