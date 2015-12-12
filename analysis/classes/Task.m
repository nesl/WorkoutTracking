classdef Task < handle
    % Task serves as a data structure to present the attributes of a task.
    
    properties (SetAccess = public, GetAccess = public)
        pathname;
        
        % The format type. For more detail, please refer to definition of
        % format argument of read_input() function;
        file_format;
        
        contributor;
        
        % Variable sets is an array of workout session, the expected type
        % is an array of class Set.
        sets = [];
    end
    
    methods
        % CONSTRUCTOR
        function obj = Task()
        end
        
        function obj = add_a_set(obj, start_time, stop_time, workout_type, num_reps)
            obj.sets = [obj.sets Set(start_time, stop_time, workout_type, num_reps)];
        end
        
        function [acc, linearacc, gravacc, gyro, mag, heartrate] = get_sensor_data(obj)
            if strcmp(obj.file_format, 'bo') == 1
                [acc, linearacc, gravacc, gyro, mag, heartrate] = read_input( ...
                    obj.pathname, 'bo'); 
            elseif strcmp(obj.file_format, 'intern') == 1
                [acc, linearacc, gravacc, gyro, mag, heartrate] = read_input( ...
                    obj.pathname, 'intern', 'compute_gravacc');
            else
                error('unsuported file format');
            end
        end
        
        function sec = get_sensor_data_length(obj)
            [acc, linearacc, gravacc, gyro, mag, heartrate] = obj.get_sensor_data();
            sec = inf;
            if numel(acc) > 0
                sec = min(sec, acc(end, 1));
            end
            if numel(linearacc) > 0
                sec = min(sec, linearacc(end, 1));
            end
            if numel(gravacc) > 0
                sec = min(sec, gravacc(end, 1));
            end
            if numel(gyro) > 0
                sec = min(sec, gyro(end, 1));
            end
            if numel(mag) > 0
                sec = min(sec, mag(end, 1));
            end
            if numel(heartrate) > 0
                sec = min(sec, heartrate(end, 1));
            end
        end
        
        function res = required_attributes_all_set(obj)
            res = ~isempty(obj.pathname) & ~isempty(obj.file_format) ...
                & ~isempty(obj.contributor);
        end
    end
end