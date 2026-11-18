import React from 'react';
import {Button} from 'baukasten-ui/core';
import {getVsCodeApi} from '../common/vscode';
import {PlanFromWebviewMessage} from '../protocol/plan';

const vscodeApi = getVsCodeApi<PlanFromWebviewMessage>();

const TRIPLES_MAP_ASCII = `
╭────────────────────────────────────────╮
│             rml:TriplesMap             │
│ ╭────────────────────────────────────╮ │
│ │  rml:logicalSource   :Source       │ │
│ │  rml:subjectMap      ex:{id}       │ │
│ │  rml:predicateObjectMap            │ │
│ │    ├── rml:predicate foaf:name     │ │
│ │    └── rml:objectMap "Awesome RDF" │ │
│ ╰────────────────────────────────────╯ │
╰────────────────────────────────────────╯
`.trim();

export const PlanApp: React.FC = () => {
    const handleOpenIssues = () => {
        vscodeApi.postMessage({command: 'openIssues'});
    };

    return (
        <div
            className="bk-app-container"
            style={{
                padding: '16px',
                display: 'flex',
                flexDirection: 'column',
                gap: '16px',
                height: '100%',
                boxSizing: 'border-box',
                overflowY: 'auto'
            }}
        >


            <div
                style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: 'var(--bk-gap-xl)',
                }}
            >
                <pre className="--bk-font-family-mono"
                     style={{color: 'var(--bk-subj-color, #38bdf8)'}}>
                    {TRIPLES_MAP_ASCII}
                </pre>

                <div
                    style={{
                        display: 'flex',
                        flexDirection: 'column',
                        textAlign: 'center',
                        maxWidth: '320px',
                        gap: 'var(--bk-gap-sm)',
                    }}
                >
                    <div className="--bk-font-size-lg">
                        Under Redesign 🏗️
                    </div>
                    <div>
                        The Plan Node Hierarchy is being redesigned to provide cleaner execution plan.
                    </div>
                    <div>
                        Have ideas, suggestions, or specific use cases? We'd love your feedback!
                    </div>
                </div>

                <Button
                    variant="primary"
                    size="sm"
                    onClick={handleOpenIssues}
                    style={{marginTop: 'var(--bk-gap-sm)'}}
                >
                    💬 Contact Us / Open an Issue
                </Button>
            </div>
        </div>
    );
};
