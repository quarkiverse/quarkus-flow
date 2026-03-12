import babel from '@babel/core';
import type { PluginOptions } from './utils';
/** @deprecated Use `jotai-babel/preset` instead. */
export default function jotaiPreset(_: typeof babel, options?: PluginOptions): {
    plugins: babel.PluginItem[];
};
