package com.example.ryanlpeterman.workouttracker;

import android.content.Context;
import android.graphics.Typeface;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;

/**
 * Created by tahiyasalam on 8/3/15.
 * Customized adapter to display fields of data class
 * Fields of data class represent activity completed
 */
public class MyAdapter extends ArrayAdapter<ExerciseGroup> {
    private ArrayList<ExerciseGroup> mlExerciseGroup;

    private static LayoutInflater inflater = null;
    private Context mContext;

    public MyAdapter(Context context, int resource,  ArrayList<ExerciseGroup> lExerciseGroup) {
        super(context, resource, lExerciseGroup);
        mlExerciseGroup = lExerciseGroup;
        mContext = context;
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;

        if(v == null) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = inflater.inflate(R.layout.dataitem, null);
        }
        ExerciseGroup exerciseGroup = mlExerciseGroup.get(position);
        LinearLayout linearLayout = (LinearLayout) v.findViewById(R.id.exercise_list);

        Log.i("TEST", "total_exercise=" + exerciseGroup.getmExercises().size() + "");

        if(!exerciseGroup.getmExercises().isEmpty()) {
            TextView timeView = new TextView(mContext);
            timeView.setId(1);
            timeView.setText(exerciseGroup.getDate());
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            llp.setMargins(0, 0, 0, 0);
            timeView.setLayoutParams(llp);
            // timeView.setTextAppearance(mContext, R.style.Widget_AppCompat_Button);
            timeView.setTextSize(20);
            timeView.setTypeface(null, Typeface.BOLD);
            linearLayout.addView(timeView);

            double cardio_total = 0;
            int rep_total = 0;
            int set_total = 0;

            for (Exercise ex : exerciseGroup.getmExercises()) {
                Log.i("TEST", ex.getmType().toString());
                TextView exerciseView = new TextView(mContext);
                exerciseView.setId(1);
                if (ex.getmDuration() != -1) {
                    exerciseView.setText(ex.getmTime() + ": " + ex.getmType().toString() + " for " + ex.getmDuration() + "min.");
                    cardio_total += ex.getmDuration();
                }
                else if (ex.getmRepCount() != -1) {
                    exerciseView.setText(ex.getmTime() + ": " + ex.getmType().toString() + " for " + ex.getmRepCount() + "reps.");
                    rep_total += ex.getmRepCount();
                    set_total++;
                }

                LinearLayout.LayoutParams llp1 = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                llp1.setMargins(50, 20, 0, 0);
                exerciseView.setLayoutParams(llp1);
                exerciseView.setTextAppearance(mContext, R.style.TextAppearance_AppCompat_Button);
                exerciseView.setTextSize(16);
                linearLayout.addView(exerciseView);
            }

            TextView cardioView = new TextView(mContext);
            cardioView.setId(1);
            cardioView.setText("Cardio total: " + cardio_total + " min.");
            LinearLayout.LayoutParams llpc = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            llpc.setMargins(0, 30, 0, 0);
            cardioView.setLayoutParams(llpc);
            // timeView.setTextAppearance(mContext, R.style.Widget_AppCompat_Button);
            cardioView.setTextSize(20);
            cardioView.setTypeface(null, Typeface.BOLD);
            linearLayout.addView(cardioView);

            TextView repView = new TextView(mContext);
            repView.setId(1);
            repView.setText("Weightlifting total:\n\t" + set_total + " sets, " + rep_total + " reps.");
            LinearLayout.LayoutParams llpl = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            llpl.setMargins(0, 30, 0, 0);
            repView.setLayoutParams(llpl);
            // timeView.setTextAppearance(mContext, R.style.Widget_AppCompat_Button);
            repView.setTextSize(20);
            repView.setTypeface(null, Typeface.BOLD);
            linearLayout.addView(repView);
        }

        return v;

    }


    public int getCount() {
        return mlExerciseGroup.size();
    }

    public long getItemId(int position) {
        return position;
    }


}
