import { LitElement, html, css } from 'lit';
import '@vaadin/button';
import '@vaadin/icon';

/**
* Shared header for workflow detail.
* Renders a Back button plus the workflow name, namespace/version and description.
*/
export class QwcFlowWorkflowHeader extends LitElement {
 static properties = {
   // WorkflowDefinitionId from backend: { namespace, name, version }
   workflowId: { type: Object },
   description: { type: String },
 };

 static styles = css`
   :host {
     display: block;
   }

   .workflow-name {
     margin: 12px 0px 4px 0px;
   }

   .workflow-meta {
     font-size: var(--lumo-font-size-s);
     color: var(--lumo-secondary-text-color);
     margin-bottom: 12px;
   }
 `;

 constructor() {
   super();
   this.workflowId = null;
   this.description = '';
 }

 render() {
   const id = this.workflowId || {};
   const name = id.name || '(unknown)';
   const ns = id.namespace || '';
   const version = id.version || '';

   return html`
     <vaadin-button @click=${this._backAction} class="backButton">
       <vaadin-icon icon="font-awesome-solid:caret-left" slot="prefix"></vaadin-icon>
       Back
     </vaadin-button>
     <h2 class="workflow-name">${name}</h2>
     <div class="workflow-meta">
       ${ns ? html`<span><b>Namespace:</b> ${ns}</span>` : ''}
       ${version ? html`${ns ? ' · ' : ''}<span><b>Version:</b> ${version}</span>` : ''}
       ${this.description
         ? html`
             <br />
             <span>${this.description}</span>
           `
         : ''}
     </div>
   `;
 }

 _backAction() {
   this.dispatchEvent(
     new CustomEvent('flow-header-back', {
       detail: {},
       bubbles: true,
       cancelable: true,
       composed: false,
     })
   );
 }
}

customElements.define('qwc-flow-workflow-header', QwcFlowWorkflowHeader);
