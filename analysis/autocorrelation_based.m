% baseline_rep_counting2.m branches from baseline_rep_detection2.m. The
% algorithm estimates number of reps based on average rep period. 
%
% strategies of improvement:
%  - resolve the bug the algo always miss the last rep
%
% strategies that don't work:
%  - apply median filter

%% house keeping
clc; close all; clear all;
addpath(genpath('./'))

%% start of the algorithm
manager = get_task_manager();

type_to_keep = TYPE.get_standard_types();
num_types = numel(type_to_keep);
manager.keep_types(type_to_keep);
manager.remove_fragile_tasks();

manager.report_by_workout_type();


% TODO: include more non-workout time in-between
% Threashold for determing repetition from auto-correlation result
DONT_CARE_SEC = 3;
% Window for auto correlation
AC_WLEN = 100;  % samples
FLAG_SHIFT_ZERO_MEAN = true;
% threshold
MIN_AUTOCORR_PEAK_VAL = 0.6;
PEAK_MIN_DIS_SEC = 1;
NUM_AXES = 1;  % number of axes have to be significant
PROMINENCE = 0.2;  % distance between peak and minima
PEAK_DETECTION_WIN_SEC = 40;
PEAK_DETECTION_WIN_SPL = PEAK_DETECTION_WIN_SEC * 25;  % num samples
JITTER_THRES = 14;


TO_PLOT = false;


% Stats of baseline detection algorithm
total_len = 0;
tp_sec = 0;
tn_sec = 0;
fp_sec = 0;
fn_sec = 0;

tp_set = 0;
fp_set = 0;
fn_set = 0;

rep_errors = [];

% Disaggregation states
contributor_list = {'B', 'C', 'P', 'S', 'M'};
num_contributors = numel(contributor_list);
contributor_map = containers.Map(contributor_list, 1:num_contributors);

total_set_breakdown = zeros(num_types, num_contributors);
tp_set_breakdown = zeros(num_types, num_contributors);
fp_set_contributor = zeros(1, num_contributors);

num_rep_breakdown = cell(num_types, num_contributors);
rep_error_breakdown = cell(num_types, num_contributors);


