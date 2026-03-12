import babel from '@babel/core';
import type { PluginObj } from '@babel/core';
import type { PluginOptions } from './utils.mjs';
/** @deprecated Use `jotai-babel/plugin-react-refresh` instead. */
export default function reactRefreshPlugin({ types: t }: typeof babel, options?: PluginOptions): PluginObj;
