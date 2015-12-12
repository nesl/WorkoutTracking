package edu.ucla.nesl.android.workoutdetector;

import android.util.Log;

/**
 * Created by timestring on 11/18/15.
 */
public class AutocorredRepDetector implements IWeightliftingDetector {
    private final int WINDOW_LEN = 100;  // samples
    private final int BUFFER_LEN = 1000;  // samples
    private final int LOOP_LEN = BUFFER_LEN - WINDOW_LEN;
    private final float MIN_AUTOCORR_PEAK_VAL = 0.6f;
    private final int PEAK_MIN_DIS = 25;  // samples
    private final float PROMINENCE = 0.2f;
    private final float JITTER_THRES = 14f;
    private final int AUTO_CORR_DETECTION_STEP = 25;  // samples

    private final int INVALID_DURATION = 0x7ffffff;


    private float[][] cb = new float[BUFFER_LEN][3];  // circular buffer
    private int b_end_idx;  // buffer end index where value to be pushed
    private int b_start_idx;  // buffer start index

    public int last_event_start_idx = 0;
    public int last_event_end_idx = 0;


    @Override
    public void reset() {
        b_end_idx = 0;
        b_start_idx = 0;
        last_event_start_idx = 0;
        last_event_end_idx = 0;
    }

    @Override
    public void input(float x, float y, float z) {
        cb[b_end_idx % BUFFER_LEN][0] = x;
        cb[b_end_idx % BUFFER_LEN][1] = y;
        cb[b_end_idx % BUFFER_LEN][2] = z;
        b_end_idx++;
        if (b_end_idx - b_start_idx == BUFFER_LEN) {
            int result_length = detect(b_start_idx);
            Log.i("AutoDet", "b_start_idx=" + b_start_idx + ", return=" + result_length);
            if (result_length < INVALID_DURATION) {
                int report_end_idx = b_start_idx + detect(b_start_idx);
                if (b_start_idx > last_event_end_idx) {
                    last_event_start_idx = b_start_idx;
                    last_event_end_idx = report_end_idx;
                } else if (report_end_idx > last_event_end_idx) {
                    last_event_end_idx = report_end_idx;
                }
                Log.i("AutoDet", "b_start_idx=" + b_start_idx + ", report=[" + last_event_start_idx
                        + "-" + last_event_end_idx + "]");
            }
            b_start_idx += AUTO_CORR_DETECTION_STEP;
        }
    }


    private float[] auto_corr_score = new float[LOOP_LEN];
    private float[] square_sum = new float[BUFFER_LEN + 1];
    private float[] lminima = new float[LOOP_LEN];
    private float[] rminima = new float[LOOP_LEN];
    private int[] local_peak_idxs = new int[LOOP_LEN];
    private int num_local_peaks;
    private int[] out_peak_idxs = new int[LOOP_LEN];
    private int num_out_peaks;