for task_idx = 1 : numel(manager.tasks)
%for task_idx = 11:11
    cur_task = manager.tasks{task_idx};
    fprintf('task_idx=%d, contributor=%s, set=%d \n', ...
        task_idx, cur_task.contributor, numel(cur_task.sets));
    [~, ~, gravacc, ~, ~, ~] = cur_task.get_sensor_data();
    contributor_idx = contributor_map(cur_task.contributor);
    
    % now ac_gravacc_agg has different length compared with gravacc_agg,
    % the total length in second has to be changed accordingly
    gravacc_num_row = size(gravacc, 1);
    total_sec = ceil(gravacc(end, 1));
    cur_sec = floor(gravacc(gravacc_num_row - AC_WLEN, 1));
    
    mask = true(total_sec, 1);
    rep_gc = false(total_sec, 1);
    rep_pr = false(total_sec, 1);
    rep_num_gc = zeros(total_sec, 1);
    rep_period_sum = zeros(total_sec, 1);
    rep_period_cnt = zeros(total_sec, 1);
    
    if TO_PLOT
        h1 = subplot(2, 1, 1);
        hold on
        plot(gravacc(:, 1), gravacc(:, 2), 'r');
        plot(gravacc(:, 1), gravacc(:, 3), 'g');
        plot(gravacc(:, 1), gravacc(:, 4), 'b');

        h2 = subplot(2, 1, 2);
        linkaxes([h1, h2], 'x');
    end
    
    for i = 1 : numel(cur_task.sets)
        % Get ground truth
        cur_set = cur_task.sets(i);
        st = max(1, cur_set.start_sec);
        et = min(total_sec, cur_set.stop_sec);
        rep_gc(st:et) = true;
        rep_num_gc(st:et) = cur_set.num_reps;
        
        % set masks
        a = max(1, st - DONT_CARE_SEC);
        b = min(total_sec, st + DONT_CARE_SEC);
        mask(a:b) = false;
        
        a = max(1, et - DONT_CARE_SEC);
        b = min(total_sec, et + DONT_CARE_SEC);
        mask(a:b) = false;
        
        fprintf('   ground truth set %d: time=[%.1f,%.1f] (%s)\n', ...
            i, st, et, TYPE.get_name(cur_set.type));
    end

        
    for t = 1 : cur_sec
        % Get predicted result
        % Check this second's result from auto correlation
        %idx = gravacc(:, 1) <= t & gravacc(:, 1) < t + 1;
        start_idx = find(gravacc(:, 1) > (t - 1));
        start_idx = start_idx(1);
        end_idx = min(size(gravacc, 1), start_idx + PEAK_DETECTION_WIN_SPL);
        
        if TO_PLOT
            hold off
            plot(0, 0)
            hold on
        end
        
        session_tails = [];
        session_rep_periods = [];
        session_quality = [];
        for axis_idx = 2:4
            fused_series = gravacc(start_idx:end_idx, axis_idx);
            %fused_series = medfilt1(gravacc(start_idx:end_idx, axis_idx), 3);
            
            if FLAG_SHIFT_ZERO_MEAN
                u = mean(fused_series(1:AC_WLEN));
                fused_series = fused_series - u;
            end
            
            ac_gravacc_agg = autocorr2(fused_series, AC_WLEN);
            [vals, locs] = findpeaks(ac_gravacc_agg, ...
                'MinPeakDistance', round(PEAK_MIN_DIS_SEC * 25), ...
                'MinPeakHeight', MIN_AUTOCORR_PEAK_VAL);

            % calculate prominence
            lminima = zeros(size(ac_gravacc_agg));
            lminima(1) = ac_gravacc_agg(1);
            for i = 2:numel(ac_gravacc_agg)
                if ac_gravacc_agg(i) >= ac_gravacc_agg(i-1)
                    lminima(i) = lminima(i-1);
                else
                    lminima(i) = ac_gravacc_agg(i);
                end
            end

            rminima = zeros(size(ac_gravacc_agg));
            rminima(end) = ac_gravacc_agg(end);
            for i = fliplr(1:(numel(ac_gravacc_agg) - 1))
                if ac_gravacc_agg(i) >= ac_gravacc_agg(i+1)
                    rminima(i) = rminima(i+1);
                else
                    rminima(i) = ac_gravacc_agg(i);
                end
            end

            %[lminima ac_gravacc_agg rminima]

            rminima_sel = rminima(locs);
            lminima_sel = lminima(locs);
            large_prominence_idx = (vals - min(rminima_sel, lminima_sel) > PROMINENCE);

            vals = vals(large_prominence_idx);
            locs = locs(large_prominence_idx);

            if TO_PLOT
                line_style = {'', 'r', 'g', 'b'};
                ac_end_idx = start_idx + numel(ac_gravacc_agg) - 1;
                plot(gravacc(start_idx:ac_end_idx, 1), ac_gravacc_agg, line_style{axis_idx});
                ylim([-1 1])
            end
            
            detected_num_peak_gaps = 0;
            detected_idx = 0;
            %jitters = nan(size(locs(first_few_peaks)));
            for i = 4 : numel(locs)
                diffs = diff([0; locs(1:i)]);
                %diffs = diff(locs(1:i));
                u = rms(diffs);
                jitter = rms(diffs - u);
                %[i jitter]
                if jitter < JITTER_THRES
                    detected_num_peak_gaps = i;
                    detected_idx = locs(i);
                else
                    break
                end
            end
            
            
            if detected_num_peak_gaps > 0
                session_end_idx = start_idx + detected_idx - 1;
                session_end_sec = gravacc(session_end_idx, 1);
                dt = session_end_sec - t;
                rep_period = dt / detected_num_peak_gaps;
                session_rep_periods(end+1) = rep_period;
                %session_tails(end+1) = session_end_sec + session_reps(end);
                session_tails(end+1) = session_end_sec;
                %session_quality(end+1) = sum(vals(1:detected_num_reps) .* 2);
                session_quality(end+1) = rms(vals(1:detected_num_peak_gaps) .* 2) * dt * rep_period;
            end
        end
        
        if numel(session_tails) >= 1
            %session_rep_periods
            %session_tails
            %session_quality
            
            a = t;
            
            %[end_sec, s_idx] = min(session_tails + session_reps);
            [~, s_idx] = max(session_quality);
            %end_sec = session_tails(s_idx);
            end_sec = session_tails(s_idx) + session_rep_periods(s_idx);
            %end_sec = session_tails(s_idx) + AC_WLEN / 25;
            %fprintf('debug: %.3f\n', session_rep_periods(s_idx));
            
            b = round(min(total_sec, end_sec));
            
            if TO_PLOT
                [a b session_rep_periods(s_idx)]
            end
            
            rep_pr(a:b) = true;
            weight = session_quality(s_idx);
            rep_period_sum(a:b) = rep_period_sum(a:b) + session_rep_periods(s_idx) * weight;
            rep_period_cnt(a:b) = rep_period_cnt(a:b) + weight;
        end
        
        if TO_PLOT
            xlabel(['sec=' num2str(t)])
            %pause(0.1)
            pause
        end
    end
    
    rep_period = rep_period_sum ./ rep_period_cnt;
    
    % evaluate set matches
    events = diff([0; rep_gc; 0]);
    gc_set_start = find(events == 1);
    gc_set_stop = find(events == -1) - 1;
    events = diff([0; rep_pr; 0]);
    pr_set_start = find(events == 1);
    pr_set_stop = find(events == -1) - 1;
    
    set_marked = false(size(gc_set_start));
    cur_fp_set = 0;
    for i_pr = 1:length(pr_set_start)
        gc_sel_idx = 0;
        gc_sel_length = 0;
        for i_gc = 1:length(gc_set_start)
            cover_start = max(pr_set_start(i_pr), gc_set_start(i_gc));
            cover_stop = min(pr_set_stop(i_pr), gc_set_stop(i_gc));
            time_diff = cover_stop - cover_start;
            if time_diff > gc_sel_length
                gc_sel_length = time_diff;
                gc_sel_idx = i_gc;
            end
        end
        
        if gc_sel_length > 0
            set_marked(gc_sel_idx) = true;
            gnd_set = cur_task.sets(gc_sel_idx);
            gnd_type = gnd_set.type;
            
            gnd_num_reps = rep_num_gc(gc_set_start(gc_sel_idx));
            
            idxs = pr_set_start(i_pr):pr_set_stop(i_pr);
            set_time = pr_set_stop(i_pr) - pr_set_start(i_pr);
            
            % method 1: use average rep time, derived by rms
            est_avg_rep_time = rms(rep_period(idxs));
            est_num_reps = round(set_time / est_avg_rep_time);
            
            % method 2: use sum of 1/period
            %est_num_reps = round(sum(1 ./ rep_period(idxs)));
            
            % method 3: use average rep time, derived by weighted rms
            % log version of est_avg_rep_time
            % = prod(rep_period(idxs) .^ rep_period_cnt(idxs)) .^ (1 / sum(rep_period_cnt(idxs)));
            %log_est_avg_rep_time = sum(log(rep_period(idxs)) .* rep_period_cnt(idxs)) / sum(rep_period_cnt(idxs));
            %est_avg_rep_time = exp(log_est_avg_rep_time);
            %est_num_reps = round(set_time / est_avg_rep_time);
            
            rep_diff = est_num_reps - gnd_num_reps;
            rep_errors(end+1) = rep_diff;
            num_rep_breakdown{gnd_type, contributor_idx}(end+1) = gnd_num_reps;
            rep_error_breakdown{gnd_type, contributor_idx}(end+1) = rep_diff;
            
            fprintf('   esti: %d-%d=%d reps [%.1f, %.1f]\n', est_num_reps, gnd_num_reps, rep_diff, ...
                    pr_set_start(i_pr), pr_set_stop(i_pr));
        else
            cur_fp_set = cur_fp_set + 1;
            fp_set_contributor(contributor_idx) = fp_set_contributor(contributor_idx) + 1;
        end
    end
    cur_tp_set = sum(set_marked);
    
    for i = 1:numel(cur_task.sets)
        type = cur_task.sets(i).type;
        if set_marked(i)
            tp_set_breakdown(type, contributor_idx) = tp_set_breakdown(type, contributor_idx) + 1;
        end
        total_set_breakdown(type, contributor_idx) = total_set_breakdown(type, contributor_idx) + 1;
    end
    
    cur_fn_set = 0;
    for i = 1:numel(cur_task.sets)
        if ~set_marked(i) && cur_task.sets(i).num_reps >= 4
            cur_fn_set = cur_fn_set + 1;
        end
    end
    
    % remove don't care part
    rep_gc = rep_gc(mask);
    rep_pr = rep_pr(mask);
    
    % accuracy statistics
    cur_tp_sec = sum( rep_gc &  rep_pr);
    cur_tn_sec = sum(~rep_gc & ~rep_pr);
    cur_fp_sec = sum(~rep_gc &  rep_pr);
    cur_fn_sec = sum( rep_gc & ~rep_pr);
    
    % overall statistics
    tp_sec = tp_sec + cur_tp_sec;
    tn_sec = tn_sec + cur_tn_sec;
    fp_sec = fp_sec + cur_fp_sec;
    fn_sec = fn_sec + cur_fn_sec;
    total_len = total_len + sum(rep_gc);
    
    tp_set = tp_set + cur_tp_set;
    fp_set = fp_set + cur_fp_set;
    fn_set = fn_set + cur_fn_set;
    
    fprintf('Second: tp=%d, tn=%d, fp=%d, fn=%d; Set: tp=%d, fp=%d, fn=%d\n', ...
        cur_tp_sec, cur_tn_sec, cur_fp_sec, cur_fn_sec, ...
        cur_tp_set, cur_fp_set, cur_fn_set);
    %rep_errors
