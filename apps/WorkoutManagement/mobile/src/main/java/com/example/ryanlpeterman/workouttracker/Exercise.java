package com.example.ryanlpeterman.workouttracker;

/**
 * Created by cgshen on 12/3/15.
 */
public class Exercise {
    private String mTime;
    private WorkoutType mType;
    private double mDuration = -1;
    private int mRepCount = -1;

    public Exercise() {

    }

    public int getmRepCount() {
        return mRepCount;
    }

    public void setmRepCount(int mRepCount) {
        this.mRepCount = mRepCount;
    }

    public String getmTime() {
        return mTime;
    }

    public void setmTime(String mTime) {
        this.mTime = mTime;
    }

    public double getmDuration() {
        return mDuration;
    }

    public void setmDuration(double mDuration) {
        this.mDuration = mDuration;
    }

    public WorkoutType getmType() {
        return mType;
    }

    public void setmType(WorkoutType mType) {
        this.mType = mType;
    }
}
