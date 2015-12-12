function [acc, linearacc, gravacc, gyro, mag, heartrate] = read_input(path_prefix, format, varargin)

% read_input() reads sensor input. Right now we have two formats, and this
% function helps and eases the effort of reading files.

acc = NaN;
gravacc = NaN;
linearacc = NaN;
gyro = NaN;
mag = NaN;
heartrate = NaN;

if strcmp(format, 'bo') == 1
    raw_acc  = csvread([path_prefix '_acc.csv']);
    raw_gyro = csvread([path_prefix '_gyro.csv']);
    raw_grav = csvread([path_prefix '_grav.csv']);
    raw_mag  = csvread([path_prefix '_mag.csv']);
    
    % acc/grav, gyro, and mag share the same sensor time system

    % processed sensor data, format: shifted_time(s), x, y, z
    acc  = raw_acc(:, 2:5);
    gyro = raw_gyro(:, 2:5);
    gravacc = raw_grav(:, 2:5);
    mag  = raw_mag(:, 2:5);

    offset = min([ ...
        min(raw_acc(:, 2)), ...
        min(raw_gyro(:, 2)), ...
        min(raw_grav(:, 2)), ...
        min(raw_mag(:, 2)) ...
    ]);

    acc(:, 1)     = (acc(:, 1)     - offset) * 1e-9;
    gyro(:, 1)    = (gyro(:, 1)    - offset) * 1e-9;
    gravacc(:, 1) = (gravacc(:, 1) - offset) * 1e-9;
    mag(:, 1)     = (mag(:, 1)     - offset) * 1e-9;

    % filter outliers
    acc(:, 2:4) = min(30, acc(:, 2:4));
    acc(:, 2:4) = max(-30, acc(:, 2:4));
    gyro(:, 2:4) = min(30, gyro(:, 2:4));
    gyro(:, 2:4) = max(-30, gyro(:, 2:4));
    gravacc(:, 2:4) = min(30, gravacc(:, 2:4));
    gravacc(:, 2:4) = max(-30, gravacc(:, 2:4));
elseif strcmp(format, 'intern') == 1
    acc       = csvread([path_prefix '.wear.acc']);
    gyro      = csvread([path_prefix '.wear.gyro']);
    linearacc = csvread([path_prefix '.wear.linearacc']);
    mag       = csvread([path_prefix '.wear.mag']);
    heartrate = csvread([path_prefix '.wear.heartrate']);
    
    offset = min([ ...
        min(acc(:, 1)), ...
        min(gyro(:, 1)), ...
        min(linearacc(:, 1)), ...
        min(mag(:, 1)) ...
        min(heartrate(:, 1)) ...
    ]);
    
    acc(:, 1)       = (acc(:, 1)       - offset) * 1e-9;
    gyro(:, 1)      = (gyro(:, 1)      - offset) * 1e-9;
    linearacc(:, 1) = (linearacc(:, 1) - offset) * 1e-9;
    mag(:, 1)       = (mag(:, 1)       - offset) * 1e-9;
    heartrate(:, 1) = (heartrate(:, 1) - offset) * 1e-9;
    
    % filter outliers
    acc(:, 2:4) = min(30, acc(:, 2:4));
    acc(:, 2:4) = max(-30, acc(:, 2:4));
    gyro(:, 2:4) = min(30, gyro(:, 2:4));
    gyro(:, 2:4) = max(-30, gyro(:, 2:4));
    linearacc(:, 2:4) = min(30, linearacc(:, 2:4));
    linearacc(:, 2:4) = max(-30, linearacc(:, 2:4));
    
    for i = 1:numel(varargin)
        if strcmp(varargin{i}, 'compute_gravacc') == 1
            % acc array to linear time frame
            acc_2_linear = linearacc;
            acc_2_linear(:, 2) ...
                = interp1(acc(:, 1), acc(:, 2), linearacc(:, 1), 'spline');
            acc_2_linear(:, 3) ...
                = interp1(acc(:, 1), acc(:, 3), linearacc(:, 1), 'spline');
            acc_2_linear(:, 4) ...
                = interp1(acc(:, 1), acc(:, 4), linearacc(:, 1), 'spline');
            
            gravacc = acc_2_linear;
            gravacc(:, 2:4) = acc_2_linear(:, 2:4) - linearacc(:, 2:4);
            gravacc(:, 2:4) = min(30, gravacc(:, 2:4));
            gravacc(:, 2:4) = max(-30, gravacc(:, 2:4));
        end
    end
else
    error('format not supported...');
end

end