end


%% statistics
prec_sec = tp_sec / (tp_sec + fp_sec);
recl_sec = tp_sec / (tp_sec + fn_sec);

total_set = tp_set + fn_set;
prec_set = tp_set / (tp_set + fp_set);
recl_set = tp_set / (tp_set + fn_set);
total_avg_abs_rep_error = mean(abs(rep_errors));

fprintf('\n');
fprintf('====== Overall report ======\n');
fprintf('total_sec=%d, prec=%.3f, recl=%.3f (tp=%d, tn=%d, fp=%d, fn=%d)\n', ...
    total_len, prec_sec, recl_sec, tp_sec, tn_sec, fp_sec, fn_sec);
fprintf('total set=%d, prec=%.3f, recl=%.3f (tp=%d, fp=%d, fn=%d)\n', ...
    total_set, prec_set, recl_set, tp_set, fp_set, fn_set);
fprintf('rep counting abs error=%.3f\n', total_avg_abs_rep_error);

fprintf('\n');
fprintf('====== Report by contributor ======\n');
contributor_set_prec = zeros(num_contributors, 1);
contributor_set_recl = zeros(num_contributors, 1);
contributor_rep_abs_error = zeros(num_contributors, 1);
for i_contri = 1:num_contributors
    tp = sum(tp_set_breakdown(:, i_contri));
    fp = fp_set_contributor(i_contri);
    total_sets = sum(total_set_breakdown(:, i_contri));
    fn = total_sets - tp;
    prec = tp / (tp + fp);
    recl = tp / (tp + fn);
    fprintf('user %d: total set=%d, prec=%.3f, recl=%.3f (tp=%d, fp=%d, fn=%d)\n', ...
        i_contri, total_sets, prec, recl, tp, fp, fn);
    abs_mean_rep_cnt_error = mean(abs([rep_error_breakdown{:, i_contri}]));
    fprintf('         rep counting abs error=%.3f\n', abs_mean_rep_cnt_error);
    contributor_set_prec(i_contri) = prec;
    contributor_set_recl(i_contri) = recl;
    contributor_rep_abs_error(i_contri) = abs_mean_rep_cnt_error;
