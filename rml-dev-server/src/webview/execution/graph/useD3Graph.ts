import { useEffect, useRef, useState } from 'react';
import * as d3 from 'd3';
import { GraphNode, GraphLink } from './graphModel';
import {
    GraphHullMethod,
    computeSmoothHull,
    computeBubbleSetHull,
    computeConcaveHull,
    computeGraphBadgePosition
} from './hullGenerators';
import { shortenIri } from '../rdfUtils';

export interface UseD3GraphProps {
    nodes: GraphNode[];
    links: GraphLink[];
    subjectOrder: string[];
    graphColorMap: Map<string, string>;
    layoutMode: 'list' | 'organic';
    hullMode: GraphHullMethod;
    prefixMap?: Record<string, string>;
    activeMatchedNode: GraphNode | null;
    onSelectNode: (node: GraphNode) => void;
}

export function useD3Graph({
    nodes,
    links,
    subjectOrder,
    graphColorMap,
    layoutMode,
    hullMode,
    prefixMap,
    activeMatchedNode,
    onSelectNode
}: UseD3GraphProps) {
    const svgRef = useRef<SVGSVGElement>(null);
    const containerRef = useRef<HTMLDivElement>(null);
    const zoomBehaviorRef = useRef<d3.ZoomBehavior<SVGSVGElement, unknown> | null>(null);
    const simulationRef = useRef<d3.Simulation<GraphNode, GraphLink> | null>(null);
    const [pinnedCount, setPinnedCount] = useState<number>(0);

    // Pan and zoom camera to the active search match
    useEffect(() => {
        if (!activeMatchedNode || !svgRef.current || !containerRef.current || !zoomBehaviorRef.current) {
            return;
        }

        const width = containerRef.current.clientWidth || 800;
        const height = containerRef.current.clientHeight || 600;
        const svg = d3.select(svgRef.current);
        const zoom = zoomBehaviorRef.current;

        const nx = activeMatchedNode.x ?? width / 2;
        const ny = activeMatchedNode.y ?? height / 2;

        const currentTransform = d3.zoomTransform(svgRef.current);
        const targetScale = Math.max(currentTransform.k, 1.3);

        svg.transition()
            .duration(600)
            .ease(d3.easeCubicOut)
            .call(
                zoom.transform,
                d3.zoomIdentity
                    .translate(width / 2 - nx * targetScale, height / 2 - ny * targetScale)
                    .scale(targetScale)
            );
    }, [activeMatchedNode]);

    // Keep SVG viewBox and center force dynamically responsive to container resizing
    useEffect(() => {
        if (!containerRef.current || !svgRef.current) {
            return;
        }

        const observer = new ResizeObserver((entries) => {
            for (const entry of entries) {
                const { width, height } = entry.contentRect;
                if (width > 0 && height > 0 && svgRef.current) {
                    d3.select(svgRef.current).attr('viewBox', [0, 0, width, height]);
                    if (simulationRef.current) {
                        simulationRef.current.force('center', d3.forceCenter(width / 2, height / 2));
                        simulationRef.current.alpha(0.05).restart();
                    }
                }
            }
        });

        observer.observe(containerRef.current);
        return () => observer.disconnect();
    }, []);

    // Main D3 simulation and rendering
    useEffect(() => {
        if (!svgRef.current || !containerRef.current || nodes.length === 0) {
            return;
        }

        const width = containerRef.current.clientWidth || 800;
        const height = containerRef.current.clientHeight || 600;

        const svg = d3.select(svgRef.current);
        svg.selectAll('*').remove();

        svg.attr('viewBox', [0, 0, width, height]);

        // Defs: markers & gooey filter
        const defs = svg.append('defs');

        // Arrow marker
        defs.append('marker')
            .attr('id', 'arrow')
            .attr('viewBox', '0 -5 10 10')
            .attr('refX', 24)
            .attr('refY', 0)
            .attr('markerWidth', 6)
            .attr('markerHeight', 6)
            .attr('orient', 'auto')
            .append('path')
            .attr('fill', '#94a3b8')
            .attr('d', 'M0,-5L10,0L0,5');

        // Gooey Metaball SVG Filter for the liquid background
        const gooeyFilter = defs.append('filter')
            .attr('id', 'gooey-filter')
            .attr('x', '-50%')
            .attr('y', '-50%')
            .attr('width', '200%')
            .attr('height', '200%')
            .attr('color-interpolation-filters', 'sRGB');

        gooeyFilter.append('feGaussianBlur')
            .attr('in', 'SourceGraphic')
            .attr('stdDeviation', '16')
            .attr('result', 'blur');

        gooeyFilter.append('feColorMatrix')
            .attr('in', 'blur')
            .attr('mode', 'matrix')
            .attr('values', '1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 20 -9')
            .attr('result', 'goo');

        const compTransfer = gooeyFilter.append('feComponentTransfer')
            .attr('in', 'goo');
        compTransfer.append('feFuncA')
            .attr('type', 'linear')
            .attr('slope', '0.18');

        // Main zooming layer
        const g = svg.append('g');

        const zoom = d3.zoom<SVGSVGElement, unknown>()
            .scaleExtent([0.1, 4])
            .on('zoom', (event) => {
                g.attr('transform', event.transform);
            });

        zoomBehaviorRef.current = zoom;
        svg.call(zoom);

        // Precompute iteration vertical targets if in 'list' mode
        const iterSpacing = 160;
        const startY = height / 2 - ((subjectOrder.length - 1) * iterSpacing) / 4;

        nodes.forEach((n) => {
            n.targetY = startY + n.iterationIndex * iterSpacing;
        });

        // Compute cluster centers for graphs
        const uniqueGraphNames = Array.from(graphColorMap.keys());
        const graphCenters = new Map<string, { x: number; y: number }>();
        uniqueGraphNames.forEach((gName, idx) => {
            if (uniqueGraphNames.length === 1) {
                graphCenters.set(gName, { x: width / 2, y: height / 2 });
            } else {
                const angle = (idx / uniqueGraphNames.length) * 2 * Math.PI;
                const radius = Math.min(width, height) * 0.28;
                graphCenters.set(gName, {
                    x: width / 2 + Math.cos(angle) * radius,
                    y: height / 2 + Math.sin(angle) * radius
                });
            }
        });

        // Force Simulation with normalized link strength and dynamic label distance
        const simulation = d3.forceSimulation<GraphNode>(nodes)
            .force(
                'link',
                d3.forceLink<GraphNode, GraphLink>(links)
                    .id((d) => d.id)
                    .distance((d) => d.targetDistance)
                    .strength((d) => d.springStrength)
            )
            .force('charge', d3.forceManyBody().strength(-150))
            .force('collision', d3.forceCollide().radius((d: any) => (d.termType === 'Literal' ? 34 : 26)));

        // Inter-Iteration Repulsion in Free Organic Mode:
        // Pushes distinct subjects apart to prevent overlapping iterations
        if (layoutMode === 'organic') {
            simulation.force('interIterationRepulsion', (alpha) => {
                const n = nodes.length;
                for (let i = 0; i < n; i++) {
                    for (let j = i + 1; j < n; j++) {
                        const a = nodes[i];
                        const b = nodes[j];
                        const aKey = a.iterationKey || a.primarySubject;
                        const bKey = b.iterationKey || b.primarySubject;
                        if (aKey !== bKey) {
                            const dx = (b.x || 0) - (a.x || 0);
                            const dy = (b.y || 0) - (a.y || 0);
                            const dist2 = dx * dx + dy * dy;
                            const threshold = 220;
                            if (dist2 < threshold * threshold && dist2 > 0) {
                                const dist = Math.sqrt(dist2);
                                const force = ((threshold - dist) / threshold) * alpha * 48;
                                const fx = (dx / dist) * force;
                                const fy = (dy / dist) * force;
                                a.vx = (a.vx || 0) - fx;
                                a.vy = (a.vy || 0) - fy;
                                b.vx = (b.vx || 0) + fx;
                                b.vy = (b.vy || 0) + fy;
                            }
                        }
                    }
                }
            });
        }

        // Primary Clustering Force: Strong attraction per graph
        if (uniqueGraphNames.length > 1) {
            simulation.force('graphCluster', (alpha) => {
                for (const n of nodes) {
                    const center = graphCenters.get(n.graphName);
                    if (center) {
                        n.vx = (n.vx || 0) + (center.x - (n.x || 0)) * alpha * 0.22;
                        n.vy = (n.vy || 0) + (center.y - (n.y || 0)) * alpha * 0.22;
                    }
                }
            });
        }

        // Secondary Arrangement: List of Organic vs Free Organic
        if (layoutMode === 'list') {
            simulation
                .force('forceY', d3.forceY<GraphNode>((d) => d.targetY || height / 2).strength(0.35))
                .force('forceX', d3.forceX(width / 2).strength(0.06));
        } else {
            simulation
                .force('center', d3.forceCenter(width / 2, height / 2))
                .force('forceY', d3.forceY(height / 2).strength(0.04))
                .force('forceX', d3.forceX(width / 2).strength(0.04));
        }

        simulationRef.current = simulation;

        // ---------------------------------------------------------------------
        // Layer 1: Named Graph Background Effect (Gooey, Smooth Hull, BubbleSet, Concave Hull)
        // ---------------------------------------------------------------------
        const namedGraphNames = Array.from(graphColorMap.keys()).filter((g) => g !== '(default)');

        // Persistent smoothed badge coordinates for jitter-free tracking
        const smoothedBadgePositions = new Map<string, { x: number; y: number }>();

        // Cache for BubbleSet settling phase to avoid micro-marching grid jitter
        const lastBubbleNodePos = new Map<string, Map<string, { x: number; y: number }>>();

        // 1. Gooey blobs elements
        const graphBlobElements: Array<{
            graphName: string;
            color: string;
            gNodes: GraphNode[];
            nodeCircles: d3.Selection<SVGCircleElement, GraphNode, SVGGElement, unknown>;
            linkLines: d3.Selection<SVGPathElement, GraphLink, SVGGElement, unknown>;
        }> = [];

        function getNodesForGraph(gName: string) {
            const nodeIds = new Set<string>();
            links.filter((l) => l.graphName === gName).forEach((l: any) => {
                const sId = typeof l.source === 'object' ? l.source.id : l.source;
                const tId = typeof l.target === 'object' ? l.target.id : l.target;
                nodeIds.add(sId);
                nodeIds.add(tId);
            });
            nodes.filter((n) => n.graphName === gName).forEach((n) => nodeIds.add(n.id));
            return nodes.filter((n) => nodeIds.has(n.id));
        }

        // 2. Smooth / BubbleSet / Concave path elements
        const graphHullElements: Array<{
            graphName: string;
            color: string;
            pathEl: d3.Selection<SVGPathElement, unknown, SVGGElement, unknown>;
        }> = [];

        if (hullMode === 'gooey') {
            const blobsGroup = g.append('g')
                .attr('class', 'bk-graph-blobs')
                .attr('filter', 'url(#gooey-filter)');

            namedGraphNames.forEach((gName) => {
                const gColor = graphColorMap.get(gName) || '#64748b';
                const gNodes = getNodesForGraph(gName);
                const gLinks = links.filter((l) => l.graphName === gName);

                const singleGraphGroup = blobsGroup.append('g')
                    .attr('class', `bk-blob-graph-${gName}`);

                const linkLines = singleGraphGroup.append('g')
                    .selectAll('path')
                    .data(gLinks)
                    .join('path')
                    .attr('fill', 'none')
                    .attr('stroke', gColor)
                    .attr('stroke-width', 34)
                    .attr('stroke-linecap', 'round');

                const nodeCircles = singleGraphGroup.append('g')
                    .selectAll('circle')
                    .data(gNodes)
                    .join('circle')
                    .attr('r', 36)
                    .attr('fill', gColor);

                graphBlobElements.push({
                    graphName: gName,
                    color: gColor,
                    gNodes,
                    nodeCircles,
                    linkLines
                });
            });
        } else if (hullMode !== 'disabled') {
            const hullsGroup = g.append('g').attr('class', 'bk-graph-hulls');

            namedGraphNames.forEach((gName) => {
                const gColor = graphColorMap.get(gName) || '#64748b';
                const pathEl = hullsGroup.append('path')
                    .attr('class', `bk-graph-hull bk-hull-${gName}`)
                    .attr('fill', gColor)
                    .attr('fill-opacity', 0.13)
                    .attr('stroke', gColor)
                    .attr('stroke-opacity', 0.42)
                    .attr('stroke-width', 1.5)
                    .attr('stroke-linejoin', 'round');

                graphHullElements.push({
                    graphName: gName,
                    color: gColor,
                    pathEl
                });
            });
        }

        // ---------------------------------------------------------------------
        // Layer 1.5: Graph Identifier Badges in a stronger shade
        // ---------------------------------------------------------------------
        const graphBadgeElements: Array<{
            graphName: string;
            badgeGroup: d3.Selection<SVGGElement, unknown, SVGGElement, unknown>;
        }> = [];

        if (hullMode !== 'disabled') {
            const badgesGroup = g.append('g').attr('class', 'bk-graph-badges');

            namedGraphNames.forEach((gName) => {
                const gColor = graphColorMap.get(gName) || '#64748b';
                const shortName = shortenIri(gName, prefixMap).display;

                const badge = badgesGroup.append('g')
                    .attr('class', `bk-graph-badge bk-badge-${gName}`)
                    .style('cursor', 'pointer');

                // Solid graph identifier circle on the envelope path
                badge.append('circle')
                    .attr('r', 11)
                    .attr('fill', gColor)
                    .attr('stroke', '#ffffff')
                    .attr('stroke-width', 2)
                    .style('filter', 'drop-shadow(0 2px 4px rgba(0,0,0,0.35))');

                // Label UNDERNEATH the node
                badge.append('text')
                    .attr('font-size', '10px')
                    .attr('font-weight', '600')
                    .attr('fill', 'var(--vscode-foreground, #fff)')
                    .attr('stroke', 'var(--vscode-editor-background, #1e1e1e)')
                    .attr('stroke-width', 2.5)
                    .attr('paint-order', 'stroke fill')
                    .attr('stroke-linejoin', 'round')
                    .attr('text-anchor', 'middle')
                    .attr('dy', 23)
                    .text(shortName);

                badge.append('title')
                    .text(`Named Graph: ${gName}`);

                badge
                    .on('mouseenter', () => {
                        badge.select('circle')
                            .transition().duration(150)
                            .attr('r', 13)
                            .attr('stroke-width', 2.5);
                    })
                    .on('mouseleave', () => {
                        badge.select('circle')
                            .transition().duration(150)
                            .attr('r', 11)
                            .attr('stroke-width', 2);
                    });

                graphBadgeElements.push({ graphName: gName, badgeGroup: badge });
            });
        }

        // Layer 2: Links & Multi-edge Bezier Arcs
        const linkGroup = g.append('g').attr('class', 'bk-graph-links');

        const link = linkGroup
            .selectAll('path')
            .data(links)
            .join('path')
            .attr('class', 'bk-graph-link')
            .attr('stroke', '#64748b')
            .attr('stroke-opacity', 0.6)
            .attr('fill', 'none')
            .attr('stroke-width', 1.5)
            .attr('marker-end', 'url(#arrow)');

        // Link labels
        const linkLabelGroup = g.append('g').attr('class', 'bk-graph-link-labels');

        const linkLabel = linkLabelGroup
            .selectAll('text')
            .data(links)
            .join('text')
            .attr('font-size', '9px')
            .attr('fill', '#a78bfa')
            .attr('text-anchor', 'middle')
            .attr('dy', -3)
            .text((d) => d.predicateLabel);

        // Layer 3: Nodes & Interactivity
        const nodeGroup = g.append('g').attr('class', 'bk-graph-nodes');

        function drag(sim: d3.Simulation<GraphNode, GraphLink>) {
            function dragstarted(event: any, d: GraphNode) {
                if (!event.active) {
                    sim.alphaTarget(0.2).restart();
                }
                d.fx = d.x;
                d.fy = d.y;
            }

            function dragged(event: any, d: GraphNode) {
                d.fx = event.x;
                d.fy = event.y;
            }

            function dragended(event: any, d: GraphNode) {
                if (!event.active) {
                    sim.alphaTarget(0);
                }
                d.isPinned = true;
                setPinnedCount(nodes.filter((n) => n.isPinned).length);
                updatePinBadges();
            }

            return d3.drag<SVGGElement, GraphNode>()
                .on('start', dragstarted)
                .on('drag', dragged)
                .on('end', dragended);
        }

        const node = nodeGroup
            .selectAll<SVGGElement, GraphNode>('g')
            .data(nodes, (d) => d.id)
            .join('g')
            .attr('class', 'bk-graph-node')
            .call(drag(simulation) as any)
            .on('click', (event, d) => {
                event.stopPropagation();
                onSelectNode(d);
            })
            .on('mouseenter', (_, d) => {
                const neighborIds = new Set<string>([d.id]);
                const incidentLinkIds = new Set<string>();

                links.forEach((l) => {
                    const sId = typeof l.source === 'object' ? (l.source as GraphNode).id : l.source;
                    const tId = typeof l.target === 'object' ? (l.target as GraphNode).id : l.target;
                    if (sId === d.id || tId === d.id) {
                        neighborIds.add(sId);
                        neighborIds.add(tId);
                        incidentLinkIds.add(l.id);
                    }
                });

                node.style('opacity', (n) => (neighborIds.has(n.id) ? 1 : 0.18));
                link.style('opacity', (l) => (incidentLinkIds.has(l.id) ? 1 : 0.08));
                linkLabel.style('opacity', (l) => (incidentLinkIds.has(l.id) ? 1 : 0.08));
            })
            .on('mouseleave', () => {
                node.style('opacity', 1);
                link.style('opacity', 0.6);
                linkLabel.style('opacity', 1);
            });

        // Node shapes based on termType
        node.each(function (d) {
            const el = d3.select(this);

            if (d.termType === 'Literal') {
                el.append('rect')
                    .attr('x', -24)
                    .attr('y', -12)
                    .attr('width', 48)
                    .attr('height', 24)
                    .attr('rx', 4)
                    .attr('fill', '#10b981')
                    .attr('stroke', '#059669')
                    .attr('stroke-width', 1.5);
            } else if (d.termType === 'BlankNode') {
                el.append('circle')
                    .attr('r', 13)
                    .attr('fill', '#64748b')
                    .attr('stroke', '#475569')
                    .attr('stroke-width', 1.5);
            } else {
                el.append('circle')
                    .attr('r', 15)
                    .attr('fill', '#38bdf8')
                    .attr('stroke', '#0284c7')
                    .attr('stroke-width', 1.5);
            }
        });

        // Search Match Highlight Halo (steady glowing ring)
        node.append('circle')
            .attr('class', 'bk-search-match-ring')
            .attr('r', (d) => (d.termType === 'Literal' ? 26 : 21))
            .attr('fill', 'none')
            .attr('stroke', '#f59e0b')
            .attr('stroke-width', 2.5)
            .style('display', (d) => (activeMatchedNode?.id === d.id ? 'block' : 'none'));

        // Pin Badge on top-right of pinned node
        // Inner rotation group prevents resetting SVG transform attribute on hover
        const pinBadges = node.append('g')
            .attr('class', 'bk-node-pin-badge')
            .attr('transform', (d) => (d.termType === 'Literal' ? 'translate(22, -12)' : 'translate(13, -13)'))
            .style('cursor', 'pointer')
            .style('display', (d) => (d.isPinned ? 'block' : 'none'))
            .on('click', (event, d) => {
                event.stopPropagation();
                d.fx = null;
                d.fy = null;
                d.isPinned = false;
                setPinnedCount(nodes.filter((n) => n.isPinned).length);
                updatePinBadges();
                simulation.alpha(0.2).restart();
            });

        pinBadges.append('circle')
            .attr('r', 8)
            .attr('fill', '#1e293b')
            .attr('stroke', '#f59e0b')
            .attr('stroke-width', 1.5);

        const pinInner = pinBadges.append('g')
            .attr('class', 'bk-pin-icon-inner');

        pinInner.append('text')
            .attr('font-size', '9px')
            .attr('text-anchor', 'middle')
            .attr('dy', 3.5)
            .text('📌');

        function updatePinBadges() {
            node.selectAll<SVGGElement, GraphNode>('.bk-node-pin-badge')
                .style('display', (d) => (d.isPinned ? 'block' : 'none'));
        }

        // Node labels underneath
        node.append('text')
            .attr('font-size', '10px')
            .attr('fill', 'var(--vscode-foreground, #fff)')
            .attr('text-anchor', 'middle')
            .attr('dy', 25)
            .text((d) => d.label);

        // Compute curved path for multi-edges
        function computeLinkPath(d: any) {
            const x1 = d.source.x;
            const y1 = d.source.y;
            const x2 = d.target.x;
            const y2 = d.target.y;

            if (d.linkTotal <= 1) {
                return `M ${x1} ${y1} L ${x2} ${y2}`;
            }

            const dx = x2 - x1;
            const dy = y2 - y1;
            const dist = Math.sqrt(dx * dx + dy * dy) || 1;
            const nx = -dy / dist;
            const ny = dx / dist;

            const offset = (d.linkIndex - (d.linkTotal - 1) / 2) * 26;
            const mx = (x1 + x2) / 2 + nx * offset;
            const my = (y1 + y2) / 2 + ny * offset;

            return `M ${x1} ${y1} Q ${mx} ${my} ${x2} ${y2}`;
        }

        function computeLinkLabelPos(d: any) {
            const x1 = d.source.x;
            const y1 = d.source.y;
            const x2 = d.target.x;
            const y2 = d.target.y;

            if (d.linkTotal <= 1) {
                return { x: (x1 + x2) / 2, y: (y1 + y2) / 2 };
            }

            const dx = x2 - x1;
            const dy = y2 - y1;
            const dist = Math.sqrt(dx * dx + dy * dy) || 1;
            const nx = -dy / dist;
            const ny = dx / dist;

            const offset = (d.linkIndex - (d.linkTotal - 1) / 2) * 26;
            return {
                x: (x1 + x2) / 2 + nx * (offset * 0.7),
                y: (y1 + y2) / 2 + ny * (offset * 0.7)
            };
        }

        // Simulation tick updates
        simulation.on('tick', () => {
            if (hullMode === 'gooey') {
                graphBlobElements.forEach(({ nodeCircles, linkLines }) => {
                    nodeCircles
                        .attr('cx', (d: any) => d.x)
                        .attr('cy', (d: any) => d.y);

                    linkLines.attr('d', computeLinkPath);
                });
            } else if (hullMode !== 'disabled') {
                graphHullElements.forEach(({ graphName: gName, pathEl }) => {
                    const gNodes = getNodesForGraph(gName);
                    const gLinks = links.filter((l) => l.graphName === gName);
                    const obstacleNodes = nodes.filter((n) => !gNodes.some((gn) => gn.id === n.id));

                    let shouldRecompute = true;
                    // Stabilize BubbleSet settling: skip re-marching if nodes moved < 1.2px during cooling
                    if (hullMode === 'bubbleset' && simulation.alpha() < 0.15) {
                        let prevMap = lastBubbleNodePos.get(gName);
                        if (!prevMap) {
                            prevMap = new Map();
                            lastBubbleNodePos.set(gName, prevMap);
                        }
                        let maxDist = 0;
                        for (const gn of gNodes) {
                            const prev = prevMap.get(gn.id);
                            if (prev) {
                                const d = Math.hypot((gn.x ?? 0) - prev.x, (gn.y ?? 0) - prev.y);
                                if (d > maxDist) {
                                    maxDist = d;
                                }
                            } else {
                                maxDist = 10;
                            }
                        }
                        if (maxDist < 1.2) {
                            shouldRecompute = false;
                        } else {
                            for (const gn of gNodes) {
                                prevMap.set(gn.id, { x: gn.x ?? 0, y: gn.y ?? 0 });
                            }
                        }
                    }

                    if (shouldRecompute) {
                        let pathD = '';
                        if (hullMode === 'smoothHull') {
                            pathD = computeSmoothHull(gNodes, gLinks);
                        } else if (hullMode === 'bubbleset') {
                            pathD = computeBubbleSetHull(gNodes, obstacleNodes, gLinks);
                        } else if (hullMode === 'concaveHull') {
                            pathD = computeConcaveHull(gNodes, gLinks);
                        }
                        pathEl.attr('d', pathD);
                    }
                });
            }

            // Smoothly update graph badges position ON the envelope path (or near cluster if gooey)
            graphBadgeElements.forEach(({ graphName: gName, badgeGroup }) => {
                let targetPos: { x: number; y: number } | null = null;

                if (hullMode === 'gooey') {
                    const blobElem = graphBlobElements.find((b) => b.graphName === gName);
                    const clusterNodes = blobElem ? blobElem.gNodes : getNodesForGraph(gName);
                    if (clusterNodes.length > 0) {
                        let bestNode = clusterNodes[0];
                        let minScore = Infinity;
                        for (const n of clusterNodes) {
                            const x = n.x ?? 0;
                            const y = n.y ?? 0;
                            const score = x + y;
                            if (score < minScore) {
                                minScore = score;
                                bestNode = n;
                            }
                        }
                        targetPos = {
                            x: (bestNode.x ?? 0) - 27,
                            y: (bestNode.y ?? 0) - 27
                        };
                    }
                } else {
                    const hullElem = graphHullElements.find((h) => h.graphName === gName);
                    if (hullElem && hullElem.pathEl) {
                        const pathNode = hullElem.pathEl.node() as SVGPathElement | null;
                        if (pathNode) {
                            try {
                                const totalLen = pathNode.getTotalLength();
                                if (totalLen > 0) {
                                    let bestPoint = { x: 0, y: 0 };
                                    let minScore = Infinity;
                                    const currentPos = smoothedBadgePositions.get(gName);
                                    const samples = 64;
                                    for (let i = 0; i < samples; i++) {
                                        const pt = pathNode.getPointAtLength((i / samples) * totalLen);
                                        // Hysteresis: add slight distance penalty from current position
                                        // to prevent jumping between two candidate points along the perimeter
                                        const hysteresis = currentPos
                                            ? Math.hypot(pt.x - currentPos.x, pt.y - currentPos.y) * 0.18
                                            : 0;
                                        const score = (pt.x + pt.y) + hysteresis;
                                        if (score < minScore) {
                                            minScore = score;
                                            bestPoint = { x: pt.x, y: pt.y };
                                        }
                                    }
                                    targetPos = bestPoint;
                                }
                            } catch {
                                // fallback
                            }
                        }
                    }
                }

                if (targetPos) {
                    let currentPos = smoothedBadgePositions.get(gName);
                    if (!currentPos) {
                        currentPos = { x: targetPos.x, y: targetPos.y };
                        smoothedBadgePositions.set(gName, currentPos);
                    } else {
                        const dx = targetPos.x - currentPos.x;
                        const dy = targetPos.y - currentPos.y;
                        const dist = Math.hypot(dx, dy);

                        // Deadband: if distance is tiny (< 0.9px), stay completely still to avoid jitter
                        if (dist > 0.9) {
                            const lerpFactor = 0.22;
                            currentPos.x += dx * lerpFactor;
                            currentPos.y += dy * lerpFactor;
                        }
                    }
                    badgeGroup.attr('transform', `translate(${currentPos.x}, ${currentPos.y})`);
                }
            });

            link.attr('d', computeLinkPath);

            linkLabel
                .attr('x', (d: any) => computeLinkLabelPos(d).x)
                .attr('y', (d: any) => computeLinkLabelPos(d).y);

            node.attr('transform', (d: any) => `translate(${d.x},${d.y})`);
        });

        simulation.alpha(0.8).restart();

        return () => {
            simulation.stop();
        };
    }, [nodes, links, layoutMode, hullMode, graphColorMap, subjectOrder, prefixMap, onSelectNode]);

    // Update highlight ring when active search match changes
    useEffect(() => {
        if (!svgRef.current) {
            return;
        }
        d3.select(svgRef.current)
            .selectAll<SVGCircleElement, GraphNode>('.bk-search-match-ring')
            .style('display', (d) => (activeMatchedNode?.id === d.id ? 'block' : 'none'));
    }, [activeMatchedNode]);

    // Zoom Controls
    const handleZoomIn = () => {
        if (!svgRef.current || !zoomBehaviorRef.current) {
            return;
        }
        d3.select(svgRef.current)
            .transition()
            .duration(300)
            .call(zoomBehaviorRef.current.scaleBy, 1.3);
    };

    const handleZoomOut = () => {
        if (!svgRef.current || !zoomBehaviorRef.current) {
            return;
        }
        d3.select(svgRef.current)
            .transition()
            .duration(300)
            .call(zoomBehaviorRef.current.scaleBy, 1 / 1.3);
    };

    const handleFitView = () => {
        if (!svgRef.current || !containerRef.current || !zoomBehaviorRef.current || nodes.length === 0) {
            return;
        }

        const width = containerRef.current.clientWidth || 800;
        const height = containerRef.current.clientHeight || 600;

        let minX = Infinity;
        let maxX = -Infinity;
        let minY = Infinity;
        let maxY = -Infinity;

        nodes.forEach((n) => {
            const x = n.x ?? 0;
            const y = n.y ?? 0;
            if (x < minX) {
                minX = x;
            }
            if (x > maxX) {
                maxX = x;
            }
            if (y < minY) {
                minY = y;
            }
            if (y > maxY) {
                maxY = y;
            }
        });

        const graphWidth = maxX - minX + 120;
        const graphHeight = maxY - minY + 120;
        const midX = (minX + maxX) / 2;
        const midY = (minY + maxY) / 2;

        const scale = Math.min(width / graphWidth, height / graphHeight, 1.5);

        d3.select(svgRef.current)
            .transition()
            .duration(500)
            .ease(d3.easeCubicOut)
            .call(
                zoomBehaviorRef.current.transform,
                d3.zoomIdentity
                    .translate(width / 2 - midX * scale, height / 2 - midY * scale)
                    .scale(scale)
            );
    };

    // Unpin all nodes
    const handleUnpinAll = () => {
        nodes.forEach((n) => {
            n.fx = null;
            n.fy = null;
            n.isPinned = false;
        });
        setPinnedCount(0);
        if (svgRef.current) {
            d3.select(svgRef.current)
                .selectAll('.bk-node-pin-badge')
                .style('display', 'none');
        }
        if (simulationRef.current) {
            simulationRef.current.alpha(0.3).restart();
        }
    };

    return {
        svgRef,
        containerRef,
        pinnedCount,
        handleZoomIn,
        handleZoomOut,
        handleFitView,
        handleUnpinAll
    };
}
