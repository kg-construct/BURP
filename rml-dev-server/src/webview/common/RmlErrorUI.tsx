import React, { useState } from 'react';
import { Alert, Badge, Button, Dropdown, Icon, Text } from 'baukasten-ui/core';
import {
    OriginLineage,
    PointRange,
    RmlErrorDto,
    RmlEvaluationResult,
    SampleOccurrence
} from '../../generated/types';
import { ParsedQuadItem } from '../execution/nquadsParser';

export interface IssueSampleFilter {
    label: string;
    originLineage?: OriginLineage;
    error: RmlErrorDto;
}

export function errorSeverityToVariant(severity?: string): 'error' | 'warning' | 'info' {
    switch (severity) {
        case 'WARNING':
            return 'warning';
        case 'INFORMATION':
            return 'info';
        default:
            return 'error';
    }
}

export function formatErrorRange(ranges?: PointRange[]): string | null {
    if (!ranges || ranges.length === 0) return null;
    const first = ranges[0];
    if (!first?.start) return null;
    return `L${first.start.line + 1}:${first.start.column + 1}`;
}

const XSD_STRING = 'http://www.w3.org/2001/XMLSchema#string';

function termValueKey(term: any): string | null {
    if (!term || typeof term !== 'object') return null;
    if (typeof term.uri === 'string') return `iri:${term.uri}`;
    if (typeof term.value === 'string') {
        const lang = term.language ? `@${term.language}` : '';
        const dt = !lang && term.datatype?.uri && term.datatype.uri !== XSD_STRING
            ? `^^${term.datatype.uri}`
            : '';
        return `lit:${term.value}${dt}${lang}`;
    }
    if (typeof term.id === 'string') return `bnode:${term.id}`;
    if (term.id && typeof term.id === 'object') return termValueKey(term.id);
    return null;
}

function quadTermKey(term: ParsedQuadItem['subject'] | ParsedQuadItem['predicate'] | ParsedQuadItem['object']): string {
    if (term.termType === 'NamedNode') return `iri:${term.value}`;
    if (term.termType === 'BlankNode') return `bnode:${term.value}`;
    if (term.termType === 'Literal') {
        const lang = term.language ? `@${term.language}` : '';
        const dt = !lang && term.datatype?.value && term.datatype.value !== XSD_STRING
            ? `^^${term.datatype.value}`
            : '';
        return `lit:${term.value}${dt}${lang}`;
    }
    return `other:${term.value}`;
}

function matchesResultPair(sampleResult: any, targetResult: any): boolean {
    if (!sampleResult || !targetResult) return false;
    if (sampleResult.type && targetResult.type && sampleResult.type !== targetResult.type) {
        return false;
    }
    if (sampleResult.type === 'statement') {
        return termValueKey(sampleResult.subject) === termValueKey(targetResult.subject)
            && termValueKey(sampleResult.predicate) === termValueKey(targetResult.predicate)
            && termValueKey(sampleResult.object) === termValueKey(targetResult.object);
    }
    if (sampleResult.type === 'predicateObject') {
        return termValueKey(sampleResult.predicate) === termValueKey(targetResult.predicate)
            && termValueKey(sampleResult.object) === termValueKey(targetResult.object);
    }
    const k1 = termValueKey(sampleResult);
    const k2 = termValueKey(targetResult);
    return k1 !== null && k1 === k2;
}

export function matchesEvaluationResult(error: RmlErrorDto, result: RmlEvaluationResult): boolean {
    if (!error.samples || error.samples.length === 0) return false;
    return error.samples.some((sample) => matchesResultPair(sample.evaluationResult, result));
}

function matchesSampleQuad(sample: SampleOccurrence, quad: ParsedQuadItem): boolean {
    const sampleLineage = sample.originLineage;
    const quadLineage = quad.lineage;
    if (sampleLineage?.primary && quadLineage?.primary) {
        if (
            sampleLineage.primary.logicalSourceId !== quadLineage.primary.logicalSourceId
            || sampleLineage.primary.index !== quadLineage.primary.index
        ) {
            return false;
        }
    } else if (sampleLineage?.primary && !quadLineage?.primary) {
        return false;
    }

    const res: any = sample.evaluationResult;
    if (!res) {
        return Boolean(sampleLineage?.primary && quadLineage?.primary);
    }
    if (res.type === 'statement') {
        return termValueKey(res.subject) === quadTermKey(quad.subject)
            && termValueKey(res.predicate) === quadTermKey(quad.predicate)
            && termValueKey(res.object) === quadTermKey(quad.object);
    }
    if (res.type === 'predicateObject') {
        return termValueKey(res.predicate) === quadTermKey(quad.predicate)
            && termValueKey(res.object) === quadTermKey(quad.object);
    }
    const termKey = termValueKey(res);
    if (termKey !== null) {
        return termKey === quadTermKey(quad.object) || termKey === quadTermKey(quad.subject);
    }
    return false;
}

