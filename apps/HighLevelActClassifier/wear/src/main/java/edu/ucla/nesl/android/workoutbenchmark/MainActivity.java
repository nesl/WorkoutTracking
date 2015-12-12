package edu.ucla.nesl.android.workoutbenchmark;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.TextView;

import java.util.Date;

public class MainActivity extends Activity {
    private static final String TAG = "Wear/MainActivity";
    private TextView mTextView;
    private InferenceAlarmReceiver alarm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
            }
        });

        // Create and set an alarm
        alarm = new InferenceAlarmReceiver();
        alarm.init();
        alarm.setAlarm(this);

//        // Start inference
//        Intent intent = new Intent(this, BGDataCollectionService.class);
//        startService(intent);

        // Measure SigMo
//        Intent intent = new Intent(MainActivity.this, BGSigMotionService.class);
//        startService(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume() called");
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause() called");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        alarm.cancelAlarm(this);
        Log.i(TAG, "onDestroy() called");
    }
}
