import {
    AllDataContext,
    CustomDataContext,
    EvaluationDataContext,
    MultiSourceDataContext,
    RangeDataContext,
    RawStringCustomDataContext,
    ReferenceValuesCustomDataContext
} from './generated/types';

export type TypedAllDataContext = AllDataContext & { type: 'all' };
export type TypedRangeDataContext = RangeDataContext & { type: 'range' };
export type TypedRawStringCustomDataContext = RawStringCustomDataContext & { type: 'rawString' };
export type TypedReferenceValuesCustomDataContext = ReferenceValuesCustomDataContext & { type: 'referenceValues' };
export type TypedMultiSourceDataContext = MultiSourceDataContext & {
    type: 'multiSource';
    sources: { [index: string]: TypedEvaluationDataContext };
    defaultContext?: TypedEvaluationDataContext;
};

export type TypedEvaluationDataContext =
    | TypedAllDataContext
    | TypedRangeDataContext
    | TypedRawStringCustomDataContext
    | TypedReferenceValuesCustomDataContext
    | TypedMultiSourceDataContext;

export type TypedDataContext = TypedEvaluationDataContext;

export type EvaluationDataContextType = TypedEvaluationDataContext['type'];

export function isAllDataContext(ctx: unknown): ctx is TypedAllDataContext {
    return typeof ctx === 'object' && ctx !== null && (ctx as any).type === 'all';
}

export function isRangeDataContext(ctx: unknown): ctx is TypedRangeDataContext {
    return typeof ctx === 'object' && ctx !== null && (ctx as any).type === 'range';
}

export function isRawStringCustomDataContext(ctx: unknown): ctx is TypedRawStringCustomDataContext {
    return typeof ctx === 'object' && ctx !== null && (ctx as any).type === 'rawString';
}

export function isReferenceValuesCustomDataContext(ctx: unknown): ctx is TypedReferenceValuesCustomDataContext {
    return typeof ctx === 'object' && ctx !== null && (ctx as any).type === 'referenceValues';
}

export function isMultiSourceDataContext(ctx: unknown): ctx is TypedMultiSourceDataContext {
    return typeof ctx === 'object' && ctx !== null && (ctx as any).type === 'multiSource';
}
