import * as React from "react";
import { createRoot } from "react-dom/client";
import { DiagramEditor } from "@openworkflowspec/diagram-editor";
import "@openworkflowspec/diagram-editor/styles.css";

const roots = new WeakMap();

export function renderOpenWorkflowSpecDiagramEditor(container, props) {
  let root = roots.get(container);

  if (!root) {
    root = createRoot(container);
    roots.set(container, root);
  }

    root.render(React.createElement(DiagramEditor, {key: props.workflowKey, ...props}));

  return root;
}

export function unmountOpenWorkflowSpecDiagramEditor(container) {
  const root = roots.get(container);

  if (root) {
    root.unmount();
    roots.delete(container);
  }
}
