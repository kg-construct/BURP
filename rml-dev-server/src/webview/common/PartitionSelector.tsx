import React from 'react';
import { Select, SelectOption } from 'baukasten-ui/core';

export interface PartitionOption {
    id: string;
    label: string;
    count?: number;
    defaultLabel?: string;
}

export interface PartitionSelectorProps {
    label: string;
    options: PartitionOption[];
    value: string;
    onChange: (value: string) => void;
    compact?: boolean;
}

export const PartitionSelector: React.FC<PartitionSelectorProps> = ({
    label,
    options,
    value,
    onChange,
    compact = false
}) => {
    if (!options || options.length <= 1) {
        return null;
    }

    if (!compact && options.length <= 4) {
        return (
            <div className="bk-partition-bar">
                <span className="bk-partition-label">{label}:</span>
                <div className="bk-partition-tabs">
                    {options.map((opt) => {
                        const isActive = value === opt.id;
                        return (
                            <button
                                key={opt.id}
                                type="button"
                                className={`bk-partition-tab-btn ${isActive ? 'active' : ''}`}
                                onClick={() => onChange(opt.id)}
                            >
                                <span className="bk-tab-text">{opt.label}</span>
                                {opt.count !== undefined && (
                                    <span className="bk-tab-count">({opt.count})</span>
                                )}
                            </button>
                        );
                    })}
                </div>
            </div>
        );
    }

    const selectOptions: SelectOption[] = options.map((opt) => ({
        value: opt.id,
        label: opt.label,
        defaultLabel: opt.count !== undefined ? `${opt.count} quads` : opt.defaultLabel
    }));

    return (
        <div className={`bk-partition-bar ${compact ? 'bk-partition-bar-compact' : ''}`}>
            <span className="bk-partition-label">{label}:</span>
            <div className="bk-partition-select-wrapper">
                <Select
                    size="sm"
                    searchable={options.length > 4}
                    searchPlaceholder={`Search ${label.toLowerCase()}...`}
                    options={selectOptions}
                    value={value}
                    onChange={(val) => onChange(val as string)}
                    placeholder={`Select ${label.toLowerCase()}...`}
                />
            </div>
        </div>
    );
};
