package edu.ucla.nesl.android.workoutdetector;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

/**
 * Created by cgshen on 11/20/15.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private final static String TAG = "Wear/InfAlarmReceiver";
    private final static int ALARM_INTERVAL = 1000 * 60; // 1 min

//    private final static boolean logBattery = false;
//    private final static boolean featureCalc = true;
//    private final static boolean classifyCalc = false;
//
//    private SensorManager mSensorManager;
//    private TransportationModeListener mListener;
//    private DataMapClient mClient;
//    private static int numThreads;
//    private static int resCount = 0;
//
//    public static double locSpeed;
//    public static double locAccuracy;

//    private static final Object lock = new Object();
//    public static final Object locLock = new Object();

    @Override
    public void onReceive(Context context, Intent intent) {
        PowerManager mPowerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "inf_wakelock");
        wl.acquire();

//        mClient = DataMapClient.getInstance(context);
//
//        Log.i(TAG, "InferenceAlarmReceiver received, mType=" + mType + ", feature=" + featureCalc + ", classify=" + classifyCalc);
//
//        if (logBattery) {
//            try {
//                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
//                Intent batteryStatus = context.registerReceiver(null, ifilter);
//                int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
//                int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
//                float batteryPct = level / (float)scale;
//                Log.d(TAG, "Battery level = " + batteryPct);
//
//                BufferedWriter outputBattery = new BufferedWriter(new FileWriter("/sdcard/wearcontext/battery_normal_use_" + timestamp + ".txt", true));
//
//                if (batteryPct > 0.2) {
//                    outputBattery.append(String.valueOf(System.currentTimeMillis()) + "," + String.valueOf(batteryPct) + "\n");
//                    outputBattery.flush();
//
//                    Log.d(TAG, "Battery level = " + batteryPct);
//                }
//                else {
//                    outputBattery.flush();
//                    outputBattery.close();
//                }
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//            IntentFilter ifilter1 = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
//            Intent batteryStatus1 = context.registerReceiver(null, ifilter1);
//
//            // Are we charging / charged?
//            int status = batteryStatus1.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
//            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
//                    status == BatteryManager.BATTERY_STATUS_FULL;
//
//            Log.i("PowerConnectionReceiver", "isCharging==" + isCharging);
//        }
//
//        if (mType == InferenceType.NoInference) {
//            int j = 0;
//            for (int i = 0; i < 60; i++) {
//                j++;
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//        }
//        else if (mType == InferenceType.DataTransmission) {
//            // Test sending data from watch to phone
//            Log.i(TAG, "Sending data via BLE...");
//
//            for (int i = 0; i < 30; i++) {
//                long tic = System.currentTimeMillis();
//                for (int ii = 0; ii < 10; ii++) {
//                    mClient.sendSensorData(System.currentTimeMillis(), BYTE_DATA_1K);
//                }
//                long tac = System.currentTimeMillis();
//                Log.i(TAG, "Sending data finished, time=" + (tac - tic) + "ms");
//
//                try {
//                    Thread.sleep(1000);
//                } catch (InterruptedException e) {
//                    e.printStackTrace();
//                }
//            }
//
//            Log.i(TAG, "Sending data ALL finished.");
//        }
//        else if (mType == InferenceType.WearAcc || mType == InferenceType.WearAccGPS || mType == InferenceType.WearPhoneAcc) {
//            // Start inference using only acc for 1 minute (10s for test)
//            mSensorManager = ((SensorManager) context.getSystemService(Context.SENSOR_SERVICE));
//            Sensor accelerometerSensor = mSensorManager.getDefaultSensor(SENS_ACCELEROMETER);
//
//            // Register the listener
//            if (mSensorManager != null) {
//                if (accelerometerSensor != null) {
//                    mListener = new TransportationModeListener(mType);
//                    mSensorManager.registerListener(mListener, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);
//
//
//                } else {
//                    Log.w(TAG, "No Accelerometer found");
//                }
//            }
//
//            // Stop after 1 minute (10s for test)
//            Handler mHandler = new Handler();
//            mHandler.postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    mSensorManager.unregisterListener(mListener);
//                    mSensorManager = null;
//                    Log.i(TAG, "InferenceAlarmReceiver execution finished.");
//                }
//            }, SENSING_PERIOD);
//        }

        wl.release();

    }

    public void setAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent i = new Intent(context, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, i, 0);
        am.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(), ALARM_INTERVAL, pi);
        Log.i(TAG, "Alarm set.");
    }

    public void cancelAlarm(Context context) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent sender = PendingIntent.getBroadcast(context, 0, intent, 0);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.cancel(sender);
        Log.i(TAG, "Alarm cancelled.");
    }
}