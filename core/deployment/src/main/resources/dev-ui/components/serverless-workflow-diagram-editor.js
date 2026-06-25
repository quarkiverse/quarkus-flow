import { LitElement, html, css } from 'lit';
import {
  renderServerlessWorkflowDiagramEditor,
  unmountServerlessWorkflowDiagramEditor,
} from '../bundle.js';
import { JsonRpc } from 'jsonrpc';

const diagramEditorStylesUrl = new URL("../bundle.css", import.meta.url).href;

export class ServerlessWorkflowDiagramEditorElement extends LitElement {
  static properties = {
    workflow: { type: Object },
    readonly: { type: Boolean },
    extensionName: { type: String },
    _workflowDefinition: { state: true },
  };

  static styles = css`
    :host {
      display: block;
      width: 100%;
      height: 100%;
      min-height: 520px;
    }

    #container {
      width: 100%;
      height: 100%;
      min-height: 520px;
    }
  `;

  constructor() {
    super();
    this.workflow = null;
    this.readonly = true;
    this.extensionName = 'quarkus-flow';
    this._workflowDefinition = null;
  }

  connectedCallback() {
    super.connectedCallback();
    this.jsonRpc = new JsonRpc(this.extensionName);
  }

    render() {
        return html`
            <link rel="stylesheet" href=${diagramEditorStylesUrl} />
            <div id="container" class="serverless-workflow-diagram-editor-container"></div>
        `;
    }

  async updated(changedProperties) {
    if (changedProperties.has('workflow')) {
      await this._loadWorkflowDefinition();
    //} else if (changedProperties.has('_workflowDefinition')) {
      this._renderReactEditor();
    }
  }

  async _loadWorkflowDefinition() {
    if (!this.workflow?.id) {
      this._workflowDefinition = null;
      return;
    }

    try {
      const { result } = await this.jsonRpc.getWorkflowDefinition({ id: this.workflow.id });
      //throw new Error("Test error");
      this._workflowDefinition = result;
    } catch (error) {
      console.error('Failed to load workflow definition:', error);
      this._workflowDefinition = null;
    }
  }

  disconnectedCallback() {
    this._unmountReactEditor();
    super.disconnectedCallback();
  }

  _renderReactEditor() {
    const container = this.renderRoot.querySelector('#container');

    if (!container) {
      return;
    }

    renderServerlessWorkflowDiagramEditor(container, {
      content: this._workflowDefinition,
      isReadOnly: this.readonly,
    });
  }

  _unmountReactEditor() {
    const container = this.renderRoot?.querySelector('#container');

    if (container) {
      unmountServerlessWorkflowDiagramEditor(container);
    }
  }
}

customElements.define(
  'qwc-serverless-workflow-diagram-editor',
  ServerlessWorkflowDiagramEditorElement
);