end

fprintf('\n');
fprintf('====== Report by type ======\n');
type_set_det_rate = zeros(num_types, 1);
type_abs_rep_error = zeros(num_types, 1);
type_total_rep_error = zeros(num_types, 1);
for i_type = 1:num_types
    tp = sum(tp_set_breakdown(i_type, :));
    total_sets = sum(total_set_breakdown(i_type, :));
    fn = total_sets - tp;
    recl = tp / (tp + fn);
    fprintf('type %2d: total set=%d, recl=%.3f (tp=%d, fn=%d) [%s]\n', ...
        i_type, total_sets, recl, tp, fn, TYPE.get_name(i_type));
    abs_mean_rep_cnt_error = mean(abs([rep_error_breakdown{i_type, :}]));
    abs_aggregate_rep_cnt_error = ...
        sum([rep_error_breakdown{i_type, :}]) / sum([num_rep_breakdown{i_type, :}]);
    fprintf('         rep counting abs error=%.3f, aggregation error=%.3f\n', ...
        abs_mean_rep_cnt_error, abs_aggregate_rep_cnt_error);
    type_set_det_rate(i_type) = recl;
    type_abs_rep_error(i_type) = abs_mean_rep_cnt_error;
    type_total_rep_error(i_type) = abs_aggregate_rep_cnt_error;
