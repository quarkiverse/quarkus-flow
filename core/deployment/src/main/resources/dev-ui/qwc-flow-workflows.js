import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { observeState } from 'lit-element-state';
import { JsonRpc } from 'jsonrpc';
import '@vaadin/grid';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/dialog';
import { dialogRenderer } from '@vaadin/dialog/lit.js';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
import './components/serverless-workflow-diagram-editor.js';
import './qwc-flow-workflow-execution.js';
import { themeState } from 'theme-state';

export class QwcFlow extends observeState(QwcHotReloadElement) {
    jsonRpc = new JsonRpc(this);

    static styles = css`
        .workflows {
            padding: 8px;
        }
    `;

    static properties = {
        _workflows: { state: true },
        _currentDiagramEditorWorkflow: { state: true },
        _diagramEditorDialogOpened: { state: true },
        _selectedWorkflow: { state: true }
    };

    constructor() {
        super();
        this._workflows = [];
        this._currentDiagramEditorWorkflow = null;
        this._diagramEditorDialogOpened = false;
        this._selectedWorkflow = null;
    }

    render() {
        if (this._selectedWorkflow != null) {
            // Pass WorkflowDefinitionId as a *property*, not as a string attribute
            return html`
                <qwc-flow-workflow-execution
                        extensionName="${this.jsonRpc.getExtensionName()}"
                        .workflowId=${this._selectedWorkflow.id}
                        description="${this._selectedWorkflow.description ?? ''}"
                        @flow-workflows-back=${this._showWorkflows}>
                </qwc-flow-workflow-execution>
            `;
        } else {
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
                                    <vaadin-button @click=${() => this._visualizeDiagramEditor(workflow)}
                                                   id="see-${this._generateDiagramEditorId(workflow.id)}">
                                        <vaadin-icon icon="font-awesome-solid:eye"></vaadin-icon>
                                    </vaadin-button>
                                    <vaadin-button id="play-${this._generateDiagramEditorId(workflow.id)}"  @click=${() => this._executeWorkflow(workflow)}>
                                        <vaadin-icon icon="font-awesome-solid:play"></vaadin-icon>
                                    </vaadin-button>
                                `, [])}>
                        </vaadin-grid-column>
                    </vaadin-grid>
                    ${this._diagramEditorDialog()}
                </div>
            `;
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

    _generateDiagramEditorId(workflowId) {
        const { namespace, name, version } = workflowId;
        return 'diagramEditor-' + `${namespace.replaceAll('.', '-')}-${name.replaceAll('.', '-')}-${version.replaceAll('.', '-')}`;
    }

    _visualizeDiagramEditor(workflow) {
        this._diagramEditorDialogOpened = true;
        this._currentDiagramEditorWorkflow = workflow;
    }

    _diagramEditorDialog() {
        return html`
            <vaadin-dialog
                    .opened=${this._diagramEditorDialogOpened}
                    @opened-changed=${e => (this._diagramEditorDialogOpened = e.detail.value)}
                    ${dialogRenderer(() => this._diagramEditorContent(), [
                        this._diagramEditorDialogOpened
                    ])}
                    .width=${'80%'}
                    .height=${'80%'}
                    resizable
                    draggable
                    header-title="Flow Diagram"
                    theme="${themeState.theme.name}">
            </vaadin-dialog>
        `;
    }

    _diagramEditorContent() {
        return html`
            <qwc-serverless-workflow-diagram-editor
                    .workflow=${this._currentDiagramEditorWorkflow}
                    .readonly=${true}
                    .workflowKey="show-${this._generateDiagramEditorId(this._currentDiagramEditorWorkflow.id)}">
            </qwc-serverless-workflow-diagram-editor>
        `;
    }

    _executeWorkflow(workflow) {
        this._selectedWorkflow = workflow;
    }

    _showWorkflows() {
        this._selectedWorkflow = null;
    }
}

customElements.define('qwc-flow-workflows', QwcFlow);
