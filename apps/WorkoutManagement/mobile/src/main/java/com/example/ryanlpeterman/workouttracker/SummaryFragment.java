package com.example.ryanlpeterman.workouttracker;

import android.support.v4.app.Fragment;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;

/**
 * Created by tahiyasalam on 8/3/15.
 */
public class SummaryFragment extends Fragment {
    public static final ArrayList<ExerciseGroup> events = new ArrayList<ExerciseGroup>();
    private ListView mListView;

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View view = inflater.inflate(R.layout.activity_summary, container, false);
        mListView = (ListView) view.findViewById(R.id.listView);

        String date = null;
        ExerciseGroup eventClicked = null;

        if (getArguments() != null) {
            date = getArguments().getString("Date");
            Log.d("TEST", date);
        }

        for (ExerciseGroup d : mEvents()) {
            if(d.getDate() != null && d.getDate().equals(date)) {
                eventClicked = d;
//                Toast.makeText(getActivity().getApplicationContext(),
//                        "Activity for date available",
//                        Toast.LENGTH_SHORT).show();
            }
            else {
//                Toast.makeText(getActivity().getApplicationContext(),
//                        "Activity for date unavailable",
//                        Toast.LENGTH_SHORT).show();
            }
        }

        ArrayList<ExerciseGroup> selectedEvent = new ArrayList<ExerciseGroup>();
        selectedEvent.add(eventClicked);
        Log.d("TEST", selectedEvent.size() + "");

        MyAdapter arrayAdapter = new MyAdapter(this.getActivity(), R.layout.dataitem, selectedEvent);
        mListView.setAdapter(arrayAdapter);

        return view;
    }

    public ArrayList<ExerciseGroup> mEvents() {
        final SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy");

        //Data structure for holding information related to activities/days
        //Dummy data
        ExerciseGroup newExerciseGroup = new ExerciseGroup("01 Dec 2015");

        Exercise ex1 = new Exercise();
        ex1.setmTime("16:30:21");
        ex1.setmType(WorkoutType.Walking);
        ex1.setmDuration(15);
        newExerciseGroup.addExercise(ex1);

        Exercise ex2 = new Exercise();
        ex2.setmTime("16:50:00");
        ex2.setmType(WorkoutType.Running);
        ex2.setmDuration(30);
        newExerciseGroup.addExercise(ex2);

        Exercise ex3 = new Exercise();
        ex3.setmTime("17:30:08");
        ex3.setmType(WorkoutType.Walking);
        ex3.setmDuration(10);
        newExerciseGroup.addExercise(ex3);

        Exercise ex4 = new Exercise();
        ex4.setmTime("17:45:00");
        ex4.setmType(WorkoutType.WL_bicep_curl);
        ex4.setmRepCount(10);
        newExerciseGroup.addExercise(ex4);

        Exercise ex5 = new Exercise();
        ex5.setmTime("17:50:00");
        ex5.setmType(WorkoutType.WL_bicep_curl);
        ex5.setmRepCount(10);
        newExerciseGroup.addExercise(ex5);

        Exercise ex6 = new Exercise();
        ex6.setmTime("17:55:38");
        ex6.setmType(WorkoutType.WL_butter_fly);
        ex6.setmRepCount(15);
        newExerciseGroup.addExercise(ex6);

        Exercise ex7 = new Exercise();
        ex7.setmTime("18:00:55");
        ex7.setmType(WorkoutType.WL_butter_fly);
        ex7.setmRepCount(10);
        newExerciseGroup.addExercise(ex7);

        Exercise ex8 = new Exercise();
        ex8.setmTime("18:08:12");
        ex8.setmType(WorkoutType.WL_chest_press);
        ex8.setmRepCount(8);
        newExerciseGroup.addExercise(ex8);

        events.add(newExerciseGroup);
        return events;
    }


}
