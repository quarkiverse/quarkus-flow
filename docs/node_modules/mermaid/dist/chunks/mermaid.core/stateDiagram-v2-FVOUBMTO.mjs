import {
  StateDB,
  stateDiagram_default,
  stateRenderer_v3_unified_default,
  styles_default
} from "./chunk-NQ4KR5QH.mjs";
import "./chunk-55IACEB6.mjs";
import "./chunk-KX2RTZJC.mjs";
import "./chunk-GLR3WWYH.mjs";
import "./chunk-O4XLMI2P.mjs";
import "./chunk-MX3YWQON.mjs";
import "./chunk-KYZI473N.mjs";
import "./chunk-YBOYWFTD.mjs";
import "./chunk-PQ6SQG4A.mjs";
import "./chunk-PU5JKC2W.mjs";
import "./chunk-GEFDOKGD.mjs";
import "./chunk-7R4GIKGN.mjs";
import {
  __name
} from "./chunk-AGHRB4JF.mjs";

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
