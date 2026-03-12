import {
  ClassDB,
  classDiagram_default,
  classRenderer_v3_unified_default,
  styles_default
} from "./chunk-WL4C6EOR.mjs";
import "./chunk-FMBD7UC4.mjs";
import "./chunk-JSJVCQXG.mjs";
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

// src/diagrams/class/classDiagram-v2.ts
var diagram = {
  parser: classDiagram_default,
  get db() {
    return new ClassDB();
  },
  renderer: classRenderer_v3_unified_default,
  styles: styles_default,
  init: /* @__PURE__ */ __name((cnf) => {
    if (!cnf.class) {
      cnf.class = {};
    }
    cnf.class.arrowMarkerAbsolute = cnf.arrowMarkerAbsolute;
  }, "init")
};
export {
  diagram
};
