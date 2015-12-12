classdef TYPE < handle
    % Task serves as a data structure to present the attributes of a task.
    
    properties (Constant)
        none = 0;
        bicep_curl = 1;
        tricep_curl = 2;
        lat_pulldown = 3;
        chest_press = 4;
        overhead_press = 5;
        seated_row = 6;
        butter_fly = 7;
        rear_deltoid = 8;
        dip = 9;
        ab_crunch = 10;
        lift = 101;
        dead_lift = 102;
        bench_lift = 103;
        shoulder_press = 104;
        squat = 105;
        dumbbell_bicep_curl = 201;
        dumbbell_shoulder_press = 202;
        barbell_bicep_curl = 301;
    end

    methods (Static)
        function type_str = get_name(type_no)
            if type_no == TYPE.none
                type_str = 'none';
            elseif type_no == TYPE.bicep_curl
                type_str = 'bicep curl';
            elseif type_no == TYPE.tricep_curl
                type_str = 'tricep curl';
            elseif type_no == TYPE.lat_pulldown
                type_str = 'lat pulldown';
            elseif type_no == TYPE.chest_press
                type_str = 'chess press';
            elseif type_no == TYPE.overhead_press
                type_str = 'overhead press';
            elseif type_no == TYPE.seated_row
                type_str = 'seated row';
            elseif type_no == TYPE.butter_fly
                type_str = 'butter fly machine';
            elseif type_no == TYPE.rear_deltoid
                type_str = 'rear deltoid';
            elseif type_no == TYPE.dip
                type_str = 'dip machine';
            elseif type_no == TYPE.ab_crunch
                type_str = 'AB crunch';
            elseif type_no == TYPE.lift
                type_str = 'mestery lfit';
            elseif type_no == TYPE.dead_lift
                type_str = 'dead lift';
            elseif type_no == TYPE.bench_lift
                type_str = 'bench lift';
            elseif type_no == TYPE.shoulder_press
                type_str = 'shoulder press';
            elseif type_no == TYPE.squat
                type_str = 'squat';
            elseif type_no == TYPE.dumbbell_bicep_curl
                type_str = 'dumbbell bicep curl';
            elseif type_no == TYPE.dumbbell_shoulder_press
                type_str = 'dumbbell shoulder press';
            elseif type_no == TYPE.barbell_bicep_curl
                type_str = 'barbell_bicep_curl';
            else
                type_str = '(unrecognized type)';
            end
        end
        
        function arr = get_standard_types()
            arr = 1:10;
        end
    end
end