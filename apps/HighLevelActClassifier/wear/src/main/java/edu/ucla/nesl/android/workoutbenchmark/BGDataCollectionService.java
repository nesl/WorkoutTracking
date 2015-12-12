package edu.ucla.nesl.android.workoutbenchmark;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import edu.ucla.nesl.android.workoutbenchmark.hmm.HMMViterbi;

public class BGDataCollectionService extends Service {
    private static final String TAG = "Wear:BGDCService";
    private final static int SENS_ACCELEROMETER = Sensor.TYPE_ACCELEROMETER;
    private final static int BATTERY_LOG_INTERVAL = 50 * 60;
    public static SimpleDateFormat formatForFile = new SimpleDateFormat("yyyyMMdd_HHmmss");

    private final static boolean logBattery = true;
    private static BufferedWriter batteryLogger = null;
    private static HighLevelActivityClassifier mClassifier;
    private static SensorManager mSensorManager;
    public static BGDataCollectionService mContext = null;


    private static final Object lock = new Object();

    public BGDataCollectionService() {

    }

//    static {
//        System.loadLibrary("sgd-crf");
//    }

    public native int ndktest();
    public static native int load_model();
    public static native int classify(int[] features);

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);
        mContext = this;
        startRecording();
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }

    private static void logBatteryPerc(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = (float) level / (float) scale;
        try {
            if (batteryPct > 0.2) {
                batteryLogger.append(formatForFile.format(new Date()));
                batteryLogger.append(",");
                batteryLogger.append(String.valueOf(batteryPct));
                batteryLogger.append("\n");
                batteryLogger.flush();
                Log.d(TAG, "Battery level = " + batteryPct);
            }
            else {
                batteryLogger.flush();
                batteryLogger.close();
                batteryLogger = null;
                Log.d(TAG, "Battery level too low, stop logging.");
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void startRecording() {
        Log.i(TAG, "In start recording");

        // Acquire wakelock
        PowerManager mPowerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        wl = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "inf_wakelock_1");
        wl.acquire();

        // Start inference using only acc
        mSensorManager = ((SensorManager) mContext.getSystemService(Context.SENSOR_SERVICE));
        Sensor accelerometerSensor = mSensorManager.getDefaultSensor(SENS_ACCELEROMETER);

        // Register the listener
        if (mSensorManager != null) {
            if (accelerometerSensor != null) {
                // Initialize models
                if (classifierType == ClassifierType.CRF) {
                    // Load model
                    int res = load_model();
                    Log.i(TAG, "NDK load model result=" + res);
                }

                if (classifierType == ClassifierType.DT) {
                    HMMViterbi.initHmm();
                    Log.i(TAG, "HMM initialized");
                }

                // Create the classifier and subscribe to sensor data
                mClassifier = new HighLevelActivityClassifier(mContext);
                mSensorManager.registerListener(mClassifier, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);

                // Create battery logger
                String dateStr = formatForFile.format(new Date());
                if (logBattery) {
                    try {
                        String filename = "/sdcard/battery_log_" + dateStr + ".txt";
                        batteryLogger = new BufferedWriter(new FileWriter(filename, true));
                        Log.i(TAG, "Created battery log file: " + filename);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    logBatteryPerc(mContext);
                }
                Log.i(TAG, "InferenceAlarmReceiver execution started at " + dateStr + ", type=" + classifierType);
            } else {
                Log.w(TAG, "No Accelerometer found");
            }
        }
    }

    public static void stopRecording() {
        Log.i(TAG, "In stop recording");
        mSensorManager.unregisterListener(mClassifier);
        mSensorManager = null;
        if (logBattery && batteryLogger != null) {
            try {
                batteryLogger.flush();
                batteryLogger.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        Log.i(TAG, "Stop inference at " + formatForFile.format(new Date()));

        // Release wakelock
        wl.release();
    }

    @Override
    public void onDestroy () {
        super.onDestroy();
        if (mSensorManager != null) {
            stopRecording();
        }
    }

    private static int numThreads;
    private static int sensorCount = 0;
    private static final int N_FEATURES = 7;
    private static final double[][] CLUSTER_CENTERS =
            {{ -0.504281751711 , -8.09276666925 , -4.47083986562 , 2.84728180244 , -3.33131011843 , -10.9147601965 , 1.27745143108 , -5.96810018688 , -2.00348671969 , 4.0382394384 , -9.56343919785 , -12.8449526841},
                    { 0.154922771093 , 6.34680868012 , 34.3106704816 , 0.726054985895 , 1.86973283037 , 2.78365017429 , 5.54188642885 , 7.30933609362 , 60.9388006779 , 26.1367798977 , 1.21457058434 , 4.18095446071},
                    { 1.61682561211 , 7.44149309234 , 55.7218130146 , 0.202993220093 , 29.5867194785 , 4.3176033955 , 0.934071015708 , 6.47349051436 , 2.66328751138 , 8.54089215414 , 48.1321881776 , 73.2974242116},
                    { 2.57879018236 , -3.43150748876 , -0.17292300746 , 4.71812905422 , -5.09715862078 , -2.17590980739 , 1.24229342898 , -1.25415115586 , -2.84383195172 , -7.12121420505 , 3.68281082233 , -4.18647078859},
                    { -3.39128541262 , 3.22745862449 , -0.194814324013 , 4.47279657575 , -4.36134754466 , -19.6427741234 , -2.66827571067 , -1.46939815073 , 13.4382501282 , -5.56484478746 , 1.46382418538 , 36.4782168712},
                    { 18.4695599522 , 2.56813215907 , 188.877918393 , 27.3480745853 , 8.74430175797 , 4.19419693125 , 0.536659532191 , 22.0696081447 , 31.6127459037 , 6.24043068847 , 12.4944977994 , 24.608160153},
                    { 46.6950595149 , 2608471.03368 , 2517098.40377 , 2729930.86067 , 4837.55355247 , 2474130.19167 , 1900.38501102 , 2576244.77056 , 8360.30725214 , 2635592.51379 , 894.529125822 , 3162.13742007}};

    private enum ClassifierType{
        CRF, DT
    }
    private static final ClassifierType classifierType = ClassifierType.DT;
    private static PowerManager.WakeLock wl;

    // HMM queue
    private static final int HMM_QUEUE_SIZE = 300;
    private static Queue<Integer> resultQueue = new LinkedList<>();


    static class HighLevelActivityClassifier implements SensorEventListener {
        int count = 0;
        Thread thread;
        long start = 0;
        double[] data = new double[100];
        Context mContext;


        public HighLevelActivityClassifier(Context _context) {
            super();
            mContext = _context;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // Log.d(TAG, "onSensorDataChanged");
            if (event.sensor.getType() == SENS_ACCELEROMETER) {
                float [] values = event.values;
                double totalForce = 0.0;
                double accx = values[0];
                double accy = values[1];
                double accz = values[2];
                sensorCount++;

                totalForce += Math.pow(accx, 2.0);
                totalForce += Math.pow(accy, 2.0);
                totalForce += Math.pow(accz, 2.0);
                totalForce = Math.sqrt(totalForce);


                long cur = System.currentTimeMillis();
                // 1s classification window
                if (cur - start >= 1000) {
                    start = cur;
                    double[] classData = new double[count];
                    System.arraycopy(data, 0, classData, 0, classData.length);

                    if (count >= 5) {
                        thread = new Thread(new Worker(classData));
                        // Log.d(TAG, "Starting thread");
                        thread.start();
                        numThreads++;
                    }
                    else {
                        Log.d(TAG, "too few acc samples");
                    }
                    count = 0;
                    //Log.d(TAG, "Reset samples");
                }
                if (count >= 100) {
                    Log.d(TAG, "Wha?? " + System.currentTimeMillis() + " is the time now and we started samples at " + start + " and there are " + count
                            + " samples");
                    return;
                }

                data[count] = totalForce;
                count++;

                // For each BATTERY_LOG_INTERVAL, log the current battery percentage
                if (sensorCount >= BATTERY_LOG_INTERVAL && logBattery) {
                    // Get the current battery percentage and log to file
                    logBatteryPerc(mContext);
                    // Reset sensor counter
                    sensorCount = 0;
                }
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {

        }

        private class Worker implements Runnable {
            double[] mData;

            public Worker(double[] _data) {
                mData = _data;
            }

            @Override
            public void run() {
                synchronized(lock) {
                    long tic = System.nanoTime();

                    int activity = -1;
                    int n = mData.length;

                    // Features: mean, std, mad, range, qua1, qua2, fft5
                    double wear_sum = 0.0;
                    double wear_mean = 0.0;
                    double wear_std = 0.0;
                    double wear_mad = 0.0;
                    double wear_range = 0.0;
                    double wear_min = Double.MAX_VALUE;
                    double wear_max = Double.MIN_VALUE;
                    double wear_qua25 = 0.0;
                    double wear_qua50 = 0.0;
                    double wear_fft5 = 0.0;

                    // Calcualte mean, range
                    for (int i = 0; i < n; i++) {
                        double temp = mData[i];
                        wear_sum += temp;
                        wear_min = Math.min(wear_min, temp);
                        wear_max = Math.max(wear_max, temp);
                    }
                    wear_mean = wear_sum / n;
                    wear_range = Math.abs(wear_max - wear_min);

                    // Calculate std and mad
                    wear_sum = 0.0;
                    for (int i = 0; i < n; i++){
                        wear_sum += Math.pow((mData[i] - wear_mean), 2.0);
                        wear_mad += Math.abs(mData[i] - wear_mean);
                    }
                    wear_std = Math.sqrt(wear_sum / n);
                    wear_mad = wear_mad / n;

                    // Get fft
                    wear_fft5 = goertzel(mData, 5., n);

                    // Calculate qua25, qua50
                    // This needs to happen last because of the sorting
                    int n1 = (int) Math.round(n * 0.25);
                    int n2 = (int) Math.round(n * 0.5);
                    Arrays.sort(mData);
                    wear_qua25 = mData[n1];
                    wear_qua50 = mData[n2];

                    if (classifierType == ClassifierType.CRF) {
                        // ***** Classification model 1: kmeans + crf, invoke NDK
                        // Cluster features into IDs
                        int[] features = new int[N_FEATURES + 1];
                        features[0] = findClosestClusterCenter(CLUSTER_CENTERS[0], wear_mean);
                        features[1] = findClosestClusterCenter(CLUSTER_CENTERS[1], wear_std);
                        features[2] = findClosestClusterCenter(CLUSTER_CENTERS[2], wear_mad);
                        features[3] = findClosestClusterCenter(CLUSTER_CENTERS[3], wear_range);
                        features[4] = findClosestClusterCenter(CLUSTER_CENTERS[4], wear_qua25);
                        features[5] = findClosestClusterCenter(CLUSTER_CENTERS[5], wear_qua50);
                        features[6] = findClosestClusterCenter(CLUSTER_CENTERS[6], wear_fft5);
                        // Label for accuracy calculation set to 1 for now
                        features[7] = 1;
                        // Invoke NDK for classification
                        activity = classify(features);
                    }
                    else if (classifierType == ClassifierType.DT) {
                        // ***** Classification model 2: decision tree + hmm
                        if ( wear_range <= 14.9775590897 ) {
                            if ( wear_mean <= -1.7040605545 ) {
                                if ( wear_fft5 <= 21.4943027496 ) {
                                    if ( wear_mad <= 0.394643932581 ) {
                                        if ( wear_fft5 <= 0.0135743748397 ) {
                                            if ( wear_qua25 <= -2.89149236679 ) {
                                                if ( wear_mad <= 0.317696690559 ) {
                                                    activity = 0;
                                                } else {
                                                    activity = 3;
                                                }
                                            } else {
                                                if ( wear_std <= 0.0317901745439 ) {
                                                    if ( wear_fft5 <= 0.00169613095932 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                } else {
                                                    if ( wear_qua25 <= -2.84338879585 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 0;
                                                    }
                                                }
                                            }
                                        } else {
                                            if ( wear_std <= 0.0817403048277 ) {
                                                if ( wear_mean <= -2.48627614975 ) {
                                                    activity = 2;
                                                } else {
                                                    if ( wear_mean <= -2.09484434128 ) {
                                                        activity = 0;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                }
                                            } else {
                                                if ( wear_fft5 <= 0.566529095173 ) {
                                                    activity = 0;
                                                } else {
                                                    if ( wear_mean <= -4.58591175079 ) {
                                                        activity = 1;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        if ( wear_fft5 <= 4.80408143997 ) {
                                            if ( wear_qua25 <= -3.18300628662 ) {
                                                if ( wear_mean <= -3.39012980461 ) {
                                                    activity = 0;
                                                } else {
                                                    if ( wear_range <= 1.4205596447 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 0;
                                                    }
                                                }
                                            } else {
                                                if ( wear_fft5 <= 2.99034643173 ) {
                                                    activity = 1;
                                                } else {
                                                    if ( wear_std <= 0.704318225384 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 1;
                                                    }
                                                }
                                            }
                                        } else {
                                            if ( wear_std <= 0.849895000458 ) {
                                                if ( wear_mad <= 0.547067701817 ) {
                                                    if ( wear_mean <= -2.70704317093 ) {
                                                        activity = 2;
                                                    } else {
                                                        activity = 0;
                                                    }
                                                } else {
                                                    if ( wear_range <= 2.69754695892 ) {
                                                        activity = 1;
                                                    } else {
                                                        activity = 0;
                                                    }
                                                }
                                            } else {
                                                if ( wear_mean <= -2.28055143356 ) {
                                                    activity = 0;
                                                } else {
                                                    if ( wear_qua50 <= -2.36293745041 ) {
                                                        activity = 2;
                                                    } else {
                                                        activity = 1;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    if ( wear_std <= 1.11358296871 ) {
                                        if ( wear_mean <= -2.81929397583 ) {
                                            if ( wear_std <= 0.967353403568 ) {
                                                if ( wear_qua25 <= -5.91304969788 ) {
                                                    activity = 3;
                                                } else {
                                                    if ( wear_mad <= 0.657346367836 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 1;
                                                    }
                                                }
                                            } else {
                                                if ( wear_mad <= 0.746005058289 ) {
                                                    if ( wear_qua50 <= -3.84152173996 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                } else {
                                                    if ( wear_qua50 <= -2.87990045547 ) {
                                                        activity = 2;
                                                    } else {
                                                        activity = 3;
                                                    }
                                                }
                                            }
                                        } else {
                                            if ( wear_range <= 2.0473613739 ) {
                                                activity = 3;
                                            } else {
                                                if ( wear_range <= 3.62338495255 ) {
                                                    if ( wear_fft5 <= 63.7519302368 ) {
                                                        activity = 0;
                                                    } else {
                                                        activity = 3;
                                                    }
                                                } else {
                                                    activity = 1;
                                                }
                                            }
                                        }
                                    } else {
                                        if ( wear_fft5 <= 70.9755096436 ) {
                                            if ( wear_mad <= 1.25985479355 ) {
                                                if ( wear_mean <= -2.74963998795 ) {
                                                    if ( wear_range <= 4.0228972435 ) {
                                                        activity = 1;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                } else {
                                                    if ( wear_qua25 <= -3.60012125969 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 0;
                                                    }
                                                }
                                            } else {
                                                if ( wear_qua50 <= -3.22825670242 ) {
                                                    if ( wear_std <= 2.00068712234 ) {
                                                        activity = 2;
                                                    } else {
                                                        activity = 0;
                                                    }
                                                } else {
                                                    activity = 0;
                                                }
                                            }
                                        } else {
                                            if ( wear_qua50 <= -5.21220207214 ) {
                                                if ( wear_fft5 <= 110.31741333 ) {
                                                    activity = 3;
                                                } else {
                                                    if ( wear_range <= 8.5129032135 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 0;
                                                    }
                                                }
                                            } else {
                                                if ( wear_fft5 <= 149.534820557 ) {
                                                    if ( wear_std <= 2.29885482788 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 0;
                                                    }
                                                } else {
                                                    activity = 1;
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                if ( wear_mad <= 0.219667553902 ) {
                                    if ( wear_qua50 <= 4.57947921753 ) {
                                        if ( wear_mean <= 3.23217630386 ) {
                                            if ( wear_mean <= -1.57298183441 ) {
                                                if ( wear_fft5 <= 0.228285908699 ) {
                                                    if ( wear_std <= 0.0768529623747 ) {
                                                        activity = 2;
                                                    } else {
                                                        activity = 1;
                                                    }
                                                } else {
                                                    activity = 3;
                                                }
                                            } else {
                                                if ( wear_std <= 0.197052717209 ) {
                                                    activity = 2;
                                                } else {
                                                    if ( wear_fft5 <= 0.131466045976 ) {
                                                        activity = 0;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                }
                                            }
                                        } else {
                                            if ( wear_fft5 <= 1.09179449081 ) {
                                                if ( wear_qua25 <= 3.42348909378 ) {
                                                    if ( wear_fft5 <= 0.0979900658131 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                } else {
                                                    if ( wear_qua25 <= 4.08484745026 ) {
                                                        activity = 1;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                }
                                            } else {
                                                if ( wear_mean <= 3.6744158268 ) {
                                                    if ( wear_std <= 0.264720022678 ) {
                                                        activity = 0;
                                                    } else {
                                                        activity = 3;
                                                    }
                                                } else {
                                                    if ( wear_std <= 0.200703024864 ) {
                                                        activity = 0;
                                                    } else {
                                                        activity = 1;
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        activity = 3;
                                    }
                                } else {
                                    if ( wear_mean <= 2.72800827026 ) {
                                        if ( wear_fft5 <= 11.3444042206 ) {
                                            if ( wear_mad <= 0.522336006165 ) {
                                                if ( wear_qua50 <= -1.02487289906 ) {
                                                    activity = 2;
                                                } else {
                                                    if ( wear_fft5 <= 1.18687212467 ) {
                                                        activity = 2;
                                                    } else {
                                                        activity = 1;
                                                    }
                                                }
                                            } else {
                                                if ( wear_qua25 <= 1.02550148964 ) {
                                                    if ( wear_qua50 <= -0.907158017159 ) {
                                                        activity = 2;
                                                    } else {
                                                        activity = 0;
                                                    }
                                                } else {
                                                    if ( wear_range <= 4.92303705215 ) {
                                                        activity = 0;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                }
                                            }
                                        } else {
                                            if ( wear_qua25 <= -0.707745969296 ) {
                                                if ( wear_range <= 11.2373447418 ) {
                                                    activity = 0;
                                                } else {
                                                    if ( wear_qua25 <= -3.86460065842 ) {
                                                        activity = 1;
                                                    } else {
                                                        activity = 3;
                                                    }
                                                }
                                            } else {
                                                if ( wear_range <= 7.66439199448 ) {
                                                    if ( wear_mad <= 0.857717633247 ) {
                                                        activity = 1;
                                                    } else {
                                                        activity = 0;
                                                    }
                                                } else {
                                                    if ( wear_qua25 <= 0.366312801838 ) {
                                                        activity = 1;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        if ( wear_range <= 9.92084121704 ) {
                                            if ( wear_fft5 <= 223.772399902 ) {
                                                if ( wear_fft5 <= 3.73607206345 ) {
                                                    activity = 0;
                                                } else {
                                                    if ( wear_qua50 <= 2.74448823929 ) {
                                                        activity = 1;
                                                    } else {
                                                        activity = 0;
                                                    }
                                                }
                                            } else {
                                                if ( wear_mad <= 0.814567923546 ) {
                                                    if ( wear_mean <= 3.28111028671 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 1;
                                                    }
                                                } else {
                                                    if ( wear_mean <= 3.44950628281 ) {
                                                        activity = 1;
                                                    } else {
                                                        activity = 0;
                                                    }
                                                }
                                            }
                                        } else {
                                            if ( wear_mean <= 3.66206789017 ) {
                                                if ( wear_fft5 <= 4.88498592377 ) {
                                                    activity = 3;
                                                } else {
                                                    activity = 3;
                                                }
                                            } else {
                                                if ( wear_std <= 4.03944540024 ) {
                                                    activity = 0;
                                                } else {
                                                    if ( wear_mad <= 3.31446456909 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            if ( wear_range <= 17.6621856689 ) {
                                if ( wear_fft5 <= 322.353759766 ) {
                                    if ( wear_mad <= 3.49982309341 ) {
                                        activity = 3;
                                    } else {
                                        if ( wear_fft5 <= 196.464797974 ) {
                                            if ( wear_mean <= -0.155428171158 ) {
                                                if ( wear_mad <= 3.52523136139 ) {
                                                    activity = 3;
                                                } else {
                                                    activity = 2;
                                                }
                                            } else {
                                                if ( wear_mean <= 2.07545185089 ) {
                                                    activity = 3;
                                                } else {
                                                    if ( wear_range <= 15.355931282 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                }
                                            }
                                        } else {
                                            activity = 3;
                                        }
                                    }
                                } else {
                                    if ( wear_mad <= 3.08938169479 ) {
                                        activity = 3;
                                    } else {
                                        if ( wear_range <= 17.3148670197 ) {
                                            if ( wear_mad <= 4.82710075378 ) {
                                                if ( wear_range <= 15.5149860382 ) {
                                                    if ( wear_range <= 15.4321460724 ) {
                                                        activity = 0;
                                                    } else {
                                                        activity = 3;
                                                    }
                                                } else {
                                                    activity = 2;
                                                }
                                            } else {
                                                activity = 3;
                                            }
                                        } else {
                                            if ( wear_qua25 <= -6.82999897003 ) {
                                                activity = 2;
                                            } else {
                                                activity = 3;
                                            }
                                        }
                                    }
                                }
                            } else {
                                if ( wear_mad <= 3.75195026398 ) {
                                    if ( wear_range <= 19.7425804138 ) {
                                        activity = 3;
                                    } else {
                                        activity = 2;
                                    }
                                } else {
                                    if ( wear_mad <= 48.4763412476 ) {
                                        if ( wear_range <= 19.379945755 ) {
                                            if ( wear_mean <= -5.43829536438 ) {
                                                activity = 3;
                                            } else {
                                                if ( wear_fft5 <= 27.9952278137 ) {
                                                    if ( wear_qua50 <= -1.73894357681 ) {
                                                        activity = 2;
                                                    } else {
                                                        activity = 3;
                                                    }
                                                } else {
                                                    if ( wear_mean <= 0.885746896267 ) {
                                                        activity = 2;
                                                    } else {
                                                        activity = 1;
                                                    }
                                                }
                                            }
                                        } else {
                                            if ( wear_std <= 5.75497674942 ) {
                                                if ( wear_std <= 5.75363540649 ) {
                                                    if ( wear_qua50 <= -3.73633122444 ) {
                                                        activity = 0;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                } else {
                                                    activity = 3;
                                                }
                                            } else {
                                                if ( wear_qua25 <= -4.52561473846 ) {
                                                    activity = 2;
                                                } else {
                                                    if ( wear_qua25 <= -4.45105552673 ) {
                                                        activity = 3;
                                                    } else {
                                                        activity = 2;
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        activity = 3;
                                    }
                                }
                            }
                        }

                        // Save new activity result to queue for HMM smoothing
                        resultQueue.offer(activity);
                        if (resultQueue.size() > HMM_QUEUE_SIZE) {
                            resultQueue.poll();

                            // Apply HMM to smmoth result
                            int[] states = HMMViterbi.HMMViterbiCalcuator(new LinkedList<>(resultQueue));
                            activity = states[states.length - 1];
                        }
                    }

                    // Output result and calculate elapsed time
                    long toc = System.nanoTime();
                    Log.d(TAG, String.format("act=%d, time=%dns", activity, (toc - tic)));
                    // Log.d(TAG, String.format("There are %d threads", numThreads));
                    numThreads--;
                }
            }
        }

        private static int findClosestClusterCenter(double[] a, double x) {
            int idx = 0;
            double min = Math.abs(a[0] - x);
            for (int i = 1; i < a.length; i++) {
                double dis = Math.abs(a[i] - x);
                if (dis < min) {
                    min = dis;
                    idx = i;
                }
            }
            return idx;
        }

        private static double goertzel(double [] data, double freq, double sr) {
            double s_prev = 0;
            double s_prev2 = 0;
            double coeff = 2 * Math.cos( (2*Math.PI*freq) / sr);
            double s;
            for (int i = 0; i < data.length; i++) {
                double sample = data[i];
                s = sample + coeff*s_prev  - s_prev2;
                s_prev2 = s_prev;
                s_prev = s;
            }
            double power = s_prev2*s_prev2 + s_prev*s_prev - coeff*s_prev2*s_prev;

            return power;
        }
    }
}
