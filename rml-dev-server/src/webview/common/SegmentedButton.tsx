import React from 'react';

/**
 * SegmentedButton renders a group of button-like segments that behave like a
 * radio control – only one segment can be active at a time. It uses the
 * Baukasten styling class `bk-segmented-btn` to preserve the existing visual
 * appearance while delegating the click handling to the supplied `onSelect`
 * callback.
 *
 * The component is deliberately lightweight: it does not depend on any
 * external UI library other than Baukasten's CSS, which is already included in
 * the extension. This makes it easy to replace the ad‑hoc `<button>` markup
 * that was previously scattered throughout the codebase.
 */
export interface SegmentedButtonOption {
  /** Unique identifier for the segment */
  id: string;
  /** Text shown inside the segment */
  label: string;
  /** Whether this segment is currently active */
  active: boolean;
}

export interface SegmentedButtonProps {
  /** List of segment options */
  options: SegmentedButtonOption[];
  /** Callback invoked when a segment is selected */
  onSelect: (id: string) => void;
  /** Optional additional class name for the container */
  className?: string;
}

/**
 * Render a segmented control using Baukasten button styles.
 * Example usage:
 * ```tsx
 * <SegmentedButton
 *   options={[
 *     { id: 'table', label: 'Table', active: viewMode === 'table' },
 *     { id: 'graph', label: 'Graph', active: viewMode === 'graph' },
 *   ]}
 *   onSelect={(id) => setViewMode(id as 'table' | 'graph')}
 * />
 * ```
 */
export const SegmentedButton: React.FC<SegmentedButtonProps> = ({
  options,
  onSelect,
  className = ''
}) => {
  return (
    <div className={`bk-segmented-switch ${className}`.trim()}>
      {options.map((opt) => (
        <button
          key={opt.id}
          type="button"
          className={`bk-segmented-btn ${opt.active ? 'active' : ''}`}
          onClick={() => onSelect(opt.id)}
        >
          {opt.label}
        </button>
      ))}
    </div>
  );
};
