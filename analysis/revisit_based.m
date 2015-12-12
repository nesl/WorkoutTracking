% This script is copied and modified from
% cube_visit_testing_script_session_v3.m. This script is going to revise
% the rep counting mechanism, when more new users participate, the rep
% counting error increases. Also, this script provides breakdown analysis.
%
% Strategies of improvements
%   - Take median filter on the signal
%   - Loose the prominence requirement
%   - filter out short sessions
%   - if the rep length is too small, divide the answer by 2


%% Housekeeping
clc; close all; clear all;
add_paths

manager = get_task_manager();

type_to_keep = TYPE.get_standard_types();
num_types = numel(type_to_keep);
manager.keep_types(type_to_keep);
manager.remove_fragile_tasks();

manager.report_by_workout_type();




MIN_VAL = -30;
MAX_VAL = 30;

TO_PLOT = false;

RESV = 0.6;
knob_var = 2;

MAX_END_EVENT_TIME = 6;  % sec;


HISTORY_LENGTH = 2;
num_cell_per_edge = ceil((MAX_VAL - MIN_VAL) / RESV) + 1;
f_grav_idx_to_id = @(x, y, z) (x * (num_cell_per_edge ^ 2) + y * num_cell_per_edge + z);


% constants for computing event rate generation
T_WIN_SIZE = 4;
T_WIN_STEP = 0.25;

% constants for computing resticted event rate generation
R_WIN_SIZE = 10;
R_WIN_STEP = 1;

REP_MIN_PERIOD = 1.4;

% Stats of cube-visiting detection algorithm
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


