import * as React from "react";
import { createRoot } from "react-dom/client";
import { DiagramEditor } from "@serverlessworkflow/diagram-editor";
import "@serverlessworkflow/diagram-editor/styles.css";

const roots = new WeakMap();

export function renderServerlessWorkflowDiagramEditor(container, props) {
  let root = roots.get(container);

  if (!root) {
    root = createRoot(container);
    roots.set(container, root);
  }

    root.render(React.createElement(DiagramEditor, {key: props.workflowKey, ...props}));

  return root;
}

export function unmountServerlessWorkflowDiagramEditor(container) {
  const root = roots.get(container);

  if (root) {
    root.unmount();
    roots.delete(container);
  }
}
