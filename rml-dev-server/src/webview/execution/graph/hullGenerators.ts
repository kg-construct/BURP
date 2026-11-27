import * as d3 from 'd3';
import { Delaunay } from 'd3-delaunay';
import { BubbleSets, Circle, Line, Rectangle } from 'bubblesets-js';
import { GraphNode, GraphLink } from './graphModel';

export type GraphHullMethod = 'smoothHull' | 'bubbleset' | 'concaveHull' | 'gooey' | 'disabled';

export const HULL_METHOD_LABELS: Record<GraphHullMethod, string> = {
    smoothHull: 'Smooth Hull',
    bubbleset: 'BubbleSet',
    concaveHull: 'Concave Hull',
    gooey: 'Gooey Blobs',
    disabled: 'Disabled'
};

/**
 * Generates an SVG circle path for single-node cases
 */
function generateCirclePath(cx: number, cy: number, r: number): string {
    return `M ${cx - r},${cy} a ${r},${r} 0 1,0 ${r * 2},0 a ${r},${r} 0 1,0 -${r * 2},0`;
}

/**
 * 1. Padded Smooth Convex Hull (Catmull-Rom lozenge envelope)
 */
export function computeSmoothHull(memberNodes: GraphNode[], memberLinks: GraphLink[]): string {
    if (memberNodes.length === 0) {
        return '';
    }

    if (memberNodes.length === 1) {
        const n = memberNodes[0];
        const r = n.termType === 'Literal' ? 38 : 32;
        return generateCirclePath(n.x ?? 0, n.y ?? 0, r);
    }

    const points: [number, number][] = [];
    const NUM_RADIAL_PTS = 8;

    // Add perimeter points around each node
    for (const n of memberNodes) {
        const nx = n.x ?? 0;
        const ny = n.y ?? 0;
        const radius = n.termType === 'Literal' ? 36 : 30;

        for (let i = 0; i < NUM_RADIAL_PTS; i++) {
            const angle = (i / NUM_RADIAL_PTS) * 2 * Math.PI;
            points.push([
                nx + Math.cos(angle) * radius,
                ny + Math.sin(angle) * radius
            ]);
        }
    }

    // Add midpoint padding for links between member nodes
    for (const l of memberLinks) {
        const s = typeof l.source === 'object' ? l.source : null;
        const t = typeof l.target === 'object' ? l.target : null;
        if (s && t && typeof s.x === 'number' && typeof s.y === 'number' && typeof t.x === 'number' && typeof t.y === 'number') {
            const mx = (s.x + t.x) / 2;
            const my = (s.y + t.y) / 2;
            const dx = t.x - s.x;
            const dy = t.y - s.y;
            const dist = Math.sqrt(dx * dx + dy * dy) || 1;
            const nx = -dy / dist;
            const ny = dx / dist;
            const offset = 22;
            points.push([mx + nx * offset, my + ny * offset]);
            points.push([mx - nx * offset, my - ny * offset]);
        }
    }

    const hull = d3.polygonHull(points);
    if (!hull || hull.length < 3) {
        const n = memberNodes[0];
        return generateCirclePath(n.x ?? 0, n.y ?? 0, 36);
    }

    const lineGen = d3.line<[number, number]>()
        .x(d => d[0])
        .y(d => d[1])
        .curve(d3.curveCatmullRomClosed.alpha(0.5));

    return lineGen(hull) || '';
}

/**
 * 2. BubbleSet (ACM CHI 2009 algorithm) that actively routes around obstacle nodes
 */
export function computeBubbleSetHull(
    memberNodes: GraphNode[],
    obstacleNodes: GraphNode[],
    memberLinks: GraphLink[]
): string {
    if (memberNodes.length === 0) {
        return '';
    }

    try {
        const members = memberNodes.map((n) => {
            const r = n.termType === 'Literal' ? 26 : 20;
            return new Circle(n.x ?? 0, n.y ?? 0, r);
        });

        const nonMembers = obstacleNodes.map((n) => {
            const r = n.termType === 'Literal' ? 28 : 22;
            return new Rectangle((n.x ?? 0) - r, (n.y ?? 0) - r, r * 2, r * 2);
        });

        const lines = memberLinks
            .map((l) => {
                const s = typeof l.source === 'object' ? l.source : null;
                const t = typeof l.target === 'object' ? l.target : null;
                if (s && t && typeof s.x === 'number' && typeof s.y === 'number' && typeof t.x === 'number' && typeof t.y === 'number') {
                    return new Line(s.x, s.y, t.x, t.y);
                }
                return null;
            })
            .filter((l): l is Line => l !== null);

        const bb = new BubbleSets({
            maxMarchingIterations: 30,
            pixelGroup: 3,
            nodeR0: 20,
            nodeR1: 45,
            edgeR0: 12,
            edgeR1: 28
        });
        bb.pushMember(...members);
        bb.pushNonMember(...nonMembers);
        bb.pushEdge(...lines);
        const outline = bb.compute();

        const pathStr = outline.simplify(3).bSplines(8).toString();
        if (pathStr && pathStr.length > 5) {
            return pathStr;
        }
    } catch {
        // Fallback to smooth hull on computation errors
    }

    return computeSmoothHull(memberNodes, memberLinks);
}

