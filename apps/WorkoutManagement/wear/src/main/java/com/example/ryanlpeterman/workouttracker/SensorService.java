package com.example.ryanlpeterman.workouttracker;

/**
 * Created by ryanlpeterman on 7/29/15.
 */
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Vibrator;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.text.SimpleDateFormat;
import java.util.Calendar;


public class SensorService extends Service implements SensorEventListener, DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener   {
    // for use when debug logging
    private static final String TAG = MainActivity.class.getName();

    private final static int SENS_ACCELEROMETER = Sensor.TYPE_ACCELEROMETER;
    private final static int SENS_GYROSCOPE = Sensor.TYPE_GYROSCOPE;
    private final static int SENS_STEP_COUNTER = Sensor.TYPE_STEP_COUNTER;

    SensorManager mSensorManager;

    private PowerManager.WakeLock wakeLock;

    // TODO: remove the following two instance variables
    // For tracking
    private static int numThreads;
    Thread liftThread;

    // Data Interface
    private static final int ACCEL_SAMPLE_FREQ = 50;       // 50 Hz
    private static final int LIFT_WINDOW = 1;              // 1 second
    private DataWindow liftWindow = new DataWindow(LIFT_WINDOW, ACCEL_SAMPLE_FREQ);

    // Used to track cumulative time
    private int deadlift_time = 0;
    private int bench_time = 0;
    private int squat_time = 0;
    private int nonexercise_time = 0;
    private int lying_time = 0;
    private int sitting_time = 0;
    private int walking_time = 0;
    private int running_time = 0;
    private int NUM_LABELS = 8;

    // Used to vibrate the wearable
    private Vibrator vibrator;

    private BroadcastReceiver mBroadcastReceiver;

    public class PowerConnectionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL;

