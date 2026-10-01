import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/grid';
import '@vaadin/button';
import '@vaadin/icon';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import './components/openworkflowspec-diagram-editor.js';
import './components/qwc-flow-workflow-header.js';
import './qwc-flow-workflow-execution.js';

export class QwcFlow extends QwcHotReloadElement {
    jsonRpc = new JsonRpc(this);

    static styles = css`
       :host {
           display: block;
           height: 100%;
           min-height: 0;
       }

       .workflows {
           padding: 8px;
       }

       .diagram-view {
           display: flex;
           flex-direction: column;
           height: 100%;
           min-height: 0;
           padding: 0px 8px;
       }

       .diagram-view-header {
           flex-shrink: 0;
       }

       /* min-height matches openworkflowspec-diagram-editor.js so the
          diagram never collapses if an ancestor has no definite height. */
       .diagram-view-body {
           flex: 1 1 auto;
           min-height: 520px;
           position: relative;
           overflow: hidden;
       }

       .diagram-editor {
           height: 100%;
       }
   `;

    static properties = {
        _workflows: { state: true },
        _view: { state: true }
    };

    constructor() {
        super();
        this._workflows = [];
        this._view = { mode: 'list', workflow: null };
    }

    render() {
       switch(this._view.mode){
        case 'diagram':
            return this._renderDiagramView(this._view.workflow);
        case 'execute':
            return this._renderExecuteView(this._view.workflow)
        default:
            return this._renderWorkflowList();
       }
    }

    hotReload() {
        // no-op, it is necessary due to QwcHotReloadElement
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc.getWorkflows().then(({ result }) => {
            this._workflows = result;
        });
    }

    _renderWorkflowList() {
       return html`
           <div class="workflows">
               <vaadin-grid
                       .items=${this._workflows}
                       theme="no-border"
                       column-reordering-allowed
                       multi-sort>
                   <!-- Name is now id.name from WorkflowDefinitionId -->
                   <vaadin-grid-column
                           path="id.name"
                           header="Name"
                           auto-width>
                   </vaadin-grid-column>
                   <!-- Optional: show namespace -->
                   <vaadin-grid-column
                           path="id.namespace"
                           header="Namespace"
                           auto-width>
                   </vaadin-grid-column>
                   <!-- Optional: show version -->
                   <vaadin-grid-column
                           path="id.version"
                           header="Version"
                           auto-width>
                   </vaadin-grid-column>
                   <!-- Description from WorkflowInfo -->
                   <vaadin-grid-column
                           path="description"
                           header="Description">
                   </vaadin-grid-column>
                   <vaadin-grid-column
                           header="Actions"
                           auto-width
                           ${columnBodyRenderer(workflow => html`
                               <vaadin-button @click=${() => this._viewDiagram(workflow)}
                                              id="see-${this._generateDiagramEditorId(workflow.id)}"
                                              title="View diagram">
                                   <vaadin-icon icon="font-awesome-solid:eye"></vaadin-icon>
                               </vaadin-button>
                               <vaadin-button id="play-${this._generateDiagramEditorId(workflow.id)}"
                                              @click=${() => this._executeWorkflow(workflow)}
                                              title="Execute workflow">
                                   <vaadin-icon icon="font-awesome-solid:play"></vaadin-icon>
                               </vaadin-button>
                           `, [])}>
                   </vaadin-grid-column>
               </vaadin-grid>
           </div>
       `;
   }

   _renderDiagramView(workflow) {
       return html`
           <div class="diagram-view">
               <qwc-flow-workflow-header
                       class="diagram-view-header"
                       .workflowId=${workflow.id}
                       description="${workflow.description ?? ''}"
                       @flow-header-back=${this._showWorkflows}>
               </qwc-flow-workflow-header>
               <div class="diagram-view-body">
                   <qwc-openworkflowspec-diagram-editor
                           class="diagram-editor"
                           .workflow=${workflow}
                           .readonly=${true}
                           .workflowKey="show-${this._generateDiagramEditorId(workflow.id)}">
                   </qwc-openworkflowspec-diagram-editor>
               </div>
           </div>
       `;
   }

   _renderExecuteView(workflow) {
       return html`
           <qwc-flow-workflow-execution
                   extensionName="${this.jsonRpc.getExtensionName()}"
                   .workflowId=${workflow.id}
                   description="${workflow.description ?? ''}"
                   @flow-workflows-back=${this._showWorkflows}>
           </qwc-flow-workflow-execution>
       `;
   }

    _generateDiagramEditorId(workflowId) {
        const { namespace, name, version } = workflowId;
        return 'diagramEditor-' + `${namespace.replaceAll('.', '-')}-${name.replaceAll('.', '-')}-${version.replaceAll('.', '-')}`;
    }

    _viewDiagram(workflow) {
        this._view = { mode: 'diagram', workflow }
    }

    _executeWorkflow(workflow) {
        this._view = { mode: 'execute', workflow }
    }

    _showWorkflows() {
        this._view = { mode: 'list', workflow: null }
    }
}

customElements.define('qwc-flow-workflows', QwcFlow);
