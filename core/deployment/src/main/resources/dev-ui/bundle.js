import * as React from "react";
import { createRoot } from "react-dom/client";
import { TextEditor } from "@openworkflowspec/text-editor";
import "@openworkflowspec/text-editor/styles.css";

const roots = new WeakMap();

export function renderOpenWorkflowSpecTextEditor(container, props) {
  let root = roots.get(container);

  if (!root) {
    root = createRoot(container);
    roots.set(container, root);
  }

  root.render(React.createElement(TextEditor, { key: props.workflowKey, ...props }));

  return root;
}

export function unmountOpenWorkflowSpecTextEditor(container) {
  const root = roots.get(container);

  if (root) {
    root.unmount();
    roots.delete(container);
  }
}
