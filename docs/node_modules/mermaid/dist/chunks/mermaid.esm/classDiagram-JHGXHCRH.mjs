import {
  ClassDB,
  classDiagram_default,
  classRenderer_v3_unified_default,
  styles_default
} from "./chunk-2WLNDWH6.mjs";
import "./chunk-SXA26SXQ.mjs";
import "./chunk-SBHDPRHC.mjs";
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

// src/diagrams/class/classDiagram.ts
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
