import React from 'react';
import {createRoot} from 'react-dom/client';
// @ts-ignore
import './styles.css';
import {EvaluatorApp} from './evaluator/EvaluatorApp';
import {ExecutionApp} from './execution/ExecutionApp';
import {AbstractLogicalSourceApp as SourceDataTableApp} from './source/SourceDataTableApp';
import {PlanApp} from './plan/PlanApp';
import {TraceApp} from './trace/TraceApp';

declare global {
    interface Window {
        __VIEW_TYPE__?: string;
    }
}

const rootElement = document.getElementById('root');
if (rootElement) {
    const viewType = window.__VIEW_TYPE__ || rootElement.getAttribute('data-view') || 'evaluator';
    const root = createRoot(rootElement);

    if (viewType === 'structure') {
        root.render(<PlanApp/>);
    } else if (viewType === 'trace') {
        root.render(<TraceApp/>);
    } else if (viewType === 'sourceDataTable' || viewType === 'logicalViewTable') {
        root.render(<SourceDataTableApp/>);
    } else if (viewType === 'rdfviewer') {
        root.render(<ExecutionApp isRdfVisualizer />);
    } else if (viewType === 'execution') {
        root.render(<ExecutionApp/>);
    } else {
        root.render(<EvaluatorApp/>);
    }
}
