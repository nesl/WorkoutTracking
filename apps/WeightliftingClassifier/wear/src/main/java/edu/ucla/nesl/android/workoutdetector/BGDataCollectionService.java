package edu.ucla.nesl.android.workoutdetector;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.ArrayList;

public class BGDataCollectionService extends Service implements SensorEventListener {
    public static final String ACC_1K_ACTION = "acc_1k";
    private static final String TAG = "BGService";
    private final IBinder mBinder = new MyBinder();

    private final String DUMMY_GRAV_FILE_PATH = "/sdcard/weightlifting_dummy_grav.csv";

    // Make everything static so that each bind will get
    // the same SensorManager, PrintWriter, and Wakelock
    private static SensorManager mSensorManager;
    private static ArrayList<Sensor> sensors = null;

    private static long c[] = new long[20];

    private static Vibrator v;
    private static BGDataCollectionService mContext;

    private static PowerManager.WakeLock wakeLock;

    private float[][] dummy_grav;
    private int dummy_access_idx;
    private IWeightliftingDetector weightliftingDetector;

    private PrintWriter monitorLogger;
    private static AlarmReceiver alarm = new AlarmReceiver();

    private TimeString timeString = new TimeString();

    private float grav_x_sum = 0f;
    private float grav_y_sum = 0f;
    private float grav_z_sum = 0f;


    public BGDataCollectionService() {
        // load dummy gravity data
        ArrayList<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(new File(DUMMY_GRAV_FILE_PATH)))) {
            String line;
            while ((line = br.readLine()) != null)
                lines.add(line);
        } catch (Exception e) {
            e.printStackTrace();
        }
        dummy_grav = new float[lines.size()][3];
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String[] tokens = line.split(",");
            for (int j = 0; j < 3; j++)
                dummy_grav[i][j] = Float.parseFloat(tokens[j]);
        }
        dummy_access_idx = 0;

        // declare weightlifting detector by instantiating different type of object.
        //weightliftingDetector = new AutocorredRepDetector();
        weightliftingDetector = new SymmetricBasedRepDetector();
        weightliftingDetector.reset();

        // data logger
        String loggerFileName = "/sdcard/wear_energy_measurement_" + timeString.currentTimeForFile() + ".txt";
        String sensorDataFileName = loggerFileName + ".data";

        try {
            monitorLogger = new PrintWriter(loggerFileName);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        mContext = this;
        return START_STICKY;
    }


    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        // to make sure the sensory values are not being skipped
        grav_x_sum += sensorEvent.values[0];
        grav_y_sum += sensorEvent.values[1];
        grav_z_sum += sensorEvent.values[2];

        // for recording the time offset
        int sensorType = sensorEvent.sensor.getType();
        c[sensorType]++;
        if (c[sensorType] >= 1000) {
            if (sensorType == Sensor.TYPE_GRAVITY) {
                Intent intent = new Intent();
                intent.setAction(ACC_1K_ACTION);
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
                Log.i(TAG, "Sending broadcast...");
            }
            Log.i(TAG, "type " + sensorType + " reached 1000 values.");
            c[sensorType] = 0;

            long now = System.currentTimeMillis();
            Intent batteryIntent = getApplicationContext().registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            int batteryLevel = batteryIntent.getIntExtra("level", -1);

            monitorLogger.write(String.format("%d,%d,%f,%f,%f\n", now, batteryLevel,
                    grav_x_sum, grav_y_sum, grav_z_sum));
            monitorLogger.flush();
        }


        // process. there can be two sources to get sensor data. the first place is from physical
        // sensor itself. the second one, which we try to simplify the question, comes from the
        // dummy data. instead of using a variable to control the source, we comment out of the
        // code block for performance.

        // source 1: from sensor
        //weightliftingDetector.input(sensorEvent.values[0], sensorEvent.values[1], sensorEvent.values[2]);

        // source 2: from dummy data
        weightliftingDetector.input(dummy_grav[dummy_access_idx][0],
                dummy_grav[dummy_access_idx][1], dummy_grav[dummy_access_idx][2]);
        dummy_access_idx = (dummy_access_idx + 1) % dummy_grav.length;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public class MyBinder extends Binder {
        public BGDataCollectionService getService() {
            return BGDataCollectionService.this;
        }
    }

    public static void startRecording(String timestring) {
        Log.i(TAG, "start recording");

        PowerManager pm = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock( PowerManager.PARTIAL_WAKE_LOCK, "MyWakelook");
        wakeLock.acquire();

        sensors = new ArrayList<>();

        mSensorManager = ((SensorManager) mContext.getSystemService(SENSOR_SERVICE));
        sensors.add(mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY));

        v = (Vibrator) mContext.getSystemService(Context.VIBRATOR_SERVICE);

        //String folder_prefix = "/sdcard/wear_" + timestring + "_";

        // register sensors
        registerAllSensors();
        v.vibrate(100);

        alarm.setAlarm(mContext);
    }

    public static void stopRecording() {
        Log.i(TAG, "stop recording");

        unregisterAllSensors();
        mSensorManager = null;

        sensors.clear();
        sensors = null;

        if (wakeLock != null) {
            wakeLock.release();
        }

        v.vibrate(300);
    }

    private static void registerAllSensors() {
        for (Sensor sensor: sensors) {
            mSensorManager.registerListener(mContext, sensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    private static void unregisterAllSensors() {
        for (Sensor sensor: sensors) {
            mSensorManager.unregisterListener(mContext, sensor);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind");
        return mBinder;
    }

    @Override
    public void onDestroy () {
        super.onDestroy();
        if (mSensorManager != null) {
            stopRecording();
        }

        monitorLogger.close();
    }
}
