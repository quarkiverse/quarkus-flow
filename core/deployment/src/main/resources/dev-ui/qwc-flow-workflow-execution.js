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
import '@vaadin/text-field';
import '@vaadin/number-field';
import '@vaadin/checkbox';
import '@vaadin/text-area';

import 'qui-themed-code-block';
import 'qui-badge';

import { themeState } from 'theme-state';

export class QwcFlowExecution extends observeState(QwcHotReloadElement) {
    jsonRpc = new JsonRpc(this);

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
            margin: 12px 0px 4px 0px;
        }

        .workflow-meta {
            font-size: var(--lumo-font-size-s);
            color: var(--lumo-secondary-text-color);
            margin-bottom: 12px;
        }

        .form-field {
            margin-bottom: 8px;
            max-width: 500px;
        }
    `;

    static properties = {
        // WorkflowDefinitionId from backend: { namespace, name, version }
        workflowId: { type: Object },
        description: { type: String },
        extensionName: { type: String },

        _inputSchema: { state: true },
        _formModel: { state: true },

        _output: { state: true },
        _input: { state: true },
        _loading: { state: true }
    };

    constructor() {
        super();
        this.workflowId = null;
        this.description = '';
        this._inputSchema = null;
        this._formModel = null;
        this._input = '\n\n\n\n\n\n\n\n\n';
        this._output = '# Your workflow output will be displayed here\n\n\n\n\n\n\n\n\n\n';
        this._loading = false;
    }

    render() {
        return html`
            <div class="workflow-execution">
                ${this._renderForm()}
            </div>
        `;
    }

    connectedCallback() {
        super.connectedCallback();
        this.jsonRpc = new JsonRpc(this.extensionName);
    }

    updated(changedProps) {
        super.updated?.(changedProps);
        if (changedProps.has('workflowId') && this.workflowId && this.jsonRpc) {
            this._loadInputSchema();
        }
    }

    disconnectedCallback() {
        super.disconnectedCallback();
    }

    hotReload() {
        // no-op, required by QwcHotReloadElement
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
        const id = this.workflowId || {};
        const name = id.name || '(unknown)';
        const ns = id.namespace || '';
        const version = id.version || '';

        return html`
            <div>
                <vaadin-button @click="${this._backAction}" class="backButton">
                    <vaadin-icon icon="font-awesome-solid:caret-left" slot="prefix"></vaadin-icon>
                    Back
                </vaadin-button>
                <h2 class="workflow-name">${name}</h2>
                <div class="workflow-meta">
                    ${ns ? html`<span><b>Namespace:</b> ${ns}</span>` : ''}
                    ${version ? html`${ns ? ' · ' : ''}<span><b>Version:</b> ${version}</span>` : ''}
                    ${this.description ? html`
                        <br/>
                        <span>${this.description}</span>
                    ` : ''}
                </div>
            </div>
        `;
    }

    _renderForm() {
        return html`
            ${this._renderTopBar()}
            <vaadin-split-layout orientation="vertical" style="min-height: 600px; max-height: 600px;">
                <master-content class="layout-container" style="heigh: 50%;">
                    <qui-badge level='info' small><span>Input</span></qui-badge>
                    ${this._inputSchema
                            ? this._renderSchemaForm()
                            : this._renderRawInputEditor()}
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
                ${this._loading
                        ? html`<vaadin-progress-bar class="progress" indeterminate></vaadin-progress-bar>`
                        : html`<vaadin-button theme="primary success" @click=${() => this._executeWorkflow()}>
                            <vaadin-icon icon="font-awesome-solid:play"></vaadin-icon>
                            Start workflow
                        </vaadin-button>`
                }
            </div>
        `;
    }

    // ---------- Input UI ----------

    _renderRawInputEditor() {
        return html`
            <qui-themed-code-block
                    id="code"
                    class="code-block"
                    value="${this._input}"
                    content="${this._input}"
                    editable
                    showLineNumbers
                    theme="${themeState.theme.name}">
            </qui-themed-code-block>
        `;
    }

    _renderSchemaForm() {
        const schema = this._inputSchema || {};
        const properties = schema.properties || {};
        const keys = Object.keys(properties);

        if (keys.length === 0) {
            return this._renderRawInputEditor();
        }

        return html`
            <div>
                ${keys.map(name => this._renderField(name, properties[name], (schema.required || []).includes(name)))}
            </div>
        `;
    }

    _renderField(name, propSchema, required) {
        const type = propSchema.type || 'string';
        const label = `${name}${required ? ' *' : ''}`;
        const value = this._formModel ? this._formModel[name] : undefined;

        if (Array.isArray(propSchema.enum)) {
            return html`
                <vaadin-combo-box
                        class="form-field"
                        label="${label}"
                        .items=${propSchema.enum}
                        .value=${value ?? ''}
                        @value-changed=${e => this._onFieldChange(name, e.detail.value, 'enum')}>
                </vaadin-combo-box>
            `;
        }

        switch (type) {
            case 'string':
                return html`
                    <vaadin-text-field
                            class="form-field"
                            label="${label}"
                            .value=${value ?? ''}
                            @value-changed=${e => this._onFieldChange(name, e.detail.value, 'string')}>
                    </vaadin-text-field>
                `;
            case 'number':
            case 'integer':
                return html`
                    <vaadin-number-field
                            class="form-field"
                            label="${label}"
                            .value=${value ?? ''}
                            @value-changed=${e => this._onFieldChange(name, e.detail.value, 'number')}>
                    </vaadin-number-field>
                `;
            case 'boolean':
                return html`
                    <vaadin-checkbox
                            class="form-field"
                            ?checked=${value === true}
                            @checked-changed=${e => this._onFieldChange(name, e.detail.value, 'boolean')}>
                        ${label}
                    </vaadin-checkbox>
                `;
            case 'array':
            case 'object':
                return html`
                    <vaadin-text-area
                            class="form-field"
                            label="${label} (JSON)"
                            .value=${this._stringifyValue(value)}
                            @value-changed=${e => this._onFieldChange(name, e.detail.value, type)}>
                    </vaadin-text-area>
                `;
            default:
                return html`
                    <vaadin-text-field
                            class="form-field"
                            label="${label}"
                            .value=${value ?? ''}
                            @value-changed=${e => this._onFieldChange(name, e.detail.value, 'string')}>
                    </vaadin-text-field>
                `;
        }
    }

    _stringifyValue(value) {
        if (value === undefined || value === null) {
            return '';
        }
        if (typeof value === 'string') {
            return value;
        }
        try {
            return JSON.stringify(value, null, 2);
        } catch {
            return String(value);
        }
    }

    _onFieldChange(name, rawValue, kind) {
        const model = { ...(this._formModel || {}) };

        let parsed;
        switch (kind) {
            case 'string':
            case 'enum':
                parsed = rawValue ?? '';
                break;
            case 'number':
                parsed = rawValue === '' || rawValue == null ? null : Number(rawValue);
                break;
            case 'boolean':
                parsed = !!rawValue;
                break;
            case 'array':
            case 'object':
                if (!rawValue) {
                    parsed = (kind === 'array') ? [] : {};
                    break;
                }
                try {
                    parsed = JSON.parse(rawValue);
                } catch (e) {
                    notifier.showErrorMessage(`Invalid JSON for '${name}': ${e.message}`);
                    parsed = model[name];
                }
                break;
            default:
                parsed = rawValue;
        }

        model[name] = parsed;
        this._formModel = model;
        this._input = JSON.stringify(model, null, 2);
    }

    // ---------- Schema loading ----------

    _loadInputSchema() {
        if (!this.workflowId || !this.jsonRpc) {
            return;
        }
        this.jsonRpc.getInputSchema({ id: this.workflowId })
            .then(({ result }) => {
                console.log('Input schema for', this.workflowId, result);
                this._inputSchema = result || null;
                if (result) {
                    this._formModel = this._createDefaultModel(result);
                    this._input = JSON.stringify(this._formModel, null, 2);
                } else {
                    this._formModel = null;
                    this._input = '\n\n\n\n\n\n\n\n\n';
                }
            })
            .catch(err => {
                console.error('Error fetching input schema', err);
                this._inputSchema = null;
                this._formModel = null;
            });
    }

    _createDefaultModel(schema) {
        const model = {};
        const props = schema.properties || {};

        for (const [name, prop] of Object.entries(props)) {
            const type = prop.type || 'string';

            if (Array.isArray(prop.enum) && prop.enum.length > 0) {
                model[name] = prop.enum[0];
                continue;
            }

            switch (type) {
                case 'string':
                    model[name] = '';
                    break;
                case 'number':
                case 'integer':
                    model[name] = 0;
                    break;
                case 'boolean':
                    model[name] = false;
                    break;
                case 'array':
                    model[name] = [];
                    break;
                case 'object':
                    model[name] = {};
                    break;
                default:
                    model[name] = null;
            }
        }

        return model;
    }

    // ---------- Execution ----------

    _tryStringify(value) {
        try {
            return JSON.stringify(value, null, 2);
        } catch (err) {
            return null;
        }
    }

    _executeWorkflow() {
        if (!this.workflowId) {
            notifier.showErrorMessage('No workflow selected.');
            return;
        }

        let value;
        if (this._inputSchema && this._formModel) {
            value = JSON.stringify(this._formModel);
        } else {
            const codeBlock = this.shadowRoot.getElementById('code');
            value = codeBlock ? codeBlock.getAttribute('value') : '';
        }

        const payload = {
            id: this.workflowId,
            input: value
        };

        this._loading = true;

        this.jsonRpc.executeWorkflow(payload)
            .then(({ result }) => {
                console.log('workflow result', result);
                if (result.mimetype === 'text/plain') {
                    this._output = result.data;
                } else {
                    const parsedOutput = this._tryStringify(result.data);
                    if (parsedOutput) {
                        this._output = parsedOutput;
                    } else {
                        this._output = String(result.data ?? '');
                    }
                }
            })
            .catch(err => {
                const { error } = err
                console.log('error executing workflow', error);
                notifier.showErrorMessage('Error while executing workflow: ' + error.message);
                this._output = '# Error\n\n' + error.message;
            })
            .finally(() => {
                this._loading = false;
            });
    }
}

customElements.define('qwc-flow-workflow-execution', QwcFlowExecution);
