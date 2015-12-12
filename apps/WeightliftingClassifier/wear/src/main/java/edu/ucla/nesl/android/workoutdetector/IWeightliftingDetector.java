package edu.ucla.nesl.android.workoutdetector;

/**
 * Created by timestring on 11/18/15.
 */
public interface IWeightliftingDetector {
    void reset();
    void input(float x, float y, float z);
}
