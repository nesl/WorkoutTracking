package com.example.ryanlpeterman.workouttracker;

import android.hardware.SensorEvent;

/**
 * Created by ryanlpeterman on 7/31/15.
 * Data Structure to encapsulate all data manipulation
 */
public class DataWindow {

    private int mCount;                 // Current num samples in array
    private int mCapacity;              // Max number of samples in window
    private SensorData[] mData;         // Reference to the Data

    // Constructor calculates count per window
    public DataWindow(int seconds, int frequency){

        mCapacity = seconds * frequency;

        mData = new SensorData[mCapacity];

        for(int i = 0; i < mCapacity; i++){
            mData[i] = new SensorData();
        }

    }
    // reset counting variable so we read in new data
    public void emptyBuffer(){
        mCount = 0;
    }

    public boolean isFull() {
        if (mCount < mCapacity)
            return false;
        else
            return true;
    }

    public boolean addSample(SensorEvent event) {
        // Buffer is full data must be processed
        if (!(mCount < mCapacity))
            return false;

        // Store Sample
        mData[mCount].x_acc = event.values[0];
        mData[mCount].y_acc = event.values[1];
        mData[mCount].z_acc = event.values[2];

        // Increment Counter
        mCount++;

        return true;
    }

    public SensorData[] getData() {
        return mData;
    }

    public int getCount(){
        return mCount;
    }

}