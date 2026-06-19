import { LitElement, html, css } from 'lit';
import {
  renderServerlessWorkflowDiagramEditor,
  unmountServerlessWorkflowDiagramEditor,
} from '../bundle.js';
import { JsonRpc } from 'jsonrpc';

const diagramEditorStylesUrl = new URL("../bundle.css", import.meta.url).href;

const workflowExample = `document:
  dsl: '1.0.3'
  namespace: examples
  name: accumulate-room-readings
  version: '0.1.0'
  title: "Test Workflow Title"
  summary: "A test workflow with metadata"
  tags:
    iot: Internet of Things
    sensors: Sensor data
    readings: Room readings
do:
  - consumeReading:
      listen:
        to:
          all:
            - with:
                source: https://my.home.com/sensor
                type: my.home.sensors.temperature
              correlate:
                roomId:
                  from: .roomid
            - with:
                source: https://my.home.com/sensor
                type: my.home.sensors.humidity
              correlate:
                roomId:
                  from: .roomid
      output:
        as: .data.reading
  - logReading:
      for:
        each: reading
        in: .readings
      do:
        - callOrderService:
            call: openapi
            with:
              document:
                endpoint: http://myorg.io/ordersservices.json
              operationId: logreading
  - generateReport:
      call: openapi
      with:
        document:
          endpoint: http://myorg.io/ordersservices.json
        operationId: produceReport
  - emitEvent:
      emit:
        event:
          with:
            source: https://petstore.com
            type: com.petstore.order.placed.v1
            data:
              client:
                firstName: Cruella
                lastName: de Vil
              items:
                - breed: dalmatian
                  quantity: 101
timeout:
  after:
    hours: 1`;



export class ServerlessWorkflowDiagramEditorElement extends LitElement {
  static properties = {
    workflow: { type: Object },
    source: { type: String },
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
    this.source = null;
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

  firstUpdated() {
    this._renderReactEditor();
  }

  updated(changedProperties) {
    if (changedProperties.has('workflow')) {
      this._loadWorkflowDefinition();
    }

    if (
      changedProperties.has('workflow') ||
      changedProperties.has('source') ||
      changedProperties.has('readonly') ||
      changedProperties.has('_workflowDefinition')
    ) {
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
      this._workflowDefinition = JSON.stringify(result, null, 2);
      this._workflowDefinition = workflowExample;
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
      content: this._workflowDefinition || '',
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
