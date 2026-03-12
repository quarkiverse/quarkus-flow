import {
  StateDB,
  stateDiagram_default,
  stateRenderer_v3_unified_default,
  styles_default
} from "./chunk-3VXWS7DV.mjs";
import "./chunk-U2CUWHSV.mjs";
import "./chunk-M4FGLBOT.mjs";
import "./chunk-EKL7DDBO.mjs";
import "./chunk-TZW2344G.mjs";
import "./chunk-UYNZS3GD.mjs";
import "./chunk-UAL2TQZE.mjs";
import "./chunk-FWQXANIB.mjs";
import "./chunk-D2KOLKXV.mjs";
import "./chunk-TEV7DDKD.mjs";
import "./chunk-YHLHMBDM.mjs";
import "./chunk-XCSQNVCT.mjs";
import "./chunk-3OTVAOVH.mjs";
import "./chunk-UKMXQNCB.mjs";
import "./chunk-GRVEB7DL.mjs";
import "./chunk-3QJOF6JT.mjs";
import {
  __name
} from "./chunk-FQFHLQFH.mjs";

// src/diagrams/state/stateDiagram-v2.ts
var diagram = {
  parser: stateDiagram_default,
  get db() {
    return new StateDB(2);
  },
  renderer: stateRenderer_v3_unified_default,
  styles: styles_default,
  init: /* @__PURE__ */ __name((cnf) => {
    if (!cnf.state) {
      cnf.state = {};
    }
    cnf.state.arrowMarkerAbsolute = cnf.arrowMarkerAbsolute;
  }, "init")
};
export {
  diagram
};
