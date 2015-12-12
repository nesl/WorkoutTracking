package com.example.ryanlpeterman.workouttracker;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.roomorama.caldroid.CaldroidFragment;
import com.roomorama.caldroid.CaldroidListener;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by tahiyasalam on 7/30/15.
 */
public class CalendarActivity extends AppCompatActivity {
    Map<String, ExerciseGroup> calendarEvents = new HashMap<String, ExerciseGroup>();
    final SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calendar);

        final CaldroidFragment caldroidFragment = new CaldroidFragment();
        Bundle args = new Bundle();

        Calendar cal = Calendar.getInstance();
        args.putInt(CaldroidFragment.THEME_RESOURCE, com.caldroid.R.style.CaldroidDefaultDark);
        args.putInt(CaldroidFragment.MONTH, cal.get(Calendar.MONTH) + 1);
        args.putInt(CaldroidFragment.YEAR, cal.get(Calendar.YEAR));
        caldroidFragment.setArguments(args);

        final FragmentTransaction t = getSupportFragmentManager().beginTransaction();

        final Fragment sumfrag = new SummaryFragment();
        final Bundle bundle = new Bundle();

        t.replace(R.id.calendar1, caldroidFragment);

        final CaldroidListener listener = new CaldroidListener() {

            @Override
            public void onSelectDate(Date date, View view) {
                Toast.makeText(getApplicationContext(), formatter.format(date),
                        Toast.LENGTH_SHORT).show();
                bundle.putString("Date", formatter.format(date));
                sumfrag.setArguments(bundle);
        //        Log.d("original: ", getSupportFragmentManager().getFragments().toString() + " size: " + getSupportFragmentManager().getFragments().size());
                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.calendar1, sumfrag)
                        .addToBackStack(null)
                        .commit();
        //        Log.d("after: ", getSupportFragmentManager().getFragments().toString() + " size: " + getSupportFragmentManager().getFragments().size());
            }

            @Override
            public void onChangeMonth(int month, int year) {
                String text = "month: " + month + " year: " + year;
                Toast.makeText(getApplicationContext(), text,
                        Toast.LENGTH_SHORT).show();
            }
        };
        caldroidFragment.setCaldroidListener(listener);
        t.commit();
    }


}
