import type { Atom } from 'jotai/vanilla';
export type Loadable<Value> = {
    state: 'loading';
} | {
    state: 'hasError';
    error: unknown;
} | {
    state: 'hasData';
    data: Awaited<Value>;
};
/**
 * @deprecated `loadable` is deprecated infavor of `unwrap`.
 *
 * Userland implementation of loadable:
 * ```js
 * function loadable(anAtom) {
 *   const LOADING = { state: 'loading' }
 *   const unwrappedAtom = unwrap(anAtom, () => LOADING)
 *   return atom((get) => {
 *     try {
 *       const data = get(unwrappedAtom)
 *       if (data === LOADING) {
 *         return LOADING
 *       }
 *       return { state: 'hasData', data }
 *     } catch (error) {
 *       return { state: 'hasError', error }
 *     }
 *   })
 * }
 * ```
 */
export declare function loadable<Value>(anAtom: Atom<Value>): Atom<Loadable<Value>>;
