export const DEFAULT_PREFIXES: Record<string, string> = {
    'xsd': 'http://www.w3.org/2001/XMLSchema#',
    'rdf': 'http://www.w3.org/1999/02/22-rdf-syntax-ns#',
    'rdfs': 'http://www.w3.org/2000/01/rdf-schema#',
    'rml': 'http://w3id.org/rml/'
};

const TURTLE_PREFIX_REGEX = /^(?:[A-Za-z][A-Za-z0-9_-]*(?:\.[A-Za-z0-9_-]+)*)?$/;
const TURTLE_LOCAL_NAME_REGEX = /^[_a-zA-Z0-9](?:\.?[-_a-zA-Z0-9])*$/;

export function shortenIri(
    iri: string,
    customPrefixes?: Record<string, string>
): { prefix?: string; localName?: string; display: string; isCurie: boolean; fullIri: string } {
    if (!iri) {
        return { display: '', isCurie: false, fullIri: '' };
    }

    const nsToPrefix = new Map<string, string>();

    // Defaults
    for (const [p, ns] of Object.entries(DEFAULT_PREFIXES)) {
        nsToPrefix.set(ns, p);
    }
    nsToPrefix.set('http://www.w3.org/ns/rml/', 'rml');

    // Merge custom prefixes
    if (customPrefixes) {
        for (const [k, v] of Object.entries(customPrefixes)) {
            if (typeof v === 'string' && (v.startsWith('http://') || v.startsWith('https://') || v.startsWith('urn:'))) {
                const cleanP = k.endsWith(':') ? k.slice(0, -1) : k;
                nsToPrefix.set(v, cleanP);
            } else if (typeof k === 'string' && (k.startsWith('http://') || k.startsWith('https://') || k.startsWith('urn:'))) {
                const cleanP = (typeof v === 'string' && v.endsWith(':')) ? v.slice(0, -1) : (v || '');
                nsToPrefix.set(k, cleanP);
            }
        }
    }

    // Sort by namespace length descending (longest prefix match first)
    const sortedEntries = Array.from(nsToPrefix.entries()).sort(
        (a, b) => b[0].length - a[0].length
    );

    for (const [namespace, prefix] of sortedEntries) {
        if (iri.startsWith(namespace)) {
            const localName = iri.substring(namespace.length);
            // Must be a valid Turtle prefix and local name (starts with [_a-zA-Z0-9], no trailing dot)
            if (TURTLE_PREFIX_REGEX.test(prefix) && TURTLE_LOCAL_NAME_REGEX.test(localName)) {
                return {
                    prefix,
                    localName,
                    display: prefix ? `${prefix}:${localName}` : `:${localName}`,
                    isCurie: true,
                    fullIri: iri
                };
            }
        }
    }

    return {
        display: `<${iri}>`,
        isCurie: false,
        fullIri: iri
    };
}

export function formatTargetResource(res: any, prefixMap?: Record<string, string>): string | null {
    if (!res) {
        return null;
    }
    if (typeof res === 'string') {
        return shortenIri(res, prefixMap).display;
    }
    if (res.uri) {
        return shortenIri(res.uri, prefixMap).display;
    }
    if (res.id) {
        return res.id;
    }
    return null;
}
