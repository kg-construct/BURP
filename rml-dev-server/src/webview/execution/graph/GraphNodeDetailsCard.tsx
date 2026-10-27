import React from 'react';
import {Button, Tag} from 'baukasten-ui/core';
import { GraphNode } from './graphModel';
import { vscodeApi } from '../../common/vscode';
import {ExecutionFromWebviewMessage} from "../../protocol/execution";

export interface GraphNodeDetailsCardProps {
    node: GraphNode;
    onClose: () => void;
}

export const GraphNodeDetailsCard: React.FC<GraphNodeDetailsCardProps> = ({
    node,
    onClose
}) => {
    return (
        <div className="bk-graph-node-details">
            <div className="bk-node-details-header">
                <Tag
                    size="xs"
                    color={
                        node.termType === 'NamedNode'
                            ? 'info'
                            : node.termType === 'BlankNode'
                            ? 'secondary'
                            : 'success'
                    }
                >
                    {node.termType}
                </Tag>
                <span className="bk-node-details-title">{node.label}</span>
                <button
                    type="button"
                    className="bk-details-close"
                    onClick={onClose}
                    title="Close details"
                >
                    &times;
                </button>
            </div>
            <div className="bk-node-details-body">
                <div className="bk-detail-row">
                    <span className="bk-detail-lbl">Value:</span>
                    <span className="bk-detail-val">{node.fullName}</span>
                </div>
                {node.graphName && node.graphName !== '(default)' && (
                    <div className="bk-detail-row">
                        <span className="bk-detail-lbl">Graph:</span>
                        <span className="bk-detail-val">{node.graphName}</span>
                    </div>
                )}
                {node.datatype && (
                    <div className="bk-detail-row">
                        <span className="bk-detail-lbl">Datatype:</span>
                        <span className="bk-detail-val">{node.datatype}</span>
                    </div>
                )}
                {node.language && (
                    <div className="bk-detail-row">
                        <span className="bk-detail-lbl">Language:</span>
                        <span className="bk-detail-val">@{node.language}</span>
                    </div>
                )}
            </div>
        </div>
    );
};
