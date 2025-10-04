import { QwcHotReloadElement, html, css } from 'qwc-hot-reload-element';
import { observeState } from 'lit-element-state';
import { JsonRpc } from 'jsonrpc';

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
    `;

    _codeModeMapping = {
        "text": "markdown",
        "no_input": "markdown",
        "json": "json"
    };

    _items = [
        {
            value: "json",
            label: "JSON"
        },
        {
            value: "text",
            label: "Text"
        },
        {
            value: "no_input",
            label: "No input"
        }
    ];

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
        this._output = '\n\n\n\n\n\n\n\n\n\n# Your workflow output will be displayed here';
        this._selectedInputFormat = '';
        this._loading = false;
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc = new JsonRpc(this.extensionName);
        this.jsonRpc.getInputSpecification({ workflowName: this.workflow }).then(({ result }) => {
            this._inputSpec = result;
        });
    }

    disconnectedCallback() {
        super.disconnectedCallback();
    }

    hotReload() {
        // no-op, it is necessary due to QwcHotReloadElement extends
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
                        <h2>${this.workflow}</h2>
                    </div>`;
    }

    _renderForm() {
        if (this._inputSpec === null) {
            return html`<vaadin-progress-bar class="progress" indeterminate></vaadin-progress-bar>`;
        } else {
            return html`
                ${this._renderTopBar()}
                <vaadin-combo-box
                    class="fmt-combo"
                    label="Input format"
                    .items="${this._items}"
                    @value-changed="${this._onInputFormatChange}"
                    item-label-path="label"
                    item-value-path="value">
                </vaadin-combo-box>

                <vaadin-split-layout orientation="vertical" style="min-height: 600px; max-height: 600px;">
                    <master-content class="layout-container">
                        <qui-badge level='info' small><span>Input</span></qui-badge>
                        <qui-themed-code-block
                            id="code"
                            class="code-block"
                            mode="${this._codeModeMapping[this._selectedInputFormat]}"
                            value="${this._input}"
                            content="${this._input}"
                            editable
                            showLineNumbers
                            theme="${themeState.theme.name}">
                        </qui-themed-code-block>
                    </master-content>
                    <detail-content class="layout-container">
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
                    html`<vaadin-button @click=${() => this._executeWorkflow()}>
                        <vaadin-icon icon="font-awesome-solid:play"></vaadin-icon>
                        Start workflow
                    </vaadin-button>`}
                </div>`;
        }
    }

    _executeWorkflow() {
        const inputElement = this.shadowRoot.getElementById('code');

        let workflowInput = {
            workflowName: this.workflow
        };

        if (["json"].includes(this._selectedInputFormat)) {
            workflowInput = {
                ...workflowInput,
                inputAsObject: JSON.parse(inputElement.getAttribute('value')),
                inputAsText: null
            };
        } else if (["text", "no_input"].includes(this._selectedInputFormat)) {
            workflowInput = {
                ...workflowInput,
                inputAsObject: null,
                inputAsText: inputElement.getAttribute('value')
            };
        }

        this._loading = true;

        this.jsonRpc.executeWorkflow(workflowInput).then(({ result }) => {
            if (this._selectedInputFormat === "text") {
                console.log(result);
                this._output = result.output;
            } else if (this._selectedInputFormat === "no_input") {
                this._output = result;
            } else {
                this._output = JSON.stringify(result);
            }
        }).catch(err => console.error(err))
            .finally(() => {
                this._loading = false;
            })
    }

    render() {
        return html`<div class="workflow-execution">
                ${this._renderForm()}
        </div>`;
    }
}

customElements.define('qwc-flow-workflow-execution', QwcFlowExecution);