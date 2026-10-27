import React, {useEffect, useState} from 'react';
import {Alert, Button, Icon, Input, Tag, Text, TextArea, Tooltip} from 'baukasten-ui/core';
import {getVsCodeApi} from '../common/vscode';
import {RdfToken} from '../common/RdfToken';
import {PrefixAccordion} from '../common/PrefixAccordion';
import {EvaluatorFromWebviewMessage, EvaluatorToWebviewMessage} from "../protocol/evaluator";
import {EvaluateResult} from "../../generated/types";

const vscodeApi = getVsCodeApi<EvaluatorFromWebviewMessage>();

export const EvaluatorApp: React.FC = () => {
    const [cursor, setCursor] = useState<{ line: number; character: number } | undefined>();
    const [results, setResults] = useState<EvaluateResult[] | undefined>(undefined);
    const [prefixes, setPrefixes] = useState<Record<string, string>>({});
    const [emptyMessage, setEmptyMessage] = useState<string>('Move cursor or click evaluate to inspect values.');
    const [mode, setMode] = useState<string>('sample');
    const [customPayload, setCustomPayload] = useState<string>('{"Name": "Venus"}');
    const [referenceValues, setReferenceValues] = useState<{ [key: string]: string }>({});
    const [dependencies, setDependencies] = useState<any[]>([]);

    useEffect(() => {
        const handler = (event: MessageEvent<EvaluatorToWebviewMessage>) => {
            const msg = event.data;
            if (msg.command === 'render') {
                if (msg.cursor) {
                    setCursor(msg.cursor);
                }
                setResults(msg.results);
                if (msg.prefixes) {
                    setPrefixes(msg.prefixes);
                }
                if (msg.emptyMessage) {
                    setEmptyMessage(msg.emptyMessage);
                }
            } else if (msg.command === 'renderDependencies') {
                const deps = msg.dependencies || [];
                setDependencies(deps);
                const initialVals: { [key: string]: string } = {};
                for (const d of deps) {
                    if (d.referenceId) initialVals[d.referenceId] = referenceValues[d.referenceId] || '';
                    if (d.placeholders) {
                        for (const p of d.placeholders) {
                            initialVals[p] = referenceValues[p] || '';
                        }
                    }
                }
                if (Object.keys(initialVals).length === 0) {
                    initialVals['$.Name'] = '';
                }
                setReferenceValues(prev => ({...initialVals, ...prev}));
            }
        };

        window.addEventListener('message', handler);
        return () => window.removeEventListener('message', handler);
    }, [referenceValues]);

    const [iterationIndex, setIterationIndex] = useState<number>(0);

    const handleModeChange = (newMode: string, newIterIdx: number = iterationIndex) => {
        setMode(newMode);
        if (newMode === 'referenceFields') {
            vscodeApi.postMessage({command: 'fetchDependencies'});
        } else if (newMode === 'sample') {
            vscodeApi.postMessage({
                command: 'runEvaluation',
                dataContext: {type: 'range', startIndex: newIterIdx, endIndex: newIterIdx}
            });
        } else if (newMode === 'all') {
            vscodeApi.postMessage({command: 'runEvaluation', dataContext: {type: 'all'}});
        }
    };

    const handleIterationIndexChange = (newIdx: number) => {
        const validIdx = Math.max(0, newIdx);
        setIterationIndex(validIdx);
        vscodeApi.postMessage({
            command: 'runEvaluation',
            dataContext: {type: 'range', startIndex: validIdx, endIndex: validIdx}
        });
    };

    const handleApplyCustom = () => {
        vscodeApi.postMessage({
            command: 'runEvaluation',
            dataContext: {type: 'rawString', rawString: customPayload}
        });
    };

    const handleApplyReferenceValues = () => {
        vscodeApi.postMessage({
            command: 'runEvaluation',
            dataContext: {type: 'referenceValues', referenceValues}
        });
    };

    const handleRefresh = () => {
        if (mode === 'referenceFields') {
            handleApplyReferenceValues();
        } else if (mode === 'customRaw') {
            handleApplyCustom();
        } else if (mode === 'sample') {
            handleIterationIndexChange(iterationIndex);
        } else {
            vscodeApi.postMessage({command: 'refresh'});
        }
    };

    const firstResult = results && results.length > 0 ? results[0] : undefined;
    const evalMode = firstResult?.evaluationMode;
    const isIndependent = evalMode === 'INDEPENDENT_EVALUATION';

    return (
        <div className="bk-app-container">
            {firstResult && firstResult.traceSummary && firstResult.traceSummary.length > 0 && (
                <div style={{
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 'var(--bk-gap-xs)',
                    paddingBottom: 'var(--bk-gap-xl)',
                    borderBottom: '1px solid var(--bk-color-border)'
                }}>
                    <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
                        <div style={{display: 'flex', alignItems: 'center', gap: 'var(--bk-gap-xs)'}}>
                            <Text size="xs" weight="semibold" color="muted" style={{textTransform: 'uppercase'}}>
                                Inline Evaluations
                            </Text>
                            <Tooltip
                                content={
                                    isIndependent
                                        ? "Results shown are from the first iteration of each TriplesMap logical source."
                                        : "Inlay hints in the Turtle editor are synchronized with this scope. Stepping forward/back follows join conditions between TriplesMaps."
                                }
                                variant="info"
                            >
                                <Button variant="ghost" size="xs" circular aria-label="Inline evaluation info">
                                    <Icon name="info"/>
                                </Button>
                            </Tooltip>
                        </div>
                        <div style={{display: 'flex', alignItems: 'center', gap: 'var(--bk-gap-xs)'}}>
                            <Button
                                variant="ghost"
                                size="xs"
                                circular
                                title="Previous Inline Iteration"
                                aria-label="Previous Inline Iteration"
                                onClick={() => vscodeApi.postMessage({command: 'stepInlineIteration', delta: -1})}
                            >
                                <Icon name="chevron-left"/>
                            </Button>
                            <Button
                                variant="ghost"
                                size="xs"
                                circular
                                title="Next Inline Iteration"
                                aria-label="Next Inline Iteration"
                                onClick={() => vscodeApi.postMessage({command: 'stepInlineIteration', delta: 1})}
                            >
                                <Icon name="chevron-right"/>
                            </Button>
                        </div>
                    </div>
                    {firstResult.traceSummary.map((line, lIdx) => renderTraceLine(line, lIdx))}
                </div>
            )}

            <div style={{display: 'flex', alignItems: 'center', justifyContent: 'space-between'}}>
                <Text size="xs" weight="semibold" color="muted" style={{textTransform: 'uppercase'}}>
                    Value Inspector
                </Text>
                <Button size="sm" variant="secondary" onClick={handleRefresh} title="Refresh">
                    ↻ Refresh
                </Button>
            </div>

            {firstResult && (firstResult.targetNodeType || firstResult.targetParentTriplesMap) && (
                <div
                    style={{
                        padding: 'var(--bk-padding-sm)',
                        background: 'var(--bk-color-background-secondary)',
                        borderRadius: 'var(--bk-radius-sm)',
                        border: '1px solid var(--bk-color-border)',
                        display: 'flex',
                        flexDirection: 'column',
                        gap: '3px'
                    }}
                >
                    <div
                        style={{display: 'flex', alignItems: 'center', gap: 'var(--bk-gap-xs)', flexWrap: 'wrap'}}>
                        {firstResult.targetNodeType && (
                            <Tag size="xs" variant="info">
                                {firstResult.targetNodeType}
                            </Tag>
                        )}
                        {firstResult.targetParentTriplesMap && (
                            <Text size="xs" color="muted">
                                in <span style={{
                                fontFamily: 'monospace',
                                fontWeight: 600
                            }}>{firstResult.targetParentTriplesMap}</span>
                            </Text>
                        )}
                    </div>
                    {firstResult.targetNodeSummary && (
                        <Text size="xs" monospace block color="muted" style={{wordBreak: 'break-all'}}>
                            {firstResult.targetNodeSummary}
                        </Text>
                    )}
                </div>
            )}

            <div className="bk-controls-row">
                <select
                    className="bk-select"
                    value={mode}
                    onChange={(e) => handleModeChange(e.target.value)}
                >
                    <option value="sample">Sample Iteration</option>
                    <option value="all">All Data</option>
                    <option value="referenceFields">Reference Field Values</option>
                    <option value="customRaw">Custom Payload (JSON/CSV)</option>
                </select>

                {mode === 'sample' && (
                    <div style={{display: 'flex', alignItems: 'center', gap: 'var(--bk-gap-xs)'}}>
                        <Text size="xs" color="muted">Iter:</Text>
                        <input
                            type="number"
                            min={0}
                            value={iterationIndex}
                            onChange={(e) => handleIterationIndexChange(parseInt(e.target.value, 10) || 0)}
                            style={{
                                width: '50px',
                                background: 'var(--bk-color-input-background)',
                                color: 'var(--bk-color-input-foreground)',
                                border: '1px solid var(--bk-color-input-border)',
                                borderRadius: 'var(--bk-radius-sm)',
                                padding: '3px 6px',
                                fontSize: '12px'
                            }}
                        />
                    </div>
                )}
            </div>

            {mode === 'referenceFields' && (
                <div className="bk-panel-card">
                    <div className="bk-field-grid">
                        {Object.keys(referenceValues).map((key) => (
                            <div key={key} className="bk-field-row">
                                <label className="bk-field-label">{key}</label>
                                <Input
                                    size="sm"
                                    value={referenceValues[key] || ''}
                                    placeholder="value..."
                                    onChange={(e) => setReferenceValues({...referenceValues, [key]: e.target.value})}
                                />
                            </div>
                        ))}
                    </div>
                    <Button size="sm" variant="primary" onClick={handleApplyReferenceValues}>
                        Apply & Run
                    </Button>
                </div>
            )}

            {mode === 'customRaw' && (
                <div className="bk-panel-card">
                    <TextArea
                        rows={4}
                        value={customPayload}
                        onChange={(e) => setCustomPayload(e.target.value)}
                        placeholder='{"Name": "Venus"}'
                    />
                    <div style={{marginTop: '6px'}}>
                        <Button size="sm" variant="primary" onClick={handleApplyCustom}>
                            Apply & Run
                        </Button>
                    </div>
                </div>
            )}

            <div className="bk-results-container">
                {(!results || results.length === 0) ? (
                    <div className="bk-empty-state">{emptyMessage}</div>
                ) : (
                    results.map((iter, idx) => (
                        <div key={iter.iterationId || idx} className="bk-iteration-card">
                            <div className="bk-card-header">
                                <span>Iteration #{iter.iterationId || '0'}</span>
                            </div>
                            <div className="bk-card-body">
                                {iter.diagnostics && iter.diagnostics.length > 0 && (
                                    <div style={{marginBottom: '8px'}}>
                                        {iter.diagnostics.map((d: string, dIdx: number) => (
                                            <Alert key={dIdx} variant="error">
                                                {d}
                                            </Alert>
                                        ))}
                                    </div>
                                )}

                                {iter.iterationData && (
                                    <div className="bk-section-block">
                                        <Tag size="xs" variant="info">INPUT</Tag>
                                        <pre className="bk-code-box">
                                            {formatJsonString(iter.iterationData)}
                                        </pre>
                                    </div>
                                )}

                                <div className="bk-section-block">
                                    <Tag size="xs" variant="success">OUTPUT</Tag>
                                    {iter.generatedRdf && iter.generatedRdf.length > 0 ? (
                                        <div className="bk-token-stack">
                                            {iter.generatedRdf.map((rdf: any, rIdx: number) => (
                                                <RdfToken key={rIdx} rdf={rdf} prefixMap={prefixes}/>
                                            ))}
                                        </div>
                                    ) : (
                                        <div className="bk-empty-hint">(No terms generated)</div>
                                    )}
                                </div>
                            </div>
                        </div>
                    ))
                )}
            </div>

            <PrefixAccordion prefixes={prefixes}/>
        </div>
    );
};

function renderTraceLine(line: string, index: number) {
    const match = line.match(/^((?:<https?:\/\/[^>]+>|https?:\/\/\S+))\s+(.+)$/);
    if (match) {
        const [, iri, rest] = match;
        return (
            <div key={index} style={{display: 'flex', flexDirection: 'column', gap: '2px'}}>
                <Text size="sm" monospace block style={{wordBreak: 'break-all'}}>
                    {iri}
                </Text>
                <Text size="xs" monospace block color="muted"
                      style={{paddingLeft: 'var(--bk-spacing-2)', wordBreak: 'break-all'}}>
                    {rest}
                </Text>
            </div>
        );
    }
    return (
        <Text key={index} size="sm" monospace block style={{wordBreak: 'break-all'}}>
            {line}
        </Text>
    );
}

function formatJsonString(raw: string): string {
    try {
        const parsed = JSON.parse(raw);
        return JSON.stringify(parsed, null, 2);
    } catch {
        return raw;
    }
}