end

fprintf('\n');
fprintf('====== Detail ======\n');
fprintf('[set detection rate (recall)]\n');
tp_set_breakdown ./ total_set_breakdown

fprintf('\n[rep counting abs error]\n');
f_mean_abs = @(x) (mean(abs(x)));
cellfun(f_mean_abs, rep_error_breakdown)

fprintf('\n[number of sets]\n');
total_set_breakdown

%rep_errors


%% results to store in the file for figure generation

PLAIN_RESULT_OUT_DIR = 'plain_result_for_drawing/';

dlmwrite([PLAIN_RESULT_OUT_DIR 'auto_set_pr_by_user.csv'], ...
    [contributor_set_prec, contributor_set_recl; prec_set, recl_set], ...
    'delimiter', ',', 'precision', 6);
dlmwrite([PLAIN_RESULT_OUT_DIR 'auto_rep_err_by_user.csv'], ...
    [contributor_rep_abs_error; total_avg_abs_rep_error], ...
    'delimiter', ',', 'precision', 6);
dlmwrite([PLAIN_RESULT_OUT_DIR 'auto_set_det_rate_by_type.csv'], ...
    type_set_det_rate, ...
    'delimiter', ',', 'precision', 6);
dlmwrite([PLAIN_RESULT_OUT_DIR 'auto_rep_err_by_type.csv'], ...
    [type_abs_rep_error, type_total_rep_error], ...
    'delimiter', ',', 'precision', 6);

return

%% plot grav data
hold on
plot(gravacc(:, 1), gravacc(:, 2), 'r');
plot(gravacc(:, 1), gravacc(:, 3), 'g');
plot(gravacc(:, 1), gravacc(:, 4), 'b');
hold off


%% check whether all sets in each task have the same type
for task_idx = 1 : numel(manager.tasks)
    cur_task = manager.tasks{task_idx};
    if numel(cur_task.sets) > 1
        for i = 2:numel(cur_task.sets)
            if cur_task.sets(1).type ~= cur_task.sets(i).type
                % expect not printing the following lines
                [task_idx cur_task.sets(1).type cur_task.sets(i).type]
            end
        end
    end
end

%% extract task index
tmp_manager = get_task_manager();

type_to_keep = TYPE.get_standard_types();
num_types = numel(type_to_keep);
tmp_manager.keep_types(type_to_keep);
tmp_manager.remove_fragile_tasks();
tmp_manager.indexing();
[task_idx, set_idx] = tmp_manager.query_by_contributor_type('M', 10, 4)

%%
% jitter=10:  rep error=1.130
% jitter=12:  rep error=1.081
% jitter=13:  rep error=1.056 (<-)
% jitter=14:  rep error=1.089
% jitter=15:  rep error=1.073
% jitter=20:  rep error=1.127