            //Checks to see if phone is charging, sends measurements to phone if so
            if (isCharging)
                //Log.i("Charging", "yes");
                sendMeasurment();

        }
    }

    final String COUNT_KEY = "com.example.key.count";
    private GoogleApiClient googleApiClient;
    private int count = 0;

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        mBroadcastReceiver = new PowerConnectionReceiver();
        registerReceiver(mBroadcastReceiver, ifilter);

        googleApiClient = new GoogleApiClient.Builder(this)
                        .addApi(Wearable.API)
                        .addConnectionCallbacks(this)
                        .addOnConnectionFailedListener(this)
                        .build();

        Notification.Builder builder = new Notification.Builder(this);
        builder.setContentTitle("Sensor Dashboard");
        builder.setContentText("Collecting sensor data..");
        // TODO: Make Icon
        // builder.setSmallIcon(R.drawable.ic_launcher);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        // unique ID (1) and notification
        startForeground(1, builder.build());

        startMeasurement();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBroadcastReceiver);

        stopMeasurement();
    }

    @Override
    public void onConnected(Bundle bundle) {
        Wearable.DataApi.addListener(googleApiClient, this);
    }

    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.getErrorCode() == ConnectionResult.API_UNAVAILABLE) {
            Log.d(TAG,"Wearable API is unavailable");
        }
    }

    public void onConnectionSuspended(int cause) { }

    public void onDataChanged(DataEventBuffer dataEvents) {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected void startMeasurement() {
        Log.d(TAG, "start measurement in wear: SensorService");

       // vibrator.vibrate(1000);

        // Wakelock
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorCollector");
        wakeLock.acquire();

        mSensorManager = ((SensorManager) getSystemService(SENSOR_SERVICE));

        Sensor accelerometerSensor = mSensorManager.getDefaultSensor(SENS_ACCELEROMETER);
        Sensor gyroscopeSensor = mSensorManager.getDefaultSensor(SENS_GYROSCOPE);
        Sensor stepCounterSensor = mSensorManager.getDefaultSensor(SENS_STEP_COUNTER);

        // Register the listener
        if (mSensorManager != null) {
            if (accelerometerSensor != null) {
                mSensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);
            } else {
                Log.w(TAG, "No Accel found");
            }
            if (gyroscopeSensor != null) {
                mSensorManager.registerListener(this, gyroscopeSensor, SensorManager.SENSOR_DELAY_FASTEST);
            } else {
                Log.w(TAG, "No Gyro Sensor found");
            }
            if (stepCounterSensor != null) {
                mSensorManager.registerListener(this, stepCounterSensor, SensorManager.SENSOR_DELAY_FASTEST);
            } else {
                Log.d(TAG, "No Step Count found");
            }
        }
    }


    public void sendMeasurment() {
        Log.i("Called", " ");
        PutDataMapRequest putDataMapReq = PutDataMapRequest.create("/count");
        putDataMapReq.getDataMap().putInt(COUNT_KEY, count++);
        //putDataMapReq.getDataMap().putDataMapArrayList(key, value);
        PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
        PendingResult<DataApi.DataItemResult> pendingResult =
                Wearable.DataApi.putDataItem(googleApiClient, putDataReq);

        // Push cumulative classification data to the phone
        //Send formatted date, labels[], and get_time to phone

        // Access to database
        FeedReaderDbHelper mDbHelper = new FeedReaderDbHelper(getApplicationContext());

        // Allows writing
        SQLiteDatabase db = mDbHelper.getWritableDatabase();

        // Make new map of values
        ContentValues values = new ContentValues();

        Calendar cal = Calendar.getInstance();
        SimpleDateFormat df = new SimpleDateFormat("dd MMM yyyy");
        String formattedDate = df.format(cal.getTime());

        String[] labels = {"bench", "deadlift", "squat", "lying", "nonexercise", "sitting",
                           "running", "walking"};

        for(int i = 0; i < NUM_LABELS; i++) {
            values.put(FeedUserInfo.FeedEntry.COLUMN_DATE, formattedDate);
            values.put(FeedUserInfo.FeedEntry.COLUMN_ACTIVITY, labels[i]);
            values.put(FeedUserInfo.FeedEntry.COLUMN_TOTAL_TIME, get_time(labels[i]));
        }

        //Insert row and store key value of row
        db.insert(FeedUserInfo.FeedEntry.TABLE_NAME, null, values);
    }

    private void stopMeasurement() {
        vibrator.vibrate(200);

        mSensorManager.unregisterListener(this);
        mSensorManager = null;

        wakeLock.release();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        int type = event.sensor.getType();
        long timestamp = event.timestamp;

        if (type == SENS_ACCELEROMETER) {

            // if sample could not be added because buffer is full
            if(!liftWindow.addSample(event)){
                // Process Data

                liftThread = new Thread(
                        new Classification(liftWindow.getData(), liftWindow.getCount(), this));
                liftThread.start();
                numThreads++;

                liftWindow.emptyBuffer();
            }
        }
    }

    public void set_time(String activity) {
        // TODO: rename method name: increase_rep()
        switch (activity) {
            case "deadlift":
                deadlift_time++;
                break;
            case "bench":
                bench_time++;
                break;
            case "squat":
                squat_time++;
                break;
            case "nonexercise":
                nonexercise_time++;
                break;
            case "lying":
                lying_time++;
                break;
            case "sitting":
                sitting_time++;
                break;
            case "running":
                running_time++;
                break;
            case "walking":
                walking_time++;
                break;
            default:
                nonexercise_time++;
                break;
        }
    }

    public int get_time(String activity) {
        switch (activity) {
            case "deadlift":
                return deadlift_time;
            case "bench":
                return bench_time;
            case "squat":
                return squat_time;
            case "nonexercise":
                return nonexercise_time;
            case "lying":
                return lying_time;
            case "sitting":
                return sitting_time;
            case "running":
                return running_time;
            case "walking":
                return walking_time;
            default:
                return nonexercise_time;
        }
    }


    // Class to Encapsulate Feature Calculation once Data is collected from sensors
    private class Classification implements Runnable {
        private SensorData[] mData;
        private int mCount;        // num of samples in a window
        private Context mContext;

        public Classification(SensorData[] data, int numCount, Context context){
            // TODO: why do you need to clone entire array? If it is necessary, consider clone()
            mCount = numCount;
            mContext = context;

            // Create a copy of the data to process
            mData = new SensorData[mCount];

            for(int i = 0; i < mCount; i++){
                mData[i] = new SensorData();
            }

            for(int i = 0; i < mCount; i++){
                mData[i].x_acc = data[i].x_acc;
                mData[i].y_acc = data[i].y_acc;
                mData[i].z_acc = data[i].z_acc;
            }

        }

        public void sort( int[] a) {
            // TODO: leave some comments to state the purpose of this method
            int i, m = a[0], exp = 1, n = a.length;
            int[] b = new int[10];
            for (i = 1; i < n; i++)
                if (a[i] > m)
                    m = a[i];
            while (m / exp > 0)
            {
                int[] bucket = new int[10];

                for (i = 0; i < n; i++)
                    bucket[(a[i] / exp) % 10]++;
                for (i = 1; i < 10; i++)
                    bucket[i] += bucket[i - 1];
                for (i = n - 1; i >= 0; i--)
                    b[--bucket[(a[i] / exp) % 10]] = a[i];
                for (i = 0; i < n; i++)
                    a[i] = b[i];
                exp *= 10;
            }
        }

        @Override
        public void run() {
            double sum = 0.0;
            double x_mean = 0.0;
            double y_mean = 0.0;
            double z_mean = 0.0;
            // If we need more accuracy can add tot_var, but will be a lot more processing

            // Calculate Features: x,y,z - axes mean
            for(int i = 0; i < mCount; i++){
                sum += mData[i].x_acc;
            }

            x_mean = sum / mCount;
            sum = 0.0;

            for(int i = 0; i < mCount; i++){
                sum += mData[i].y_acc;
            }

            y_mean = sum / mCount;
            sum = 0.0;

            for(int i = 0; i < mCount; i++){
                sum += mData[i].z_acc;
            }

            z_mean = sum / mCount;

            String activity = "";

            // Use Weightlifting Decision Tree to determine the type of exercise
            // If combined dt returns true then weightlifting is classified
            if(combined_dt(x_mean, y_mean, z_mean)) {
                activity = lifting_dt(x_mean, y_mean, z_mean);
            }
            // Use Cardio Decision Tree to determine which type of cardio the user is engaging in
            else{
                activity = cardio_dt(x_mean, y_mean, z_mean);
            }

            // Add to running total of time in activity
            set_time(activity);

            // Broadcast message to main activity for display
            Intent messageIntent = new Intent();
            messageIntent.setAction(Intent.ACTION_SEND);
            messageIntent.putExtra("message", activity);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(messageIntent);
        }

        public String lifting_dt(double x_mean, double y_mean, double z_mean) {

            String activity = "";

            if ( y_mean <= -4.52604341507 ) {
                if ( z_mean <= -2.93227291107 ) {
                    if ( x_mean <= -6.47807598114 ) {
                        activity = "deadlift";
                    } else {
                        activity = "squat";
                    }
                } else {
                    activity = "squat";
                }
            } else {
                if ( z_mean <= 3.64232158661 ) {
                    if ( x_mean <= 3.28544902802 ) {
                        if ( y_mean <= -1.63899207115 ) {
                            if ( z_mean <= 1.08966827393 ) {
                                if ( y_mean <= -3.94071722031 ) {
                                    if ( y_mean <= -4.33652687073 ) {
                                        activity = "deadlift";
                                    } else {
                                        activity = "squat";
                                    }
                                } else {
                                    activity = "deadlift";
                                }
                            } else {
                                activity = "squat";
                            }
                        } else {
                            activity = "squat";
                        }
                    } else {
                        activity = "bench";
                    }
                } else {
                    if ( y_mean <= -2.76127719879 ) {
                        activity = "nonexercise";
                    } else {
                        activity = "squat";
                    }
                }
            }

            return activity;
        }

        public String cardio_dt(double x_mean, double y_mean, double z_mean) {

            String activity = "";

            if ( x_mean <= -5.1505484581 ) {
                activity = "walking";
            } else {
                if ( y_mean <= -9.14158725739 ) {
                    if ( x_mean <= -2.45381546021 ) {
                        activity = "running";
                    } else {
                        activity = "walking";
                    }
                } else {
                    if ( z_mean <= 7.19430112839 ) {
                        if ( x_mean <= -1.61807191372 ) {
                            if ( z_mean <= 3.69302034378 ) {
                                if ( y_mean <= -1.67251038551 ) {
                                    activity = "walking";
                                } else {
                                    activity = "running";
                                }
                            } else {
                                activity = "sitting";
                            }
                        } else {
                            if ( x_mean <= 8.29422092438 ) {
                                activity = "lying";
                            } else {
                                activity = "sitting";
                            }
                        }
                    } else {
                        if ( x_mean <= 2.77542757988 ) {
                            if ( z_mean <= 8.65986633301 ) {
                                if ( y_mean <= -3.91545724869 ) {
                                    activity = "sitting";
                                } else {
                                    if ( y_mean <= -1.87652683258 ) {
                                        activity = "lying";
                                    } else {
                                        activity = "sitting";
                                    }
                                }
                            } else {
                                if ( x_mean <= 1.96243023872 ) {
                                    if ( y_mean <= -0.115493007004 ) {
                                        activity = "sitting";
                                    } else {
                                        activity = "lying";
                                    }
                                } else {
                                    activity = "lying";
                                }
                            }
                        } else {
                            activity = "lying";
                        }
                    }
                }
            }
            return activity;
        }

        public boolean combined_dt(double x_mean, double y_mean, double z_mean) {

            // Variable to store the result of the classification
            // Switched to boolean for efficiency

            // Exported Decision Tree from Sci-kit Learn
            if ( x_mean <= 5.68539714813 ) {
                if ( y_mean <= -4.49912261963 ) {
                    if ( x_mean <= -5.39485359192 ) {
                        if ( z_mean <= 0.422652959824 ) {
                            if ( z_mean <= -2.55716943741 ) {
                                return true;
                            } else {
                                if ( z_mean <= 0.259501427412 ) {
                                    if ( y_mean <= -5.8627281189 ) {
                                        if ( y_mean <= -6.06389379501 ) {
                                            if ( z_mean <= -0.534971237183 ) {
                                                return false;
                                            } else {
                                                if ( y_mean <= -6.32211351395 ) {
                                                    return true;
                                                } else {
                                                    return false;
                                                }
                                            }
                                        } else {
                                            return true;
                                        }
                                    } else {
                                        return false;
                                    }
                                } else {
                                    if ( x_mean <= -7.7662820816 ) {
                                        return true;
                                    } else {
                                        return false;
                                    }
                                }
                            }
                        } else {
                            if ( x_mean <= -9.31362533569 ) {
                                return false;
                            } else {
                                if ( x_mean <= -7.79961538315 ) {
                                    if ( y_mean <= -4.52743244171 ) {
                                        if ( z_mean <= 1.07437586784 ) {
                                            if ( z_mean <= 1.05855536461 ) {
                                                if ( x_mean <= -8.33265972137 ) {
                                                    return true;
                                                } else {
                                                    if ( x_mean <= -8.15695762634 ) {
                                                        return false;
                                                    } else {
                                                        return true;
                                                    }
                                                }
                                            } else {
                                                return false;
                                            }
                                        } else {
                                            if ( y_mean <= -5.4870467186 ) {
                                                return false;
                                            } else {
                                                if ( y_mean <= -4.68166351318 ) {
                                                    return true;
                                                } else {
                                                    if ( y_mean <= -4.68118810654 ) {
                                                        return false;
                                                    } else {
                                                        return true;
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        return false;
                                    }
                                } else {
                                    if ( y_mean <= -5.28322410583 ) {
                                        if ( z_mean <= 2.03569626808 ) {
                                            if ( z_mean <= 0.978359699249 ) {
                                                return true;
                                            } else {
                                                if ( x_mean <= -7.44812393188 ) {
                                                    return true;
                                                } else {
                                                    return false;
                                                }
                                            }
                                        } else {
                                            return true;
                                        }
                                    } else {
                                        return false;
                                    }
                                }
                            }
                        }
                    } else {
                        if ( x_mean <= 3.62226963043 ) {
                            if ( z_mean <= -7.00912952423 ) {
                                return true;
                            } else {
                                if ( y_mean <= -8.85764694214 ) {
                                    return false;
                                } else {
                                    if ( z_mean <= 3.14163923264 ) {
                                        if ( z_mean <= 2.63422870636 ) {
                                            if ( x_mean <= -3.19677138329 ) {
                                                if ( z_mean <= -1.68033313751 ) {
                                                    if ( y_mean <= -8.64511966705 ) {
                                                        return false;
                                                    } else {
                                                        if ( y_mean <= -8.13242149353 ) {
                                                            return true;
                                                        } else {
                                                            if ( y_mean <= -7.13172912598 ) {
                                                                return false;
                                                            } else {
                                                                return true;
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    if ( x_mean <= -3.75184011459 ) {
                                                        if ( y_mean <= -8.10316848755 ) {
                                                            if ( x_mean <= -4.68654680252 ) {
                                                                if ( z_mean <= -1.10717308521 ) {
                                                                    return false;
                                                                } else {
                                                                    return true;
                                                                }
                                                            } else {
                                                                return false;
                                                            }
                                                        } else {
                                                            return false;
                                                        }
                                                    } else {
                                                        if ( x_mean <= -3.58018159866 ) {
                                                            return true;
                                                        } else {
                                                            if ( y_mean <= -8.19405841827 ) {
                                                                return false;
                                                            } else {
                                                                return true;
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                if ( z_mean <= -0.311860620975 ) {
                                                    return false;
                                                } else {
                                                    if ( z_mean <= -0.0728372335434 ) {
                                                        return true;
                                                    } else {
                                                        return false;
                                                    }
                                                }
                                            }
                                        } else {
                                            return true;
                                        }
                                    } else {
                                        if ( y_mean <= -7.0118560791 ) {
                                            if ( z_mean <= 6.42052936554 ) {
                                                if ( x_mean <= -2.40269374847 ) {
                                                    if ( x_mean <= -3.48435640335 ) {
                                                        return false;
                                                    } else {
                                                        return true;
                                                    }
                                                } else {
                                                    return false;
                                                }
                                            } else {
                                                return true;
                                            }
                                        } else {
                                            return false;
                                        }
                                    }
                                }
                            }
                        } else {
                            if ( z_mean <= 2.59543180466 ) {
                                return true;
                            } else {
                                return false;
                            }
                        }
                    }
                } else {
                    if ( z_mean <= 0.540688037872 ) {
                        if ( y_mean <= -3.71821689606 ) {
                            if ( x_mean <= -9.06424331665 ) {
                                return false;
                            } else {
                                if ( x_mean <= -8.12728691101 ) {
                                    return true;
                                } else {
                                    return false;
                                }
                            }
                        } else {
                            if ( z_mean <= -2.52151250839 ) {
                                return false;
                            } else {
                                if ( y_mean <= 18.9356918335 ) {
                                    if ( z_mean <= 0.0699166506529 ) {
                                        return true;
                                    } else {
                                        if ( y_mean <= -2.62679791451 ) {
                                            if ( x_mean <= -9.75483512878 ) {
                                                return true;
                                            } else {
                                                if ( x_mean <= -9.427775383 ) {
                                                    return false;
                                                } else {
                                                    return true;
                                                }
                                            }
                                        } else {
                                            if ( z_mean <= 0.517773985863 ) {
                                                return true;
                                            } else {
                                                if ( y_mean <= -1.72962641716 ) {
                                                    return false;
                                                } else {
                                                    return true;
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    return false;
                                }
                            }
                        }
                    } else {
                        if ( z_mean <= 0.591979503632 ) {
                            if ( y_mean <= -2.54721164703 ) {
                                return false;
                            } else {
                                return true;
                            }
                        } else {
                            if ( x_mean <= 3.94212245941 ) {
                                if ( z_mean <= 0.964741826057 ) {
                                    if ( y_mean <= -2.43396520615 ) {
                                        return false;
                                    } else {
                                        return true;
                                    }
                                } else {
                                    if ( x_mean <= -8.80621147156 ) {
                                        return false;
                                    } else {
                                        if ( x_mean <= -8.79809379578 ) {
                                            return true;
                                        } else {
                                            if ( z_mean <= 3.20363163948 ) {
                                                if ( x_mean <= -7.98044586182 ) {
                                                    if ( y_mean <= -4.16380596161 ) {
                                                        if ( x_mean <= -8.18628311157 ) {
                                                            return true;
                                                        } else {
                                                            return false;
                                                        }
                                                    } else {
                                                        return false;
                                                    }
                                                } else {
                                                    if ( y_mean <= -3.98555660248 ) {
                                                        return false;
                                                    } else {
                                                        return true;
                                                    }
                                                }
                                            } else {
                                                return false;
                                            }
                                        }
                                    }
                                }
                            } else {
                                if ( x_mean <= 4.07435131073 ) {
                                    return true;
                                } else {
                                    return false;
                                }
                            }
                        }
                    }
                }
            } else {
                if ( z_mean <= 1.05499064922 ) {
                    return true;
                } else {
                    if ( x_mean <= 8.0973443985 ) {
                        if ( z_mean <= 4.41103315353 ) {
                            if ( x_mean <= 7.725440979 ) {
                                return false;
                            } else {
                                return true;
                            }
                        } else {
                            return true;
                        }
                    } else {
                        if ( z_mean <= 1.21502041817 ) {
                            if ( z_mean <= 1.15645074844 ) {
                                return false;
                            } else {
                                return true;
                            }
                        } else {
                            if ( y_mean <= -2.95744347572 ) {
                                if ( z_mean <= 3.11497831345 ) {
                                    return false;
                                } else {
                                    return true;
                                }
                            } else {
                                return false;
                            }
                        }
                    }
                }
            }
        }

    }

    // Placeholder
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

}
