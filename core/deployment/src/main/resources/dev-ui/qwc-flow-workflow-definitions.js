import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { observeState } from 'lit-element-state';
import { JsonRpc } from 'jsonrpc';
import { unsafeHTML } from "lit/directives/unsafe-html.js"
import { devuiState } from 'devui-state';
import '@vaadin/grid';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/dialog';
import { notifier } from 'notifier';

import { dialogRenderer, dialogFooterRenderer } from '@vaadin/dialog/lit.js';
import { columnBodyRenderer } from '@vaadin/grid/lit.js';
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
        _mermaidDialogOpened: { state: true }
    };

    constructor() {
        super();
        this._workflows = [];
        this._currentMermaid = '';
        this._mermaidDialogOpened = false;
    }

    connectedCallback() {
        super.connectedCallback();
        if (!window.mermaid) {
            const script = document.createElement('script');
            script.src = 'https://cdn.jsdelivr.net/npm/mermaid@11.12.0/dist/mermaid.min.js';
            document.head.appendChild(script);
        }
        this.jsonRpc.getWorkflows().then(({ result }) => {
            this._workflows = result;
        });
    }

    visualizeMermaid(workflow) {
        this.jsonRpc.generateMermaid(workflow).then(({ result }) => {
            this._currentMermaid = result.mermaid.replace(/^---[\s\S]*?---\s*/, '');
            this._mermaidDialogOpened = true;
        });
    }

    mermaidContent() {
        return unsafeHTML(`
             <pre class="mermaid mermaid-container" style="display: flex; flex-direction: column; align-items: center;">
               ${(this._currentMermaid)}
            </pre>`);
    }

    downloadDiagramAsPng() {
        let svgData = document.querySelector('pre.mermaid > svg');
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

    mermaidDialog() {
        return html`
            <vaadin-dialog
                .opened=${this._mermaidDialogOpened}
                @opened-changed=${e => (this._mermaidDialogOpened = e.detail.value)}
                ${dialogRenderer(() => this.mermaidContent(), [])}
                ${dialogFooterRenderer(() => html`
                    <div class="buttonBar">
                        <vaadin-button class="button" @click=${() => this.downloadDiagramAsPng()}>
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

    updated(changedProps) {
        super.updated?.(changedProps);
        if (
            this._currentMermaid &&
            window.mermaid
        ) {
            window.mermaid.init({
                startOnLoad: false,
                theme: themeState.theme.name === 'dark' ? 'dark' : 'default',
                fontFamily: 'var(--lumo-font-family)',
                fontSize: 12,
                flowchart: {
                    useMaxWidth: true,
                    htmlLabels: true,
                }
            }, '.mermaid');
        }
    }

    render() {
        return html`
            <div class="workflows">
                <vaadin-grid
                    .items=${this._workflows}
                    theme="row-stripes"
                    column-reordering-allowed
                    multi-sort>
                    <vaadin-grid-column
                        path="name"
                        header="Name"
                        auto-width>
                    </vaadin-grid-column>
                    <vaadin-grid-column
                        header="Mermaid"
                        auto-width
                        ${columnBodyRenderer(
            workflow => html`
                            <vaadin-button @click=${() => this.visualizeMermaid(workflow)}>
                                <vaadin-icon icon="font-awesome-solid:eye"></vaadin-icon>
                            </vaadin-button>
                            `,
            []
        )}>
                    </vaadin-grid-column>
                </vaadin-grid>
                ${this.mermaidDialog()}
            </div>
        `;
    }
}

customElements.define('qwc-flow-workflow-definitions', QwcFlow);