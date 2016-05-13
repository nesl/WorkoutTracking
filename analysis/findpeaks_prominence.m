function [vals, locs] = findpeaks_prominence(data, prominence, mode, varargin)
    % Args:
    %   data: either a row or a column vector
    %   prominence: falldown threshold
    %   mode: can be interpreted at least how many sides have to be
    %     satisfied the prominence threshold. 2 means both sides. 1 means
    %     at least one side.

    %data
    %prominence
    %mode
    %varargin
    
    [vals, locs] = findpeaks(data, varargin{:});
    
    EPS = 0.2;
    lminima = zeros(size(data));
    lminima(1) = data(1);
    now_y = data(1);
    for i = 2:numel(data)
        if data(i) >= now_y - EPS
            lminima(i) = lminima(i-1);
            now_y = max(now_y, data(i));
        else
            lminima(i) = data(i);
            now_y = data(i);
        end
    end

    rminima = zeros(size(data));
    rminima(end) = data(end);
    now_y = data(end);
    for i = fliplr(1:(numel(data) - 1))
        if data(i) >= data(i+1) - EPS
            rminima(i) = rminima(i+1);
            now_y = max(now_y, data(i));
        else
            rminima(i) = data(i);
            now_y = data(i);
        end
    end

    %[(1:numel(data))' lminima data rminima]
    
    rminima_sel = rminima(locs);
    lminima_sel = lminima(locs);
    if mode == 1
        large_prominence_idx = (vals - min(rminima_sel, lminima_sel) > prominence);
    elseif mode == 2
        large_prominence_idx = (vals - max(rminima_sel, lminima_sel) > prominence);
    else
        error(['Unsupported mode number (' num2str(mode) ')'])
    end

    vals = vals(large_prominence_idx);
    locs = locs(large_prominence_idx);
end

