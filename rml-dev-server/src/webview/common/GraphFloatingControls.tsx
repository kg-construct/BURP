import React from 'react';

export interface GraphFloatingControlsProps {
    onZoomIn: () => void;
    onZoomOut: () => void;
    onFitView: () => void;
}

export const GraphFloatingControls: React.FC<GraphFloatingControlsProps> = ({
    onZoomIn,
    onZoomOut,
    onFitView
}) => {
    return (
        <div className="bk-graph-floating-controls">
            <button type="button" className="bk-floating-btn" onClick={onZoomIn} title="Zoom In">
                +
            </button>
            <button type="button" className="bk-floating-btn" onClick={onZoomOut} title="Zoom Out">
                −
            </button>
            <button type="button" className="bk-floating-btn" onClick={onFitView} title="Fit to Screen">
                ⊡
            </button>
        </div>
    );
};
