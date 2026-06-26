import { LitElement, html, css } from 'lit';
import '@vaadin/progress-bar';
import {
  renderServerlessWorkflowDiagramEditor,
  unmountServerlessWorkflowDiagramEditor,
} from '../bundle.js';
import { JsonRpc } from 'jsonrpc';

const diagramEditorStylesUrl = new URL('../bundle.css', import.meta.url).href;

export class ServerlessWorkflowDiagramEditorElement extends LitElement {
  _workflowDefinition = null;

  static properties = {
    workflow: { type: Object },
    readonly: { type: Boolean },
    extensionName: { type: String },
    workflowKey: { state: true },
    _isLoading: { state: true },
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

    .loading-workflow-definition {
      max-width: 80%;
      margin: auto;
      padding-top: 80px;
      text-align: center;
    }
  `;

  constructor() {
    super();
    this.workflow = null;
    this.readonly = true;
    this.extensionName = 'quarkus-flow';
    this.workflowKey = null;
    this._isLoading = false;
  }

  connectedCallback() {
    super.connectedCallback();
    this.jsonRpc = new JsonRpc(this.extensionName);
  }

  render() {
    return html`
      <link rel="stylesheet" href=${diagramEditorStylesUrl} />

      ${this._isLoading
        ? html`
            <div class="loading-workflow-definition">
              <vaadin-progress-bar indeterminate></vaadin-progress-bar>
              <p>Loading workflow definition…</p>
            </div>
          `
        : html`
            <div id="container" class="serverless-workflow-diagram-editor-container"></div>
          `}
    `;
  }

  async updated(changedProperties) {
    if (changedProperties.has('workflow')) {
      this._unmountReactEditor();

      this._isLoading = true;
      await this.updateComplete;

      try {
        await this._loadWorkflowDefinition();
      } finally {
        this._isLoading = false;
        await this.updateComplete;
        this._renderReactEditor();
      }
    }
  }

  async _loadWorkflowDefinition() {
    if (!this.workflow?.id) {
      this._workflowDefinition = null;
      return;
    }

    try {
      const { result } = await this.jsonRpc.getWorkflowDefinition({ id: this.workflow.id });
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
      workflowKey: this.workflowKey,
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
