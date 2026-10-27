import React from 'react';
import {Button} from 'baukasten-ui/core';

export interface HeaderBarProps {
    cursor?: { line: number; character: number };
    onRefresh: () => void;
    extraRight?: React.ReactNode;
}

export const HeaderBar: React.FC<HeaderBarProps> = ({onRefresh, extraRight}) => {
    return (
        <div className="bk-header-bar" style={{justifyContent: 'flex-end'}}>
            <div className="bk-header-right">
                {extraRight}
                <Button size="sm" variant="secondary" onClick={onRefresh} title="Refresh">
                    ↻ Refresh
                </Button>
            </div>
        </div>
    );
};
