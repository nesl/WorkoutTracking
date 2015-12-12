classdef Set < handle
    % Task serves as a data structure to present the attributes of a task.
    
    properties (SetAccess = public, GetAccess = public)
        start_sec;
        stop_sec;
        type;
        num_reps;
    end
    
    methods
        % CONSTRUCTOR
        function obj = Set(start_sec, stop_sec, type, num_reps)
            obj.start_sec = start_sec;
            obj.stop_sec = stop_sec;
            obj.type = type;
            obj.num_reps = num_reps;
        end
    end
end