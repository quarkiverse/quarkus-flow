import * as React from "react";
import { createRoot } from "react-dom/client";
import { DiagramEditor } from "@serverlessworkflow/diagram-editor";

const rootElement = document.getElementById("root");

if (!rootElement) {
  throw new Error("Missing #root element for Serverless Workflow Diagram Editor");
}

createRoot(rootElement).render(
  React.createElement(DiagramEditor, {
    content: "{}",
    isReadOnly: false,
    locale: "en",
    colorMode: "system",
  })
);
