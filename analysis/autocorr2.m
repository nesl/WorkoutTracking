function ac = autocorr2(y, wlen)
    % y - original signal
    % wlen - window size
    % ac - auto-correlation
    
    % check the shape of y, transport it to a row vector if necessary
    [h, w] = size(y);
    if w > 1 && h > 1
        error('input y is not a vector');
    elseif h > 1 && w == 1
        y = y';
    end
    
    if numel(y) < wlen
        warning('size of y is smaller len wlen');
        ac = [];
    else
        % Sweeping autocorrelation
        n = length(y) - wlen + 1;
        ac = zeros(n, 1);
        hw = y(1:wlen);  % head window
        hw_norm = norm(hw);
        for i=1:n
            % take window
            w = y(i:(i+wlen-1));
            w_norm = norm(w);

            % auto-corr
            %ac(i) = sum(hw .* w);
            %c = xcorr(hw, w, 'coeff');
            %ac(i) = c(wlen);
            ac(i) = sum(hw .* w) / hw_norm / w_norm;
        end
    end
end
