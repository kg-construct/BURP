import React from 'react';
import {Button, Tag} from 'baukasten-ui/core';

export interface HeaderBarProps {
    cursor?: { line: number; character: number };
    onRefresh: () => void;
    extraRight?: React.ReactNode;
}

export const HeaderBar: React.FC<HeaderBarProps> = ({ cursor, onRefresh, extraRight }) => {
    return (
        <div className="bk-header-bar">
            <div className="bk-header-left">
                {cursor && (
                    <Tag size="sm" color="secondary">
                        Line {cursor.line}, Col {cursor.character}
                    </Tag>
                )}
            </div>
            <div className="bk-header-right">
                {extraRight}
                <Button size="sm" variant="secondary" onClick={onRefresh} title="Refresh">
                    ↻ Refresh
                </Button>
            </div>
        </div>
    );
};