    /**
     *
     * @param start_idx: starting index of the circular buffer
     */
    private int detect(int start_idx) {
        int out_idx = INVALID_DURATION;

        for (int i_axis = 0; i_axis < 3; i_axis++) {

            // auto correlation
            square_sum[0] = 0f;
            for (int j = 0; j < BUFFER_LEN; j++)
                square_sum[j + 1] = square_sum[j] + cb[(j + start_idx) % BUFFER_LEN][i_axis];
            for (int j = 0; j < LOOP_LEN; j++) {
                float inner_product_sum = 0;
                for (int k = 0; k < WINDOW_LEN; k++) {
                    int idxa = (k + start_idx) % BUFFER_LEN;
                    int idxb = (j + k + start_idx) % BUFFER_LEN;
                    inner_product_sum += cb[idxa][i_axis] * cb[idxb][i_axis];
                }
                float norm_a = (float)Math.sqrt(square_sum[100] - square_sum[0]);
                float norm_b = (float)Math.sqrt(square_sum[j + 100] - square_sum[j]);
                auto_corr_score[j] = inner_product_sum / norm_a / norm_b;
            }

            // peak detection
            lminima[0] = auto_corr_score[0];
            for (int j = 1; j < LOOP_LEN; j++) {
                if (auto_corr_score[j] >= auto_corr_score[j - 1])
                    lminima[j] = lminima[j-1];
                else
                    lminima[j] = auto_corr_score[j];
            }

            rminima[LOOP_LEN - 1] = auto_corr_score[LOOP_LEN - 1];
            for (int j = LOOP_LEN - 2; j >= 0; j--) {
                if (auto_corr_score[j] >= auto_corr_score[j + 1])
                    rminima[j] = rminima[j + 1];
                else
                    rminima[j] = auto_corr_score[j];
            }

            num_local_peaks = 0;
            num_out_peaks = 0;
            for (int j = 1; j < LOOP_LEN - 1; j++) {
                if (auto_corr_score[j] > MIN_AUTOCORR_PEAK_VAL
                        && auto_corr_score[j-1] < auto_corr_score[j]
                        && auto_corr_score[j] <= auto_corr_score[j+1]
                        && (auto_corr_score[j] - lminima[j] > PROMINENCE
                            || auto_corr_score[j] - rminima[j] > PROMINENCE)) {

                    if (num_local_peaks == 0) {
                        local_peak_idxs[num_local_peaks++] = j;
                    } else {
                        if (auto_corr_score[j] > auto_corr_score[local_peak_idxs[num_local_peaks - 1]])
                            local_peak_idxs[num_local_peaks++] = j;
                        // otherwise, idx_j is not a candidate peak because there's a larger peak
                        // at local_peak_idxs[num_local_peaks - 1] or somewhere in next
                        // PEAK_MIN_DIS.
                    }

                    final int sel_marker = 0x00100000;
                    final int mask = sel_marker - 1;
                    if (j - local_peak_idxs[num_local_peaks - 1] >= PEAK_MIN_DIS) {
                        // which means local_peak_idxs[num_local_peaks - 1] definitely become a
                        // qualified peak which satisfies all the conditions, and the previous
                        // queued candidate peaks can be determined accordingly
                        int last_sel_peak_idx = local_peak_idxs[num_local_peaks - 1];
                        local_peak_idxs[num_local_peaks - 1] |= sel_marker;
                        //Log.i("Debug", "i_axis=" + i_axis + ", j=" + j + ", num_local_peaks=" + num_local_peaks);
                        for (int k = num_local_peaks - 2; k >= 0; k--) {
                            if (local_peak_idxs[k] - last_sel_peak_idx > PEAK_MIN_DIS) {
                                last_sel_peak_idx = local_peak_idxs[k];
                                local_peak_idxs[k] |= sel_marker;
                            }
                        }
                        for (int k = 0; k < num_local_peaks; k++)
                            if ((local_peak_idxs[k] & sel_marker) > 0)
                                out_peak_idxs[num_out_peaks++] = local_peak_idxs[k] & mask;
                        num_local_peaks = 0;
                    }
                }
            }

            int sel_num_ele = 0;
            for (int j_num_ele = 4; j_num_ele < num_out_peaks; j_num_ele++) {
                float mean_sum = 0f;
                for (int k = 1; k < j_num_ele; k++) {
                    float d = out_peak_idxs[k] - out_peak_idxs[k - 1];
                    mean_sum += d * d;
                }
                float mean = (float)Math.sqrt(mean_sum / (j_num_ele - 1));
                for (int k = 1; k < j_num_ele; k++) {
                    float v = (out_peak_idxs[k] - out_peak_idxs[k - 1]) - mean;
                    mean_sum += v * v;
                }
                float jitter = (float)Math.sqrt(mean_sum / (j_num_ele - 1));
                if (jitter < JITTER_THRES)
                    sel_num_ele = j_num_ele;
            }
            if (sel_num_ele > 0)
                if (out_peak_idxs[sel_num_ele] < out_idx)
                    out_idx = out_peak_idxs[sel_num_ele];
        }

        return out_idx;
    }
}
