import type { Atom, Getter, WritableAtom } from 'jotai/vanilla';
type AnyError = unknown;
type Subscription = {
    unsubscribe: () => void;
};
type Observer<T> = {
    next: (value: T) => void;
    error: (error: AnyError) => void;
    complete: () => void;
};
type SubscribableObservable<T> = {
    subscribe(observer: Observer<T>): Subscription;
} | {
    subscribe(observer: Partial<Observer<T>>): Subscription;
} | {
    subscribe(observer: Partial<Observer<T>>): Subscription;
    subscribe(next: (value: T) => void): Subscription;
};
type SymbolObservable<T> = {
    [Symbol.observable]: () => SubscribableObservable<T>;
};
type ObservableLike<T> = SubscribableObservable<T> | SymbolObservable<T>;
type SubjectLike<T> = ObservableLike<T> & Observer<T>;
type Options<Data> = {
    initialValue?: Data | (() => Data);
    unstable_timeout?: number;
};
type OptionsWithInitialValue<Data> = {
    initialValue: Data | (() => Data);
    unstable_timeout?: number;
};
export declare function atomWithObservable<Data>(getObservable: (get: Getter) => SubjectLike<Data>, options: OptionsWithInitialValue<Data>): WritableAtom<Data, [Data], void>;
export declare function atomWithObservable<Data>(getObservable: (get: Getter) => SubjectLike<Data>, options?: Options<Data>): WritableAtom<Data | Promise<Data>, [Data], void>;
export declare function atomWithObservable<Data>(getObservable: (get: Getter) => ObservableLike<Data>, options: OptionsWithInitialValue<Data>): Atom<Data>;
export declare function atomWithObservable<Data>(getObservable: (get: Getter) => ObservableLike<Data>, options?: Options<Data>): Atom<Data | Promise<Data>>;
export {};
