import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { observeState } from 'lit-element-state';
import { JsonRpc } from 'jsonrpc';
import { unsafeHTML } from "lit/directives/unsafe-html.js"
import { devuiState } from 'devui-state';
import '@vaadin/grid';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/dialog';
import { dialogRenderer, dialogFooterRenderer } from '@vaadin/dialog/lit.js';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';

import { notifier } from 'notifier';
import './qwc-flow-workflow-execution.js';

import { themeState } from 'theme-state';

export class QwcFlow extends observeState(QwcHotReloadElement) {
    jsonRpc = new JsonRpc(this);

    static styles = css`
        .workflows {
            padding: 8px;
        }
        .buttonBar {
            display: flex;
            justify-content: space-between;
            gap: 10px;
            align-items: center;
            width: 90%;
            color: var(--lumo-primary-text-color);
            width: 100%;
        }
    `;

    static properties = {
        _workflows: { state: true },
        _currentMermaid: { state: true },
        _mermaidDialogOpened: { state: true },
        _selectedWorkflow: { state: true }
    };

    constructor() {
        super();
        this._workflows = [];
        this._currentMermaid = '';
        this._mermaidDialogOpened = false;
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
                                    <vaadin-button @click=${() => this._visualizeMermaid(workflow)}>
                                        <vaadin-icon icon="font-awesome-solid:eye"></vaadin-icon>
                                    </vaadin-button>
                                    <vaadin-button @click=${() => this._executeWorkflow(workflow)}>
                                        <vaadin-icon icon="font-awesome-solid:play"></vaadin-icon>
                                    </vaadin-button>
                                `, [])}>
                        </vaadin-grid-column>
                    </vaadin-grid>
                    ${this._mermaidDialog()}
                </div>
            `;
        }
    }

    hotReload() {
        // no-op, it is necessary due to QwcHotReloadElement
    }

    connectedCallback() {
        super.connectedCallback();
        if (!window.mermaid) {
            const script = document.createElement('script');
            script.src = 'https://cdn.jsdelivr.net/npm/mermaid@11.12.0/dist/mermaid.min.js';
            document.head.appendChild(script);
        }
        this.jsonRpc.getWorkflows().then(({ result }) => {
            // result: [{ id: { namespace, name, version }, description }]
            this._workflows = result;
        });
    }

    disconnectedCallback() {
        super.disconnectedCallback();
    }

    updated(changedProps) {
        super.updated?.(changedProps);
        if (this._currentMermaid && window.mermaid) {
            window.mermaid.init({
                startOnLoad: false,
                theme: themeState.theme.name === 'dark' ? 'dark' : 'default',
                fontFamily: 'var(--lumo-font-family)',
                fontSize: 12,
                flowchart: {
                    useMaxWidth: true,
                    htmlLabels: true,
                },
                themeCSS: ".label foreignObject { font-size: 14px; overflow: visible; width: auto; }"
            }, '.mermaid');
        }
    }

    _visualizeMermaid(workflow) {
        // Backend expects: generateMermaidDiagram(WorkflowDefinitionId id)
        this.jsonRpc.generateMermaidDiagram({
            id: workflow.id
        }).then(({ result }) => {
            this._currentMermaid = result.mermaid.replace(/^---[\s\S]*?---\s*/, '');
            this._mermaidDialogOpened = true;
        });
    }

    _mermaidContent() {
        return unsafeHTML(`
             <pre class="mermaid mermaid-container" style="display: flex; flex-direction: column; align-items: center;">
               ${this._currentMermaid}
            </pre>`);
    }

    _downloadDiagramAsPng() {
        let svgData = document.querySelector('pre.mermaid > svg');
        if (!svgData) {
            return;
        }
        let img = new Image(svgData.width.baseVal.value, svgData.height.baseVal.value);
        img.src = `data:image/svg+xml;base64,${btoa(new XMLSerializer().serializeToString(svgData))}`;
        img.onload = function () {
            let cnv = document.createElement('canvas');
            cnv.width = img.width;
            cnv.height = img.height;
            cnv.getContext("2d").drawImage(img, 0, 0);
            cnv.toBlob((blob) => {
                let lnk = document.createElement('a');
                lnk.href = URL.createObjectURL(blob);
                lnk.download = "flow-" + devuiState.applicationInfo.applicationName + "-" + new Date().toISOString().replace(/\D/g, '') + ".png";
                lnk.click();
                notifier.showSuccessMessage(lnk.download + " downloaded.", 'bottom-end');
            });
        }
    }

    _mermaidDialog() {
        return html`
            <vaadin-dialog
                    .opened=${this._mermaidDialogOpened}
                    @opened-changed=${e => (this._mermaidDialogOpened = e.detail.value)}
                    ${dialogRenderer(() => this._mermaidContent(), [])}
                    ${dialogFooterRenderer(() => html`
                        <div class="buttonBar">
                            <vaadin-button class="button" @click=${() => this._downloadDiagramAsPng()}>
                                <vaadin-icon icon="font-awesome-solid:file-export"></vaadin-icon>
                                Export
                            </vaadin-button>
                        </div>
                    `, [])}
                    .width=${'800px'}
                    .height=${'600px'}
                    resizable
                    draggable
                    header-title="Flow Diagram"
                    theme="${themeState.theme.name}">
            </vaadin-dialog>
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
