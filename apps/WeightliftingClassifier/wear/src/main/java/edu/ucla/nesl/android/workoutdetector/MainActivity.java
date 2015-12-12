package edu.ucla.nesl.android.workoutdetector;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class MainActivity extends Activity {
    private TextView mTextView;
    // private BGDataCollectionService serviceInstance = null;
    private static final String TAG = "Activity";
    // Set format for date and time
    private static TimeString mTimestring = new TimeString();
    // Current state of the recording
    private boolean mTracking = false;
    private String mTime = null;
    private TextView mAccCounterTextView;
    private static long mAccCounter = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
                mAccCounterTextView = (TextView) stub.findViewById(R.id.text1);

                // Get saved recording state from shared preference
                SharedPreferences sharedPref = getSharedPreferences(
                        getString(R.string.preference_file_key), Context.MODE_PRIVATE);
                mTracking = sharedPref.getBoolean(getString(R.string.saved_rec_flag), false);
                if (mTracking) {
                    mTime = sharedPref.getString(getString(R.string.saved_rec_time), "");
                    mTextView.setText("(Energy) Sensor on at " + mTime);
                } else {
                    mTextView.setText("(Energy) Sensor off");
                }
            }
        });

        if (!mTracking) {
            // Start the service
            Intent intent = new Intent(this, BGDataCollectionService.class);
            startService(intent);
        }
    }

    public void onStartClicked(View view) {
        Log.i(TAG, "start clicked");
        if (!mTracking) {
            mTracking = true;
            mTime = mTimestring.currentTimeForDisplay();
            BGDataCollectionService.startRecording(mTimestring.currentTimeForFile());
            mTextView.setText("(Energy) Tracking started at " + mTime);
        }
        else {
            Log.w(TAG, "Tracking already started!");
        }
    }

    public void onStopClicked(View view) {
        Log.i(TAG, "stop clicked");
        if (mTracking) {
            BGDataCollectionService.stopRecording();
            mTextView.setText("(Energy) Tracking stopped");
            mTracking = false;
            mTime = null;
        }
        else {
            Log.w(TAG, "Tracking already stopped!");
        }
    }

    protected void onPause() {
        super.onPause();
        // Unbind to service on pause
//        unbindService(mConnection);
//        serviceInstance = null;
        // Save current recording state to shared preference
        SharedPreferences sharedPref = getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putBoolean(getString(R.string.saved_rec_flag), mTracking);
        if (mTracking) {
            editor.putString(getString(R.string.saved_rec_time), mTime);
        }
        else {
            editor.putString(getString(R.string.saved_rec_time), null);
        }
        editor.commit();

        // Unregister broadcast receiver
        LocalBroadcastManager bManager = LocalBroadcastManager.getInstance(this);
        bManager.unregisterReceiver(accCounterReceiver);
        Log.i(TAG, "Receiver un-registered.");
    }

    protected void onResume() {
        super.onResume();

        // Register broadcast receiver
        LocalBroadcastManager bManager = LocalBroadcastManager.getInstance(this);
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BGDataCollectionService.ACC_1K_ACTION);
        bManager.registerReceiver(accCounterReceiver, intentFilter);
        Log.i(TAG, "Receiver registered.");
    }


    private BroadcastReceiver accCounterReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.i(TAG, "broadcast received " + intent.getAction());
            if(intent.getAction().equals(BGDataCollectionService.ACC_1K_ACTION)) {
                mAccCounter++;
                mAccCounterTextView.setText("Grav: " + mAccCounter + "k @" + new TimeString().currentTimeOnlyForDisplay());
            }
        }
    };
}
