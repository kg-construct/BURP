declare function acquireVsCodeApi<TPost = any, TState = any>(): {
    postMessage: (msg: TPost) => void;
    getState: () => TState | undefined;
    setState: (state: TState) => void;
};

export interface TypedVsCodeApi<TPost = any, TState = any> {
    postMessage: (msg: TPost) => void;
    getState: () => TState | undefined;
    setState: (state: TState) => void;
}

let cachedVsCodeApi: any = null;

export function getVsCodeApi<TPost = any, TState = any>(): TypedVsCodeApi<TPost, TState> {
    if (!cachedVsCodeApi) {
        if (typeof acquireVsCodeApi === 'function') {
            cachedVsCodeApi = acquireVsCodeApi<TPost, TState>();
        } else {
            cachedVsCodeApi = {
                postMessage: (msg: any) => console.log('VSCode msg:', msg),
                getState: () => undefined,
                setState: () => {}
            };
        }
    }
    return cachedVsCodeApi as TypedVsCodeApi<TPost, TState>;
}

export const vscodeApi = getVsCodeApi<any, any>();
