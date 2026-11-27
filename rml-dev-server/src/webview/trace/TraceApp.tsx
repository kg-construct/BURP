import React, {useEffect, useState} from 'react';
import {Alert, Button, Spinner} from 'baukasten-ui/core';
import {getVsCodeApi} from '../common/vscode';
import {Range, TraceResult} from '../../generated/types';
import {TraceGraphView} from './TraceGraphView';
import {TraceFromWebviewMessage, TraceQuadTarget, TraceToWebviewMessage} from "../protocol/trace";

interface TracePayload {
    result: TraceResult;
    mode: 'iteration' | 'result';
    iterationIndex?: number;
    targetQuad?: TraceQuadTarget;
    title?: string;
}

const vscodeApi = getVsCodeApi<TraceFromWebviewMessage>();

export const TraceApp: React.FC = () => {
    const [loading, setLoading] = useState<boolean>(true);
    const [error, setError] = useState<string | null>(null);
    const [tracePayload, setTracePayload] = useState<TracePayload | null>(null);

    useEffect(() => {
        const handler = (event: MessageEvent<TraceToWebviewMessage>) => {
            const msg = event.data;
            if (!msg) return;

            switch (msg.command) {
                case 'traceLoading':
                    setLoading(true);
                    setError(null);
                    break;
                case 'traceError':
                    setLoading(false);
                    setError(msg.error || 'Unknown trace error');
                    break;
                case 'traceData':
                    setLoading(false);
                    setError(null);
                    setTracePayload({
                        result: msg.result,
                        mode: msg.mode,
                        iterationIndex: msg.iterationIndex,
                        targetQuad: msg.targetQuad,
                        title: msg.title
                    });
                    break;
            }
        };

        window.addEventListener('message', handler);
        vscodeApi.postMessage({command: 'ready'});

        return () => window.removeEventListener('message', handler);
    }, []);

    const handleRefresh = () => {
        setLoading(true);
        setError(null);
        vscodeApi.postMessage({command: 'refresh'});
    };

    if (loading && !tracePayload) {
        return (
            <div style={{
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
                gap: '12px'
            }}>
                <Spinner size="md"/>
                <span style={{color: 'var(--vscode-descriptionForeground)', fontSize: '13px'}}>
                    Tracing execution dataflow...
                </span>
            </div>
        );
    }

    if (error && !tracePayload) {
        return (
            <div style={{padding: '24px', maxWidth: '600px', margin: '0 auto'}}>
                <Alert variant="error">
                    <div style={{display: 'flex', flexDirection: 'column', gap: '8px'}}>
                        <span style={{fontWeight: 'bold'}}>Trace Failed</span>
                        <span>{error}</span>
                        <div>
                            <Button size="sm" variant="secondary" onClick={handleRefresh}>
                                Retry
                            </Button>
                        </div>
                    </div>
                </Alert>
            </div>
        );
    }

    if (!tracePayload || !tracePayload.result) {
        return (
            <div style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                height: '100%',
                color: 'var(--vscode-descriptionForeground)'
            }}>
                No trace data available.
            </div>
        );
    }

    const handleSelectRange = (range: Range) => {
        vscodeApi.postMessage({command: 'selectRange', range});
    };

    return (
        <TraceGraphView
            result={tracePayload.result}
            mode={tracePayload.mode}
            iterationIndex={tracePayload.iterationIndex}
            targetQuad={tracePayload.targetQuad}
            title={tracePayload.title}
            onRefresh={handleRefresh}
            onSelectRange={handleSelectRange}
        />
    );
};
