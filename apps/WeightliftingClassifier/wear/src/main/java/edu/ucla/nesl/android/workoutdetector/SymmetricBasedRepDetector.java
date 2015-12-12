package edu.ucla.nesl.android.workoutdetector;

import android.util.Log;

/**
 * Created by timestring on 11/19/15.
 */
public class SymmetricBasedRepDetector implements IWeightliftingDetector {
    private final int NUM_BIN_IN_1D = (1 << 5);
    private final int CUBE_SIZE = NUM_BIN_IN_1D * NUM_BIN_IN_1D * NUM_BIN_IN_1D;
    private final int HISTORY_SIZE = (1 << 14);
    private final int HISTORY_SIZE_MASK = HISTORY_SIZE - 1;

    private final int VAR_WINDOW_SIZE = 50;  // samples
    private final float VAR_WINDOW_THRES = 2f;

    private final float MIN_GRAV_VAL = -10f;
    private final float MAX_GRAV_VAL = 10f;
    private final float GRAV_DELTA = MAX_GRAV_VAL - MIN_GRAV_VAL;
    private final float GRAV_SCALE = GRAV_DELTA / NUM_BIN_IN_1D;

    private final int MAX_VISITING_TIME = 150;  // samples

    private final int END_EVENT_STEP = 5;  // samples
    private final int END_EVENT_CNT_SIZE = (1 << 10);
    private final int END_EVENT_CNT_SIZE_MASK = END_EVENT_CNT_SIZE - 1;
    private final int END_EVENT_BIN_THRES = 4;
    private final int MAX_END_EVENT_GAP = 175;  // samples

    private int[] end_event_cnts = new int[END_EVENT_CNT_SIZE];
    private int earliest_end_event_time;
    private int latest_end_event_time;

    private int[] visited_time;
    private float[][] sensor_history;
    private int sensor_timestamp;

    private float SENSOR_VAL_EPS = 0.2f;
    private float[] lmaxima = new float[HISTORY_SIZE];
    private float[] rmaxima = new float[HISTORY_SIZE];

    private int last_cube_idx;
    private int last_last_cube_idx;

    public int num_reps;

    @Override
    public void reset() {
        visited_time = new int[CUBE_SIZE];
        sensor_history = new float[HISTORY_SIZE][3];

        // initialize sensor_timestamp as a non-zero value because we treat zeros in visited_time
        // as non-visited cube. also this has to be multiple times of VAR_WINDOW_SIZE since we use
        // current sensor_timestamp for reaching the window boundary.
        sensor_timestamp = VAR_WINDOW_SIZE * 10;

        // any valid cube index will be positive values or zero. we use negative values to indicate
        // that variables are not set
        last_cube_idx = -1;
        last_last_cube_idx = -1;

        earliest_end_event_time = 0x7ffffff;
        latest_end_event_time = 0;
    }