for task_idx = 1:numel(manager.tasks)
%for task_idx = 109:113
    fprintf('Task idx=%d\n', task_idx);
    
    cur_task = manager.tasks{task_idx};
    [~, ~, gravacc, ~, ~, ~] = cur_task.get_sensor_data();
    contributor_idx = contributor_map(cur_task.contributor);
    
    short_history = zeros(HISTORY_LENGTH, 1);
    
    cubes = nan(num_cell_per_edge, num_cell_per_edge, num_cell_per_edge);

    time_difference = nan(size(gravacc, 1), 2);
    time_difference(:, 1) = gravacc(:, 1);

    for i = 1:size(gravacc, 1)
        xidx = min(max(ceil((gravacc(i, 2) - MIN_VAL) / RESV), 1), num_cell_per_edge);
        yidx = min(max(ceil((gravacc(i, 3) - MIN_VAL) / RESV), 1), num_cell_per_edge);
        zidx = min(max(ceil((gravacc(i, 4) - MIN_VAL) / RESV), 1), num_cell_per_edge);
        cur_idx = f_grav_idx_to_id(xidx, yidx, zidx);
        if ~any(short_history == cur_idx)
            time_difference(i, 2) = gravacc(i, 1) - cubes(xidx, yidx, zidx);
            cubes(xidx, yidx, zidx) = gravacc(i, 1);
            short_history(2:end) = short_history(1:(end-1));
            short_history(1) = cur_idx;
        end
    end


    % calulate variance for each window
    WINDOW_SIZE_VAR = 2;  % sec
    num_win = floor(gravacc(end, 1) / WINDOW_SIZE_VAR);
    var_wins = zeros(num_win, 1);
    for i = 1:num_win
        start_time = (i-1) * WINDOW_SIZE_VAR;
        stop_time = i * WINDOW_SIZE_VAR;
        idx = start_time < gravacc(:, 1) & gravacc(:, 1) < stop_time;
        center = mean(gravacc(idx, 2:4));
        var_xyz = var(gravacc(idx, 2:4) - repmat(center, sum(idx), 1));
        var_wins(i) = norm(var_xyz);
    end

    % filter by small variance
    for i = 1:numel(time_difference(i, 1))
        if time_difference(i, 1) > num_win * WINDOW_SIZE_VAR % strip the tail
            time_difference(i, 2) = NaN;
        else
            idx = ceil(time_difference(i, 1) / WINDOW_SIZE_VAR);
            if var_wins(idx) < knob_var
                time_difference(i, 2) = NaN;
            end
        end
    end

    idx = ~isnan(time_difference(:, 2)) & time_difference(:, 2) < MAX_END_EVENT_TIME;
    time_difference = time_difference(idx, :);
    time_difference(:, 3) = time_difference(:, 1) - time_difference(:, 2) / 2;



    % applying windows to cluster events
    WINDOW_SIZE_EVENT = 0.2;  % sec
    WINDOW_STEP_EVENT = 0.1;  % sec
    EVENT_THRES = 4;

    num_wins = floor( ...
        (gravacc(end, 1) - WINDOW_SIZE_EVENT) / WINDOW_STEP_EVENT);

    % indexes of events which can be grouped by end events
    grouped = false(size(time_difference, 1), 1);

    end_events = [];
    for i = 1:num_wins
        start_time = (i-1) * WINDOW_STEP_EVENT;
        stop_time = start_time + WINDOW_SIZE_EVENT;
        idx = start_time <= time_difference(:, 3) & time_difference(:, 3) < stop_time;
        if sum(idx) >= EVENT_THRES && var(time_difference(idx, 2)) > 0.3
            grouped(idx) = true;
            %fprintf('find i=%d\n', i)
            end_events(end+1) = i * WINDOW_STEP_EVENT;
        end
    end

    if TO_PLOT
        h1 = subplot(2, 1, 1);
        plot(time_difference(grouped, 1), time_difference(grouped, 2), 'r*');
        hold on
        plot(time_difference(~grouped, 1), time_difference(~grouped, 2), 'k*');
    end

    % approximate rep period
    p_time_diff = time_difference(~grouped, 1:2);
    period_est = zeros(size(p_time_diff));
    period_est(:, 1) = p_time_diff(:, 1);
    period_est(1, 2) = p_time_diff(1, 2);
    for i = 2:size(period_est, 1)
        alpha = max(0, 0.75 - (p_time_diff(i) - p_time_diff(i-1)) * 0.5);
        period_est(i, 2) = period_est(i-1, 2) * alpha + p_time_diff(i, 2) * (1-alpha);
    end

    if TO_PLOT
        plot(period_est(:, 1), period_est(:, 2), 'b-o')
        h2 = subplot(2, 1, 2);
        plot(gravacc(:, 1), gravacc(:, 2), 'r');
        hold on
        plot(gravacc(:, 1), gravacc(:, 3), 'g');
        plot(gravacc(:, 1), gravacc(:, 4), 'b');
        linkaxes([h1, h2], 'x');
    end

    % an end event is approximated by a line with slope 2, but because
    % the user can do a little bit faster/slower than the symmetric
    % rep, several adjacent lines can have large responses. We combine
    % these lines together.
    tmp_end_events = end_events;
    end_events = [];
    if numel(tmp_end_events) > 0
        boundary_indicator = diff(tmp_end_events) > 0.3;
        boundary = [0 find(boundary_indicator) numel(tmp_end_events)];
        for i = 2:numel(boundary)
            a = boundary(i-1) + 1;
            b = boundary(i);
            end_events(end+1) = mean(tmp_end_events(a:b));
        end
    end

    %end_events

    % mechanism 2: use event generation rate only
    num_steps = ceil(gravacc(end, 1) / R_WIN_STEP);
    
    tmp_horizontal_markers = false(num_steps, 1);
    for i_lo = 2:0.5:5
        generation_rates = zeros(num_steps, 1);
        for j_step = 1:num_steps
            mid_sec = R_WIN_STEP * j_step;
            start_sec = mid_sec - R_WIN_SIZE / 2;
            stop_sec = mid_sec + R_WIN_SIZE / 2;
            generation_rates(j_step) = ...
                sum(start_sec <= time_difference(:, 1) & time_difference(:, 1) <= stop_sec ...
                    & i_lo <= time_difference(:, 2) & time_difference(:, 2) < (i_lo+1)) / R_WIN_SIZE;
        end
        tmp_horizontal_markers = tmp_horizontal_markers | (generation_rates > 1.5);
    end
    horizontal_events = find(tmp_horizontal_markers) * R_WIN_STEP;
    
    % merge both event sources
    end_events = sort([horizontal_events' end_events]);
    
%     for i = 1:num_steps
%         start_sec = T_WIN_STEP * i;
%         stop_sec = start_sec + T_WIN_SIZE;
%         generation_rate = sum(start_sec <= time_difference(:, 1) & time_difference(:, 1) <= stop_sec) / T_WIN_SIZE;
%         if generation_rate >= 6
%         end
%     end

    % est_sets: start_sec, stop_sec, num_reps, method
    % (method 1->end event, 2->peak)
    est_sets = [NaN, NaN, NaN, NaN];

    set_boundary = [0 find(diff(end_events) > 7) numel(end_events)];
    end_event_sets = cell(numel(set_boundary) - 1, 1);
    for i = 1:numel(end_event_sets)
        a = set_boundary(i) + 1;
        b = set_boundary(i+1);
        num_events = b - a + 1;
        if num_events <= 1
            continue
        end
        
        esti_start_sec = end_events(a);
        esti_stop_sec = end_events(b);
        end_event_sets{i} = end_events(a:b);
        
        % maximum/minimum values monitoring
        mid_sec = mean([esti_start_sec, esti_stop_sec]);
        monitor_start_sec = max(esti_start_sec, mid_sec - 5);
        monitor_stop_sec = min(esti_stop_sec, mid_sec + 5);
        idx = monitor_start_sec < gravacc(:, 1) & gravacc(:, 1) < monitor_stop_sec;
        
        axes_max_vals = max(gravacc(idx, 2:4));
        axes_min_vals = min(gravacc(idx, 2:4));
        fall_distances = axes_max_vals - axes_min_vals;
        [fall_dis, sel_axis_idx] = max(fall_distances);
        if fall_dis < 2
            continue
        end
        
        MIN_DISTANCE = 20;
        prominence = min(4, fall_dis * 0.4);

        % peak detection monitoring
        monitor_start_sec = esti_start_sec - 5;
        monitor_stop_sec = esti_stop_sec + 5;
        
        idx = monitor_start_sec < gravacc(:, 1) & gravacc(:, 1) < monitor_stop_sec;
        monitor_offset = find(idx);
        monitor_offset = monitor_offset(1);
        data = medfilt1(gravacc(idx, 2:4), 5);
        sel_channel = data(:, sel_axis_idx);

        [sel_max_vals, sel_max_locs] = findpeaks_prominence(sel_channel, ...
            prominence, 1, 'MinPeakDistance', MIN_DISTANCE);

        [sel_min_vals, sel_min_locs] = findpeaks_prominence(-sel_channel, ...
            prominence, 1, 'MinPeakDistance', MIN_DISTANCE);
        sel_min_vals = -sel_min_vals;

        if numel(sel_min_locs) <= 3
            min_lin_dis = 0;
        else
            mid_peak_idx = sel_min_locs(round(numel(sel_min_locs) / 2));
            if mid_peak_idx <= 15 || mid_peak_idx > numel(sel_channel) - 15
                min_lin_dis = 0;
            else
                min_lin_dis = sum(abs(diff(sel_channel((mid_peak_idx-15):(mid_peak_idx+15)))));
            end
        end

        if numel(sel_max_locs) <= 3
            max_lin_dis = 0;
        else
            mid_peak_idx = sel_max_locs(round(numel(sel_max_locs) / 2));
            if mid_peak_idx <= 15 || mid_peak_idx > numel(sel_channel) - 15
                max_lin_dis = 0;
            else
                max_lin_dis = sum(abs(diff(sel_channel((mid_peak_idx-15):(mid_peak_idx+15)))));
            end
        end

        if min_lin_dis == 0 && max_lin_dis == 0
            continue
        end
        
        if min_lin_dis > max_lin_dis
            sel_vals = sel_min_vals;
            sel_locs = sel_min_locs;
            sel_dir = 'min';
        else
            sel_vals = sel_max_vals;
            sel_locs = sel_max_locs;
            sel_dir = 'max';
        end

        if numel(sel_vals) < 4
            % peak detection cannot process this data, handover to end
            % event approach
            m = median(diff(end_event_sets{i}));
            num_reps = ceil((sum(round(diff(end_event_sets{i}) / m)) + 1) / 2);
            if 4 <= num_reps && num_reps <= 40
                fprintf('  esti: approach=end, %d reps [%.1f, %.1f]\n', num_reps, ...
                    end_event_sets{i}(1), end_event_sets{i}(end));
                est_sets(end+1, :) = ...
                    [end_event_sets{i}(1), end_event_sets{i}(end), num_reps, 1];
            end
        else
            a = round(numel(sel_vals) * 0.34);
            b = round(numel(sel_vals) * 0.66);
            mean_diff = rms(diff(sel_locs(a:b)));

            m = round(numel(sel_vals) * 0.5);
            mid_idx = round(mean(sel_locs(m + (0:1))));
            sample_mid_val = data(mid_idx, :);

            PEAK_VAL_MANHATTEN_DIS = 6;
            MID_VAL_MANHATTEN_DIS = 10;
            PEAK_DIS = 30;  % samples

            now_diff = mean_diff;
            while a >= 1
                check = false;
                idxs = sel_locs(a + (0:1));
                if sum(abs(diff(data(idxs, :)))) < PEAK_VAL_MANHATTEN_DIS
                    dis = diff(idxs);
                    if abs(dis - now_diff) < PEAK_DIS
                        mid_idx = round(mean(idxs));
                        if sum(abs(diff([data(mid_idx, :); sample_mid_val]))) < MID_VAL_MANHATTEN_DIS
                            check = true;
                            a = a - 1;
                            now_diff = now_diff / 2 + dis / 2;
                        end
                    end
                end

                if ~check
                    break
                end
            end

            now_diff = mean_diff;
            while b <= numel(sel_vals)
                check = false;
                idxs = sel_locs(b + (-1:0));
                if sum(abs(diff(data(idxs, :)))) < PEAK_VAL_MANHATTEN_DIS
                    dis = diff(idxs);
                    if abs(dis - now_diff) < PEAK_DIS
                        mid_idx = round(mean(idxs));
                        if sum(abs(diff([data(mid_idx, :); sample_mid_val]))) < MID_VAL_MANHATTEN_DIS
                            check = true;
                            b = b + 1;
                            now_diff = now_diff / 2 + dis / 2;
                        end
                    end
                end

                if ~check
                    break
                end
            end

            a = a + 1;
            b = b - 1;
            num_reps = b - a + 1;
            
            % if the average rep period is too small, skip
            time_elasped = (sel_locs(b) - sel_locs(a)) / 25;
            avg_rep_period = time_elasped / (num_reps - 1);
            %if (sel_locs(b) - sel_locs(a)) / (num_reps - 1) / 25 < REP_MIN_PERIOD
            %    continue
            %end
            if time_elasped < 5
                continue
            %elseif time_elasped < 10 && avg_rep_period < 1.5
            %    continue
            end
            
            
            if avg_rep_period < 1.4
                fprintf('===========-+-+-> REDUCE %d->%d\n', num_reps, ceil(num_reps/2));
                num_reps = ceil(num_reps / 2);
            elseif avg_rep_period < 2.0
                session_start_sec = end_event_sets{i}(1);
                session_stop_sec = end_event_sets{i}(end);
                idxs = find(session_start_sec < time_difference(:, 1) & time_difference(:, 1) < session_stop_sec);
                sorted_time_frames = sort(time_difference(idxs, 2));
                
                % method 1, use percentile
                esti_rep_period_by_horizontal = sorted_time_frames(round(numel(idxs) * 0.7));
                
                % method 2, literally detect horizontal
                %cur_max_num_events = 0;
                %for j_lo = 2:0.5:5
                %    idxs = j_lo < sorted_time_frames & sorted_time_frames < (j_lo + 0.5);
                %    tmp_num_events = sum(idxs);
                %    if tmp_num_events > cur_max_num_events
                %        cur_max_num_events = tmp_num_events;
                %        esti_rep_period_by_horizontal = mean(sorted_time_frames(idxs));
                %        %j_lo
                %        %esti_rep_period_by_horizontal
                %    end
                %end
                %%esti_rep_period_by_horizontal
                
                if esti_rep_period_by_horizontal > avg_rep_period * 1.8
                    fprintf('================> REDUCE %d->%d\n', num_reps, ceil(num_reps/2));
                    num_reps = ceil(num_reps / 2);
                end
            end
            
            fprintf('  esti: approach=peak, %d reps [%.1f, %.1f]\n', num_reps, ...
                    end_event_sets{i}(1), end_event_sets{i}(end));
            fprintf('    axis=%d, dir=%s\n', sel_axis_idx, sel_dir);
            
            est_sets(end+1, :) = ...
                    [end_event_sets{i}(1), end_event_sets{i}(end), num_reps, 2];
        end
        
        if TO_PLOT
            pause
        end
    end

    % get rid of the first NaN row
    est_sets = est_sets(2:end, :);

    set_marked = false(numel(cur_task.sets), 1);
    cur_fp_set = 0;
    for i_pr = 1:size(est_sets, 1)
        gc_sel_idx = 0;
        gc_sel_length = 0;
        for i_gc = 1:numel(cur_task.sets)
            cur_set = cur_task.sets(i_gc);
            cover_start = max(est_sets(i_pr, 1), cur_set.start_sec);
            cover_stop = min(est_sets(i_pr, 2), cur_set.stop_sec);
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

            gnd_num_reps = gnd_set.num_reps;
            est_num_reps = est_sets(i_pr, 3);
            rep_diff = est_num_reps - gnd_num_reps;
            rep_errors(end+1) = rep_diff;
            num_rep_breakdown{gnd_type, contributor_idx}(end+1) = gnd_num_reps;
            rep_error_breakdown{gnd_type, contributor_idx}(end+1) = rep_diff;
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

    tp_set = tp_set + cur_tp_set;
    fp_set = fp_set + cur_fp_set;
    fn_set = fn_set + cur_fn_set;

    fprintf('Set: tp=%d, fp=%d, fn=%d\n', ...
        cur_tp_set, cur_fp_set, cur_fn_set);

end


%% statistics
total_set = tp_set + fn_set;
prec_set = tp_set / (tp_set + fp_set);
recl_set = tp_set / (tp_set + fn_set);
total_avg_abs_rep_error = mean(abs(rep_errors));

fprintf('\n');
fprintf('====== Overall report ======\n');
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
    fprintf('         rep counting abs error=%.3f, aggregation abs error=%.3f\n', ...
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

dlmwrite([PLAIN_RESULT_OUT_DIR 'revisit_set_pr_by_user.csv'], ...
    [contributor_set_prec, contributor_set_recl; prec_set, recl_set], ...
    'delimiter', ',', 'precision', 6);
dlmwrite([PLAIN_RESULT_OUT_DIR 'revisit_rep_err_by_user.csv'], ...
    [contributor_rep_abs_error; total_avg_abs_rep_error], ...
    'delimiter', ',', 'precision', 6);
dlmwrite([PLAIN_RESULT_OUT_DIR 'revisit_set_det_rate_by_type.csv'], ...
    type_set_det_rate, ...
    'delimiter', ',', 'precision', 6);
dlmwrite([PLAIN_RESULT_OUT_DIR 'revisit_rep_err_by_type.csv'], ...
    [type_abs_rep_error, type_total_rep_error], ...
    'delimiter', ',', 'precision', 6);

return
%% get event time density
num_steps = ceil(gravacc(end, 1) / T_WIN_STEP);
generation_rates = zeros(num_steps, 1);

for i = 1:num_steps
    start_sec = T_WIN_STEP * i;
    stop_sec = start_sec + T_WIN_SIZE;
    generation_rates(i) = ...
        sum(start_sec <= time_difference(:, 1) & time_difference(:, 1) <= stop_sec) / T_WIN_SIZE;
end

figure
h1 = subplot(2, 1, 1);
plot(time_difference(:, 1), time_difference(:, 2), 'k*')
ylim([0, MAX_END_EVENT_TIME])
h2 = subplot(2, 1, 2);
plot((1:num_steps) * T_WIN_STEP, generation_rates);
linkaxes([h1, h2], 'x')

%% get event time density by range
num_steps = ceil(gravacc(end, 1) / R_WIN_STEP);
generation_rates = zeros(num_steps, 1);

lo_value = 2;
hi_value = 3;

for i = 1:num_steps
    start_sec = R_WIN_STEP * i;
    stop_sec = start_sec + R_WIN_SIZE;
    generation_rates(i) = ...
        sum(start_sec <= time_difference(:, 1) & time_difference(:, 1) <= stop_sec ...
            & lo_value <= time_difference(:, 2) & time_difference(:, 2) < hi_value) / R_WIN_SIZE;
end

figure
h1 = subplot(2, 1, 1);
plot(time_difference(:, 1), time_difference(:, 2), 'k*')
ylim([0, MAX_END_EVENT_TIME])
h2 = subplot(2, 1, 2);
plot((1:num_steps) * R_WIN_STEP, generation_rates);
linkaxes([h1, h2], 'x')


%% extract task index
tmp_manager = get_task_manager();

type_to_keep = TYPE.get_standard_types();
num_types = numel(type_to_keep);
tmp_manager.keep_types(type_to_keep);
tmp_manager.remove_fragile_tasks();
tmp_manager.indexing();
[task_idx, set_idx] = tmp_manager.query_by_contributor_type('P', 3, 3)