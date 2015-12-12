% baseline_rep_detection3.m fuses 3 gravity channels by adding a constant
% to each channel to guarantee all values are positive, and then add each
% channel as a new series. The result is not good.

%% House keeping
clear; close all;
addpath(genpath('./'))

%%
manager = get_task_manager();


type_to_keep = [
    TYPE.bicep_curl
    TYPE.tricep_curl
    TYPE.lat_pulldown
    TYPE.chest_press
    TYPE.overhead_press
    TYPE.seated_row
    TYPE.butter_fly
    TYPE.rear_deltoid
    TYPE.dip
    TYPE.ab_crunch
];

manager.keep_types(type_to_keep);

% TODO: include more non-workout time in-between
% Threashold for determing repetition from auto-correlation result
REP_THRES = 1000;
DONT_CARE_SEC = 3;
% Window for auto correlation
AC_WLEN = 250;

% Threshold for each type of weightlifting
rep_type_thres = [9000, 9000, 6000, 10000, 4000, 10000, 2000, 7000, 9000, 7000];

% Stats of baseline detection algorithm
total_len = 0;
tp = 0;
tn = 0;
fp = 0;
fn = 0;


%for task_idx = 1 : numel(manager.tasks)
for task_idx = 2 : 2
    cur_task = manager.tasks{task_idx};
    fprintf('task_idx=%d, contributor=%s, set=%d \n', ...
        task_idx, cur_task.contributor, numel(cur_task.sets));
    [~, ~, gravacc, ~, ~, ~] = cur_task.get_sensor_data();
    
    % now ac_gravacc_agg has different length compared with gravacc_agg,
    % the total length in second has to be changed accordingly
    gravacc_num_row = size(gravacc, 1);
    cur_sec = floor(gravacc(gravacc_num_row - AC_WLEN, 1));
    
    mask = true(cur_sec, 1);
    rep_gc = false(cur_sec, 1);
    rep_pr = false(cur_sec, 1);
    
    %h1 = subplot(2, 1, 1);
    %hold on
    %plot(gravacc(:, 1), gravacc(:, 2), 'r');
    %plot(gravacc(:, 1), gravacc(:, 3), 'g');
    %plot(gravacc(:, 1), gravacc(:, 4), 'b');
    
    %h2 = subplot(2, 1, 2);
    %linkaxes([h1, h2], 'x');
    
    for i = 1 : numel(cur_task.sets)
        % Get ground truth
        cur_set = cur_task.sets(i);
        st = max(1, cur_set.start_sec);
        et = min(cur_sec, cur_set.stop_sec);
        rep_gc(st:et) = true;
        
        % set masks
        a = max(1, st - DONT_CARE_SEC);
        b = min(cur_sec, st + DONT_CARE_SEC);
        mask(a:b) = false;
        
        a = max(1, et - DONT_CARE_SEC);
        b = min(cur_sec, et + DONT_CARE_SEC);
        mask(a:b) = false;
        
        fprintf('   set %d: time=[%.1f,%.1f]\n', i, st, et);
    end

        
    for t = 1 : cur_sec
        % Get predicted result
        % Check this second's result from auto correlation
        %idx = gravacc(:, 1) <= t & gravacc(:, 1) < t + 1;
        start_idx = find(gravacc(:, 1) > (t - 1));
        start_idx = start_idx(1);
        end_idx = min(size(gravacc, 1), start_idx + 1000);
        
        %hold off
        %plot(0, 0)
        %hold on
        
        session_tails = [];
        
        % gravacc channel fusing strategy: sum of abs
        gravacc_agg = sum(gravacc(start_idx:end_idx, 2:4) + 15, 2);
        
        ac_gravacc_agg = autocorr2(gravacc_agg, AC_WLEN);
        [vals, locs] = findpeaks(ac_gravacc_agg, 'MinPeakDistance', 24, ...
            'MinPeakHeight', 0.8);

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
        large_prominence_idx = (vals - min(rminima_sel, lminima_sel) > 0.07);  % prominence

        %t
        %[vals locs]
        
        
        vals = vals(large_prominence_idx);
        locs = locs(large_prominence_idx);

        %t
        %[vals locs]

        ac_end_idx = start_idx + numel(ac_gravacc_agg) - 1;
        plot(gravacc(start_idx:ac_end_idx, 1), ac_gravacc_agg);
        xlabel(['sec=' num2str(t)]);
        pause
        

        first_few_peaks = find(locs < 600);
        if numel(first_few_peaks) >= 600 / 25 / 5
            %[t t + min(session_tails) / 25 0]
            a = t;
            b = round(min(cur_sec, t + locs(end) / 25));
            %[a b]
            rep_pr(a:b) = true;
        end
    end
    
    % remove don't care part
    rep_gc = rep_gc(mask);
    rep_pr = rep_pr(mask);
    
    % accuracy statistics
    cur_tp = sum( rep_gc &  rep_pr);
    cur_tn = sum(~rep_gc & ~rep_pr);
    cur_fp = sum(~rep_gc &  rep_pr);
    cur_fn = sum( rep_gc & ~rep_pr);
    
    % overall statistics
    tp = tp + cur_tp;
    tn = tn + cur_tn;
    fp = fp + cur_fp;
    fn = fn + cur_fn;
    total_len = total_len + sum(rep_gc);
    
    fprintf('tp=%d, tn=%d, fp=%d, fn=%d\n', ...
        cur_tp, cur_tn, cur_fp, cur_fn);
end


precision = tp / (tp + fp);
recall = tp / (tp + fn);

%fprintf('total_sec=%d, total_rep=%d, prec=%f, recl=%f\n', jj, total_len, total_rep, precision, recall);
fprintf('total_sec=%d, prec=%f, recl=%f\n', total_len, precision, recall);
fprintf('total tp=%d, tn=%d, fp=%d, fn=%d\n', tp, tn, fp, fn);

return

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