export function matchesQuadError(error: RmlErrorDto, quad: ParsedQuadItem): boolean {
    if (!error.samples || error.samples.length === 0) return false;
    return error.samples.some((sample) => matchesSampleQuad(sample, quad));
}

export function matchesIterationGroupError(
    error: RmlErrorDto,
    logicalSourceId: string | null,
    iterationIndex: number
): boolean {
    if (!error.samples || error.samples.length === 0) return false;
    return error.samples.some((sample) => {
        const primary = sample.originLineage?.primary;
        if (!primary) return false;
        if (primary.index !== iterationIndex) return false;
        return !logicalSourceId || primary.logicalSourceId === logicalSourceId;
    });
}

export function quadMatchesIssueFilter(quad: ParsedQuadItem, filter: IssueSampleFilter): boolean {
    if (filter.originLineage?.primary) {
        if (!quad.lineage?.primary) return false;
        if (
            quad.lineage.primary.logicalSourceId !== filter.originLineage.primary.logicalSourceId
            || quad.lineage.primary.index !== filter.originLineage.primary.index
        ) {
            return false;
        }
        return matchesQuadError(filter.error, quad) || filter.error.samples.every((s) => !s.evaluationResult);
    }
    return matchesQuadError(filter.error, quad);
}

interface RmlErrorBadgeProps {
    errors: RmlErrorDto[];
    onNavigateRange?: (range: PointRange) => void;
    compact?: boolean;
}

export const RmlErrorBadge: React.FC<RmlErrorBadgeProps> = ({
    errors,
    onNavigateRange,
    compact = false
}) => {
    if (!errors || errors.length === 0) return null;

    const hasError = errors.some((e) => errorSeverityToVariant(e.severity) === 'error');
    const hasWarning = errors.some((e) => errorSeverityToVariant(e.severity) === 'warning');
    const badgeVariant = hasError ? 'error' : hasWarning ? 'warning' : 'info';
    const iconName = hasError ? 'error' : hasWarning ? 'warning' : 'info';

    return (
        <Dropdown
            closeOnClick={false}
            placement="bottom-end"
            maxWidth="26rem"
            trigger={
                <span
                    onClick={(e) => e.stopPropagation()}
                    style={{ display: 'inline-flex', alignItems: 'center', cursor: 'pointer' }}
                    title={errors.map((e) => e.message).join('\n')}
                >
                    <Badge variant={badgeVariant} size="sm">
                        <Icon name={iconName} size="xs" />
                        {!compact && errors.length > 1 ? ` ${errors.length}` : ''}
                    </Badge>
                </span>
            }
        >
            <div
                onClick={(e) => e.stopPropagation()}
                style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 'var(--bk-gap-xs)',
                    padding: 'var(--bk-spacing-2)'
                }}
            >
                {errors.map((err, idx) => {
                    const variant = errorSeverityToVariant(err.severity);
                    const rangeLabel = formatErrorRange(err.ranges);
                    const firstRange = err.ranges?.[0];
                    return (
                        <Alert key={idx} variant={variant}>
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--bk-gap-xs)' }}>
                                <Text size="xs" style={{ wordBreak: 'break-word' }}>
                                    {err.message}
                                    {err.additionalCount > 0
                                        ? ` (${(err.samples?.length || 0) + err.additionalCount} iterations affected)`
                                        : ''}
                                </Text>
                                {rangeLabel && firstRange && onNavigateRange && (
                                    <div>
                                        <Button
                                            size="xs"
                                            variant="ghost"
                                            onClick={() => onNavigateRange(firstRange)}
                                        >
                                            <Icon name="go-to-file" size="xs" /> {rangeLabel}
                                        </Button>
                                    </div>
                                )}
                            </div>
                        </Alert>
                    );
                })}
            </div>
        </Dropdown>
    );
};

interface RmlIssuesPanelProps {
    errors: RmlErrorDto[];
    activeFilter: IssueSampleFilter | null;
    onSelectFilter: (filter: IssueSampleFilter | null) => void;
    onNavigateRange?: (range: PointRange) => void;
}

