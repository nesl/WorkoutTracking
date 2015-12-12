package com.example.ryanlpeterman.workouttracker;

import java.util.ArrayList;

/**
 * Created by tahiyasalam on 8/3/15.
 */

public class ExerciseGroup {
    private String date;

    public ExerciseGroup(String date) {
        this.date = date;
        mExercises = new ArrayList<>();
    }

    private ArrayList<Exercise> mExercises;

    public ExerciseGroup() {
        mExercises = new ArrayList<>();
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public ArrayList<Exercise> getmExercises() {
        return mExercises;
    }

    public void setmExercises(ArrayList<Exercise> mExercises) {
        this.mExercises = mExercises;
    }

    public void addExercise(Exercise ex) {
        mExercises.add(ex);
    }
}