/**
 * 3. Concave Hull (Alpha Shape) that indents along non-convex crevices
 */
export function computeConcaveHull(
    memberNodes: GraphNode[],
    memberLinks: GraphLink[],
    alphaThreshold = 135
): string {
    if (memberNodes.length < 3) {
        return computeSmoothHull(memberNodes, memberLinks);
    }

    try {
        const rawCoords: [number, number][] = memberNodes.map((n) => [n.x ?? 0, n.y ?? 0]);
        const delaunay = Delaunay.from(rawCoords);
        const { triangles, points } = delaunay;

        // An edge map counting how many alpha-valid triangles share each edge
        const edgeMap = new Map<string, { p1: number; p2: number; count: number }>();

        function addEdge(i: number, j: number) {
            const p1 = Math.min(i, j);
            const p2 = Math.max(i, j);
            const key = `${p1}_${p2}`;
            const existing = edgeMap.get(key);
            if (existing) {
                existing.count++;
            } else {
                edgeMap.set(key, { p1, p2, count: 1 });
            }
        }

        const numTriangles = triangles.length / 3;
        for (let t = 0; t < numTriangles; t++) {
            const i0 = triangles[t * 3];
            const i1 = triangles[t * 3 + 1];
            const i2 = triangles[t * 3 + 2];

            const ax = points[i0 * 2];
            const ay = points[i0 * 2 + 1];
            const bx = points[i1 * 2];
            const by = points[i1 * 2 + 1];
            const cx = points[i2 * 2];
            const cy = points[i2 * 2 + 1];

            // Circumradius calculation
            const d = 2 * (ax * (by - cy) + bx * (cy - ay) + cx * (ay - by));
            if (Math.abs(d) < 1e-6) {
                continue;
            }

            const a2 = ax * ax + ay * ay;
            const b2 = bx * bx + by * by;
            const c2 = cx * cx + cy * cy;
            const ux = (a2 * (by - cy) + b2 * (cy - ay) + c2 * (ay - by)) / d;
            const uy = (a2 * (cx - bx) + b2 * (ax - cx) + c2 * (bx - ax)) / d;
            const radius = Math.sqrt((ax - ux) ** 2 + (ay - uy) ** 2);

            if (radius <= alphaThreshold) {
                addEdge(i0, i1);
                addEdge(i1, i2);
                addEdge(i2, i0);
            }
        }

        // Boundary edges appear in exactly 1 valid triangle
        const boundaryEdges: [number, number][] = [];
        for (const e of edgeMap.values()) {
            if (e.count === 1) {
                boundaryEdges.push([e.p1, e.p2]);
            }
        }

        if (boundaryEdges.length >= 3) {
            // Reconstruct ordered perimeter cycle from boundary edges
            const adj = new Map<number, number[]>();
            for (const [p1, p2] of boundaryEdges) {
                if (!adj.has(p1)) {
                    adj.set(p1, []);
                }
                if (!adj.has(p2)) {
                    adj.set(p2, []);
                }
                adj.get(p1)!.push(p2);
                adj.get(p2)!.push(p1);
            }

            const cycle: [number, number][] = [];
            const visited = new Set<number>();
            let curr: number | undefined = boundaryEdges[0][0];

            while (curr !== undefined && !visited.has(curr)) {
                visited.add(curr);
                cycle.push([points[curr * 2], points[curr * 2 + 1]]);
                const neighbors: number[] = adj.get(curr) || [];
                const next: number | undefined = neighbors.find((nbr: number) => !visited.has(nbr));
                if (next !== undefined) {
                    curr = next;
                } else {
                    break;
                }
            }

            if (cycle.length >= 3) {
                // Compute centroid of cycle to push boundary points slightly outward
                let cx = 0;
                let cy = 0;
                for (const [px, py] of cycle) {
                    cx += px;
                    cy += py;
                }
                cx /= cycle.length;
                cy /= cycle.length;

                const paddedCycle: [number, number][] = cycle.map(([px, py]) => {
                    const dx = px - cx;
                    const dy = py - cy;
                    const dist = Math.sqrt(dx * dx + dy * dy) || 1;
                    const pad = 28;
                    return [px + (dx / dist) * pad, py + (dy / dist) * pad];
                });

                const lineGen = d3.line<[number, number]>()
                    .x((d) => d[0])
                    .y((d) => d[1])
                    .curve(d3.curveCatmullRomClosed.alpha(0.5));

                const result = lineGen(paddedCycle);
                if (result) {
                    return result;
                }
            }
        }
    } catch {
        // Fallback to smooth hull on geometry errors
    }

    return computeSmoothHull(memberNodes, memberLinks);
}

/**
 * Computes anchor coordinates for the Graph Identifier Node at top-left of cluster
 */
export function computeGraphBadgePosition(memberNodes: GraphNode[]): { x: number; y: number } {
    let minX = Infinity;
    let minY = Infinity;

    for (const n of memberNodes) {
        const x = n.x ?? 0;
        const y = n.y ?? 0;
        if (x < minX) {
            minX = x;
        }
        if (y < minY) {
            minY = y;
        }
    }

    return {
        x: isFinite(minX) ? minX - 10 : 0,
        y: isFinite(minY) ? minY - 26 : 0
    };
}