    @Override
    public void input(float x, float y, float z) {
        sensor_history[sensor_timestamp & HISTORY_SIZE_MASK][0] = x;
        sensor_history[sensor_timestamp & HISTORY_SIZE_MASK][1] = y;
        sensor_history[sensor_timestamp & HISTORY_SIZE_MASK][2] = z;
        sensor_timestamp++;

        // if we haven't reached the window boundary, then wait a minute
        if (sensor_timestamp % VAR_WINDOW_SIZE != 0)
            return;


        // compute variance of the current window
        float x_sum = 0f;
        float x_square_sum = 0f;
        float y_sum = 0f;
        float y_square_sum = 0f;
        float z_sum = 0f;
        float z_square_sum = 0f;
        for (int i = -VAR_WINDOW_SIZE; i < 0; i++) {
            int t_idx = (sensor_timestamp + i) & HISTORY_SIZE_MASK;
            float v = sensor_history[t_idx][0];
            x_sum += v;
            x_square_sum += v * v;
            v = sensor_history[t_idx][1];
            y_sum += v;
            y_square_sum += v * v;
            v = sensor_history[t_idx][2];
            z_sum += v;
            z_square_sum += v * v;
        }
        float x_mean = x_sum / VAR_WINDOW_SIZE;
        float x_square_mean = x_square_sum / VAR_WINDOW_SIZE;
        float y_mean = y_sum / VAR_WINDOW_SIZE;
        float y_square_mean = y_square_sum / VAR_WINDOW_SIZE;
        float z_mean = z_sum / VAR_WINDOW_SIZE;
        float z_square_mean = z_square_sum / VAR_WINDOW_SIZE;
        float win_var = (x_square_mean - x_mean * x_mean)
                + (y_square_mean - y_mean * y_mean)
                + (z_square_mean - z_mean * z_mean);

        Log.i("SymDet", "win_var " + win_var);

        // if the variance in this window is not big enough, then discard all the visiting events in
        // this window.
        if (win_var < VAR_WINDOW_THRES)
            return;

        for (int i = -VAR_WINDOW_SIZE; i < 0; i++) {
            // get cube index and reshape to one dimension. span the code to increase the
            // performance.
            int cur_time = sensor_timestamp + i;
            int t_idx = cur_time & HISTORY_SIZE_MASK;

            int xidx = (int) ((sensor_history[t_idx][0] - MIN_GRAV_VAL) / GRAV_SCALE);
            if (xidx < 0)
                xidx = 0;
            if (xidx >= NUM_BIN_IN_1D)
                xidx = NUM_BIN_IN_1D - 1;

            int yidx = (int) ((sensor_history[t_idx][1] - MIN_GRAV_VAL) / GRAV_SCALE);
            if (yidx < 0)
                yidx = 0;
            if (yidx >= NUM_BIN_IN_1D)
                yidx = NUM_BIN_IN_1D - 1;

            int zidx = (int) ((sensor_history[t_idx][2] - MIN_GRAV_VAL) / GRAV_SCALE);
            if (zidx < 0)
                zidx = 0;
            if (zidx >= NUM_BIN_IN_1D)
                zidx = NUM_BIN_IN_1D - 1;

            // generate visiting event
            int cur_cube_idx = (xidx << 10) | (yidx << 5) | zidx;
            if (cur_cube_idx != last_cube_idx && cur_cube_idx != last_last_cube_idx) {
                int dt = cur_time - visited_time[cur_cube_idx];
                //Log.i("SymDet", "dt=" + dt);
                visited_time[cur_cube_idx] = cur_time;
                last_last_cube_idx = last_cube_idx;
                last_cube_idx = cur_cube_idx;
                if (dt < MAX_VISITING_TIME) {
                    // generate end event
                    int end_event_time = cur_time - dt / 2;
                    int end_event_bin_idx = end_event_time / END_EVENT_STEP;

                    // now we assume the bin size of end event is 2x of END_EVENT_STEP
                    int bin_idx1 = end_event_bin_idx & END_EVENT_CNT_SIZE_MASK;
                    end_event_cnts[bin_idx1]++;
                    int bin_idx2 = (end_event_bin_idx + 1) & END_EVENT_CNT_SIZE_MASK;
                    end_event_cnts[bin_idx2]++;
                    if (end_event_cnts[bin_idx1] == END_EVENT_BIN_THRES
                            || end_event_cnts[bin_idx2] == END_EVENT_BIN_THRES) {
                        if (end_event_time < earliest_end_event_time)
                            earliest_end_event_time = end_event_time;
                        if (end_event_time > latest_end_event_time)
                            latest_end_event_time = end_event_time;
                        //Log.i("SymDet", "detect " + end_event_time);
                    }
                }
            }
        }

        // if no more end event, then calculate reps


        if (sensor_timestamp - latest_end_event_time > MAX_END_EVENT_GAP) {

            // if we have enough number of end events
            if (latest_end_event_time - earliest_end_event_time > 50) {
                earliest_end_event_time -= 125;
                latest_end_event_time += 125;
                int mid_time = (latest_end_event_time + earliest_end_event_time) / 2;

                Log.i("SymDet", "calculating from " + earliest_end_event_time + " to " + latest_end_event_time);

                int monitor_start_time = mid_time - 125;
                int monitor_end_time = mid_time + 125;
                float x_max = -10f;
                float x_min = 10f;
                float y_max = -10f;
                float y_min = 10f;
                float z_max = -10f;
                float z_min = 10f;
                for (int i = monitor_start_time; i < monitor_end_time; i += 5) {
                    int t_idx = i & HISTORY_SIZE_MASK;
                    if (sensor_history[t_idx][0] > x_max)
                        x_max = sensor_history[t_idx][0];
                    if (sensor_history[t_idx][0] < x_min)
                        x_min = sensor_history[t_idx][0];
                    if (sensor_history[t_idx][1] > y_max)
                        y_max = sensor_history[t_idx][1];
                    if (sensor_history[t_idx][1] < y_min)
                        y_min = sensor_history[t_idx][1];
                    if (sensor_history[t_idx][2] > z_max)
                        z_max = sensor_history[t_idx][2];
                    if (sensor_history[t_idx][2] < z_min)
                        z_min = sensor_history[t_idx][2];
                }
                float fall_dis = x_max - x_min;
                int axis_idx = 0;
                float tmp_dis = y_max - y_min;
                if (tmp_dis > fall_dis) {
                    fall_dis = tmp_dis;
                    axis_idx = 1;
                }
                tmp_dis = z_max - z_min;
                if (tmp_dis > fall_dis) {
                    fall_dis = tmp_dis;
                    axis_idx = 2;
                }

                float prominence = 4f;
                float poss_prominence = fall_dis * 0.6f;
                if (poss_prominence < prominence)
                    prominence = poss_prominence;

                lmaxima[monitor_start_time & HISTORY_SIZE_MASK]
                        = sensor_history[monitor_start_time & HISTORY_SIZE_MASK][axis_idx];
                float now_v = sensor_history[monitor_start_time & HISTORY_SIZE_MASK][axis_idx];
                for (int i = monitor_start_time + 1; i < monitor_end_time; i++) {
                    if (sensor_history[i & HISTORY_SIZE_MASK][axis_idx] >= now_v - SENSOR_VAL_EPS) {
                        lmaxima[i & HISTORY_SIZE_MASK] = lmaxima[(i - 1) & HISTORY_SIZE_MASK];
                        if (sensor_history[i & HISTORY_SIZE_MASK][axis_idx] > now_v)
                            now_v = sensor_history[i & HISTORY_SIZE_MASK][axis_idx];
                    }
                    else {
                        lmaxima[i & HISTORY_SIZE_MASK] = sensor_history[i & HISTORY_SIZE_MASK][axis_idx];
                        now_v = sensor_history[i & HISTORY_SIZE_MASK][axis_idx];
                    }
                }

                rmaxima[(monitor_end_time - 1) & HISTORY_SIZE_MASK]
                        = sensor_history[(monitor_end_time - 1) & HISTORY_SIZE_MASK][axis_idx];
                now_v = sensor_history[(monitor_end_time - 1) & HISTORY_SIZE_MASK][axis_idx];
                for (int i = monitor_end_time - 2; i >= monitor_start_time; i--) {
                    if (sensor_history[i & HISTORY_SIZE_MASK][axis_idx] >= now_v - SENSOR_VAL_EPS) {
                        rmaxima[i & HISTORY_SIZE_MASK] = rmaxima[(i + 1) & HISTORY_SIZE_MASK];
                        if (sensor_history[i & HISTORY_SIZE_MASK][axis_idx] > now_v)
                            now_v = sensor_history[i & HISTORY_SIZE_MASK][axis_idx];
                    }
                    else {
                        rmaxima[i & HISTORY_SIZE_MASK] = sensor_history[i & HISTORY_SIZE_MASK][axis_idx];
                        now_v = sensor_history[i & HISTORY_SIZE_MASK][axis_idx];
                    }
                }

                int max_peak_idx = 0;
                for (int i = monitor_start_time; i < monitor_end_time; i++) {
                    if ((sensor_history[(i-1) & HISTORY_SIZE_MASK][axis_idx]
                            <= sensor_history[i & HISTORY_SIZE_MASK][axis_idx])
                            && (sensor_history[i & HISTORY_SIZE_MASK][axis_idx]
                            <= sensor_history[(i+1) & HISTORY_SIZE_MASK][axis_idx])
                            && (sensor_history[i & HISTORY_SIZE_MASK][axis_idx] - lmaxima[i & HISTORY_SIZE_MASK] > prominence
                                || sensor_history[i & HISTORY_SIZE_MASK][axis_idx] - rmaxima[i & HISTORY_SIZE_MASK] > prominence)) {
                        max_peak_idx = i;
                    }
                }
                num_reps = max_peak_idx;
            }

            // clean whatever we have
            earliest_end_event_time = 0x7ffffff;
            latest_end_event_time = 0;
        }
    }


}
