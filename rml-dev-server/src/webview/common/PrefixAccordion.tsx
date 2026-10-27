import React, { useState, useMemo } from 'react';
import { Tag } from 'baukasten-ui/core';

export interface PrefixAccordionProps {
    prefixes?: Record<string, string>;
}

export const PrefixAccordion: React.FC<PrefixAccordionProps> = ({ prefixes }) => {
    const [isOpen, setIsOpen] = useState(false);
    const [filter, setFilter] = useState('');

    const entries = useMemo(() => {
        if (!prefixes) return [];
        return Object.entries(prefixes)
            .map(([p, ns]) => ({
                prefix: p ? (p.endsWith(':') ? p : `${p}:`) : ':',
                namespace: ns
            }))
            .sort((a, b) => a.prefix.localeCompare(b.prefix));
    }, [prefixes]);

    const filtered = useMemo(() => {
        if (!filter.trim()) return entries;
        const q = filter.trim().toLowerCase();
        return entries.filter(
            e => e.prefix.toLowerCase().includes(q) || e.namespace.toLowerCase().includes(q)
        );
    }, [entries, filter]);

    if (entries.length === 0) {
        return null;
    }

    return (
        <div className="bk-prefix-accordion">
            <button
                type="button"
                className="bk-prefix-accordion-header"
                onClick={() => setIsOpen(!isOpen)}
                aria-expanded={isOpen}
            >
                <span className="bk-prefix-accordion-arrow">{isOpen ? '▼' : '▶'}</span>
                <span className="bk-prefix-accordion-title">Prefixes</span>
                <Tag size="xs" color="default">{entries.length}</Tag>
            </button>

            {isOpen && (
                <div className="bk-prefix-accordion-content">
                    {entries.length > 5 && (
                        <input
                            type="text"
                            className="bk-prefix-search-input"
                            placeholder="Filter prefixes or namespaces..."
                            value={filter}
                            onChange={(e) => setFilter(e.target.value)}
                        />
                    )}
                    <div className="bk-prefix-table-wrapper">
                        <table className="bk-prefix-table">
                            <thead>
                                <tr>
                                    <th>Prefix</th>
                                    <th>Namespace</th>
                                </tr>
                            </thead>
                            <tbody>
                                {filtered.map((item) => (
                                    <tr key={item.prefix}>
                                        <td className="bk-prefix-name-col">
                                            <code>{item.prefix}</code>
                                        </td>
                                        <td className="bk-prefix-ns-col" title={item.namespace}>
                                            <span className="bk-prefix-ns-text">&lt;{item.namespace}&gt;</span>
                                        </td>
                                    </tr>
                                ))}
                                {filtered.length === 0 && (
                                    <tr>
                                        <td colSpan={2} className="bk-prefix-empty">No prefixes match filter</td>
                                    </tr>
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
            )}
        </div>
    );
};
