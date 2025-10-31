import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { observeState } from 'lit-element-state';
import { JsonRpc } from 'jsonrpc';
import { notifier } from 'notifier';

import '@vaadin/grid';
import '@vaadin/button';
import '@vaadin/icon';
import '@vaadin/dialog';
import '@vaadin/vertical-layout';
import '@vaadin/split-layout';
import '@vaadin/combo-box';
import '@vaadin/progress-bar';
import 'qui-themed-code-block';
import 'qui-badge';

import { themeState } from 'theme-state';

export class QwcFlowExecution extends observeState(QwcHotReloadElement) {

    static styles = css`
        .workflow-execution {
            padding: 0px 8px;
            display: flex;
            flex-direction: column;
        }
        
        .fmt-combo {
            width: 200px;
            margin: 10px 0px;
        }

        .code-block {
            max-height: 90%;
        }

        .layout-container {
            height: 100%;
        }

        .button-container {
            margin-top: auto;
        }

        .workflow-name {
            margin: 12px 0px;
        }
    `;

    static properties = {
        workflow: { type: String },
        extensionName: { type: String },
        _inputSpec: { state: true },
        _output: { state: true },
        _input: { state: true },
        _selectedInputFormat: { state: true },
        _loading: { state: true }
    };

    constructor() {
        super();
        this._inputSpec = null;
        this._input = '\n\n\n\n\n\n\n\n\n';
        this._output = '# Your workflow output will be displayed here\n\n\n\n\n\n\n\n\n\n';
        this._selectedInputFormat = '';
        this._loading = false;
    }

    render() {
        return html`<div class="workflow-execution">
                ${this._renderForm()}
        </div>`;
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc = new JsonRpc(this.extensionName);
    }

    disconnectedCallback() {
        super.disconnectedCallback();
    }

    hotReload() {
        // no-op, it is necessary due to QwcHotReloadElement
    }

    _onInputFormatChange({ detail }) {
        this._selectedInputFormat = detail.value;
    }

    _backAction() {
        const back = new CustomEvent("flow-workflows-back", {
            detail: {},
            bubbles: true,
            cancelable: true,
            composed: false,
        });
        this.dispatchEvent(back);
    }

    _renderTopBar() {
        return html`
                    <div>
                        <vaadin-button @click="${this._backAction}" class="backButton">
                            <vaadin-icon icon="font-awesome-solid:caret-left" slot="prefix"></vaadin-icon>
                            Back
                        </vaadin-button>
                        <h2 class="workflow-name">${this.workflow}</h2>
                    </div>`;
    }

    _renderForm() {
        return html`
            ${this._renderTopBar()}
            <vaadin-split-layout orientation="vertical" style="min-height: 600px; max-height: 600px;">
                <master-content class="layout-container" style="heigh: 50%;">
                    <qui-badge level='info' small><span>Input</span></qui-badge>
                    <qui-themed-code-block
                        id="code"
                        class="code-block"
                        value="${this._input}"
                        content="${this._input}"
                        editable
                        showLineNumbers
                        theme="${themeState.theme.name}">
                    </qui-themed-code-block>
                </master-content>
                <detail-content class="layout-container" style="heigh: 50%;" >
                    <qui-badge level="info" small><span>Output</span></qui-badge>
                    <qui-themed-code-block
                        class="code-block"
                        mode="markdown"
                        theme="${themeState.theme.name}"
                        content="${this._output}">
                    </qui-themed-code-block>
                </detail-content>
            </vaadin-split-layout>
            <div class="button-container">
                ${this._loading ?
                html`<vaadin-progress-bar class="progress" indeterminate></vaadin-progress-bar>` :
                html`<vaadin-button theme="primary success" @click=${() => this._executeWorkflow()}>
                    <vaadin-icon icon="font-awesome-solid:play"></vaadin-icon>
                    Start workflow
                </vaadin-button>`}
            </div>`;
    }

    _tryParse(value) {
        try {
            return JSON.parse(value);
        } catch (err) {
            return null;
        }
    }

    _tryStringify(value) {
        try {
            return JSON.stringify(value);
        } catch (err) {
            return null
        }
    }
    _executeWorkflow() {
        const value = this.shadowRoot.getElementById('code').getAttribute('value');
        let workflowInput = {
            workflowName: this.workflow,
            input: value
        };

        this._loading = true;

        this.jsonRpc.executeWorkflow(workflowInput).then(({ result }) => {
            console.log('workflow result', result);
            if (result.mimetype === 'text/plain') {
                this._output = result.data;
            } else {
                const parsedOutput = this._tryStringify(result.data);
                if (parsedOutput) {
                    this._output = parsedOutput;
                } else {
                    this._output = result;
                }
            }
        })
            .catch(err => {
                console.log('error executing workflow', err);
                notifier.showErrorMessage('Error while executing workflow: ' + err.message);
                this._output = '# Error\n\n' + err.message;
            })
            .finally(() => {
                this._loading = false;
            });
    }
}

customElements.define('qwc-flow-workflow-execution', QwcFlowExecution);