export const RmlIssuesPanel: React.FC<RmlIssuesPanelProps> = ({
    errors,
    activeFilter,
    onSelectFilter,
    onNavigateRange
}) => {
    const [collapsed, setCollapsed] = useState(false);

    if (!errors || errors.length === 0) return null;

    const errorCount = errors.filter((e) => errorSeverityToVariant(e.severity) === 'error').length;
    const warningCount = errors.filter((e) => errorSeverityToVariant(e.severity) === 'warning').length;
    const infoCount = errors.length - errorCount - warningCount;

    return (
        <div
            style={{
                border: '1px solid var(--bk-color-border)',
                borderRadius: 'var(--bk-radius-md)',
                background: 'var(--bk-color-surface)',
                marginBottom: 'var(--bk-spacing-2)'
            }}
        >
            <div
                onClick={() => setCollapsed((c) => !c)}
                style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: 'var(--bk-gap-sm)',
                    padding: 'var(--bk-spacing-1-5) var(--bk-spacing-2)',
                    cursor: 'pointer',
                    userSelect: 'none'
                }}
            >
                <Icon name={collapsed ? 'chevron-right' : 'chevron-down'} size="xs" />
                <Text size="xs" weight="semibold">Issues</Text>
                {errorCount > 0 && <Badge variant="error" size="sm">{errorCount} {errorCount === 1 ? 'Error' : 'Errors'}</Badge>}
                {warningCount > 0 && <Badge variant="warning" size="sm">{warningCount} {warningCount === 1 ? 'Warning' : 'Warnings'}</Badge>}
                {infoCount > 0 && <Badge variant="info" size="sm">{infoCount} Info</Badge>}
            </div>

            {!collapsed && (
                <div
                    style={{
                        display: 'flex',
                        flexDirection: 'column',
                        gap: 'var(--bk-gap-xs)',
                        padding: '0 var(--bk-spacing-2) var(--bk-spacing-2)',
                        maxHeight: '220px',
                        overflowY: 'auto'
                    }}
                >
                    {errors.map((err, idx) => {
                        const variant = errorSeverityToVariant(err.severity);
                        const rangeLabel = formatErrorRange(err.ranges);
                        const firstRange = err.ranges?.[0];
                        const isIssueFiltered = activeFilter?.error === err && !activeFilter.originLineage;

                        return (
                            <div
                                key={idx}
                                style={{
                                    display: 'flex',
                                    flexDirection: 'column',
                                    gap: 'var(--bk-gap-xs)',
                                    padding: 'var(--bk-spacing-1-5) var(--bk-spacing-2)',
                                    borderRadius: 'var(--bk-radius-sm)',
                                    border: '1px solid var(--bk-color-border)',
                                    background: 'var(--bk-color-surface-elevated, var(--bk-color-background))'
                                }}
                            >
                                <div style={{ display: 'flex', alignItems: 'flex-start', gap: 'var(--bk-gap-sm)' }}>
                                    <Badge variant={variant} size="sm">
                                        <Icon name={variant === 'error' ? 'error' : variant === 'warning' ? 'warning' : 'info'} size="xs" />
                                    </Badge>
                                    <Text size="xs" style={{ flex: 1, wordBreak: 'break-word' }}>
                                        {err.message}
                                        {err.additionalCount > 0
                                            ? ` (${(err.samples?.length || 0) + err.additionalCount} iterations affected)`
                                            : ''}
                                    </Text>
                                    {err.samples && err.samples.length > 0 && (
                                        <Button
                                            size="xs"
                                            variant={isIssueFiltered ? 'primary' : 'ghost'}
                                            onClick={() =>
                                                onSelectFilter(
                                                    isIssueFiltered
                                                        ? null
                                                        : {
                                                            label: `Issue: ${err.message.slice(0, 36)}${err.message.length > 36 ? '...' : ''}`,
                                                            error: err
                                                        }
                                                )
                                            }
                                            title="Filter table to quads matching this issue"
                                        >
                                            <Icon name="filter" size="xs" /> Filter
                                        </Button>
                                    )}
                                    {rangeLabel && firstRange && onNavigateRange && (
                                        <Button
                                            size="xs"
                                            variant="ghost"
                                            onClick={() => onNavigateRange(firstRange)}
                                            title="Jump to mapping source"
                                        >
                                            {rangeLabel}
                                        </Button>
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>
            )}
        </div>
    );
};
