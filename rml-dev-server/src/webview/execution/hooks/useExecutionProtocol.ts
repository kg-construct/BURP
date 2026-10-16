import { useState, useEffect } from 'react';
import { getVsCodeApi } from '../../common/vscode';
import { ExecuteResult, LogicalTargetInfo, QuadChunkNotification, TargetExecutionResult } from '../../../generated/types';
import { ParsedQuadItem, convertStatementsToParsedQuads } from '../nquadsParser';
import { DEFAULT_PREFIXES } from '../rdfUtils';
import { ExecutionFromWebviewMessage, ExecutionToWebviewMessage } from '../../protocol/execution';

export interface TargetState {
    targetId: string;
    logicalTarget?: LogicalTargetInfo;
    quads: ParsedQuadItem[];
}

export const vscodeApi = getVsCodeApi<ExecutionFromWebviewMessage>();

/**
 * Manages VS Code webview protocol communication, JSON-RPC streaming notifications,
 * and accumulator state for RML execution chunks and results.
 *
 * - Decouples low-level VS Code message passing and JSON-RPC protocol events from UI rendering.
 * - Handles progressive quad chunk accumulation (`quadChunk`), full completion (`executionComplete`),
 *   errors (`executionError`), and session restores (`sessionState`).
 * - Exposes high-level execution control actions (`rerun`, `clearCacheAndRerun`).
 */
export function useExecutionProtocol() {
    const [fileName, setFileName] = useState<string>('');
    const [status, setStatus] = useState<'idle' | 'running' | 'completed' | 'error'>('idle');
    const [error, setError] = useState<string | null>(null);
    const [targets, setTargets] = useState<Map<string, TargetState>>(new Map());
    const [prefixes, setPrefixes] = useState<Record<string, string>>(DEFAULT_PREFIXES);
    const [logicalSources, setLogicalSources] = useState<Record<string, string>>({});
    const [defaultHullMode, setDefaultHullMode] = useState<string>('smoothHull');

    useEffect(() => {
        const handler = (event: MessageEvent<ExecutionToWebviewMessage>) => {
            const msg = event.data;

            if (msg.type === 'startExecution') {
                setFileName(msg.fileName || '');
                setStatus('running');
                setError(null);
                setTargets(new Map());
                setLogicalSources({});
                if (msg.graphBackgroundMethod) {
                    setDefaultHullMode(msg.graphBackgroundMethod);
                }
                if (msg.prefixes) {
                    setPrefixes(msg.prefixes);
                }
            } else if (msg.type === 'quadChunk') {
                const chunk: QuadChunkNotification = msg.chunk;
                const targetId = chunk.targetId || 'default';
                const newQuads = convertStatementsToParsedQuads(
                    chunk.statements,
                    `${targetId}-c${chunk.chunkIndex}`,
                    targetId
                );

                if (chunk.logicalSources) {
                    setLogicalSources((prev) => ({ ...prev, ...chunk.logicalSources }));
                }

                setTargets((prev) => {
                    const next = new Map(prev);
                    const existing = next.get(targetId) || {
                        targetId,
                        quads: []
                    };
                    next.set(targetId, {
                        ...existing,
                        quads: [...existing.quads, ...newQuads]
                    });
                    return next;
                });
            } else if (msg.type === 'executionComplete') {
                const result: ExecuteResult = msg.result;
                if (result && result.reportUpdate && result.reportUpdate.startsWith('Execution error:')) {
                    setStatus('error');
                    setError(result.reportUpdate);
                    return;
                }
                setStatus('completed');
                if (result && (result as any).prefixMap) {
                    setPrefixes((result as any).prefixMap);
                }
                if (result && result.logicalSources) {
                    setLogicalSources((prev) => ({ ...prev, ...result.logicalSources }));
                }

                if (result && result.targets) {
                    setTargets((prev) => {
                        const next = new Map(prev);
                        for (const [tId, targetRes] of Object.entries(result.targets)) {
                            const res = targetRes as TargetExecutionResult;
                            const existing = next.get(tId);
                            if (!existing || existing.quads.length === 0) {
                                const parsed = convertStatementsToParsedQuads(res.statements, tId, tId);
                                next.set(tId, {
                                    targetId: tId,
                                    logicalTarget: res.logicalTarget,
                                    quads: parsed
                                });
                            } else {
                                next.set(tId, {
                                    ...existing,
                                    logicalTarget: res.logicalTarget || existing.logicalTarget
                                });
                            }
                        }
                        return next;
                    });
                }
            } else if (msg.type === 'executionError') {
                setStatus('error');
                setError(msg.error);
            } else if (msg.type === 'sessionState') {
                const session = msg.session;
                if (session) {
                    setFileName(session.fileName || '');
                    setStatus(session.status);
                    setError(session.error || null);
                    if (session.graphBackgroundMethod) {
                        setDefaultHullMode(session.graphBackgroundMethod);
                    }
                    if (session.prefixes && Object.keys(session.prefixes).length > 0) {
                        setPrefixes(session.prefixes);
                    }
                    if (session.result?.prefixMap && Object.keys(session.result.prefixMap).length > 0) {
                        setPrefixes(session.result.prefixMap);
                    }
                    if (session.result?.logicalSources) {
                        setLogicalSources(session.result.logicalSources);
                    }
                    if (session.result?.targets) {
                        const next = new Map<string, TargetState>();
                        for (const [tId, targetRes] of Object.entries(session.result.targets)) {
                            const res = targetRes as TargetExecutionResult;
                            const parsed = convertStatementsToParsedQuads(res.statements, tId, tId);
                            next.set(tId, {
                                targetId: tId,
                                logicalTarget: res.logicalTarget,
                                quads: parsed
                            });
                        }
                        setTargets(next);
                    }
                }
            }
        };

        window.addEventListener('message', handler);
        vscodeApi.postMessage({ command: 'ready' });
        return () => window.removeEventListener('message', handler);
    }, []);

    const rerun = () => {
        vscodeApi.postMessage({ command: 'rerun' });
    };

    const clearCacheAndRerun = () => {
        vscodeApi.postMessage({ command: 'clearCacheAndRerun' });
    };

    return {
        fileName,
        status,
        error,
        targets,
        prefixes,
        logicalSources,
        defaultHullMode,
        rerun,
        clearCacheAndRerun
    };
}
