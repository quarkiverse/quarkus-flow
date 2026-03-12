import babel from '@babel/core';
import type { PluginObj } from '@babel/core';
import type { PluginOptions } from './utils.mjs';
/** @deprecated Use `jotai-babel/plugin-debug-label` instead. */
export default function debugLabelPlugin({ types: t }: typeof babel, options?: PluginOptions): PluginObj;
