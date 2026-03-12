import {
  parse
} from "./chunk-6OSLE3NY.mjs";
import "./chunk-OBMQL7OE.mjs";
import "./chunk-BDXV45HO.mjs";
import "./chunk-ALB5NAU3.mjs";
import "./chunk-LYCWX5BJ.mjs";
import "./chunk-LIIAENVV.mjs";
import "./chunk-NUOAVMUD.mjs";
import {
  selectSvgElement
} from "./chunk-NWP4V2O6.mjs";
import {
  configureSvgSize
} from "./chunk-UKMXQNCB.mjs";
import {
  log
} from "./chunk-GRVEB7DL.mjs";
import "./chunk-75NCXSTK.mjs";
import "./chunk-S3MDV2AR.mjs";
import "./chunk-MFRUYFWM.mjs";
import "./chunk-UKL4YMJ2.mjs";
import "./chunk-3QJOF6JT.mjs";
import {
  __name
} from "./chunk-FQFHLQFH.mjs";

// src/diagrams/info/infoParser.ts
var parser = {
  parse: /* @__PURE__ */ __name(async (input) => {
    const ast = await parse("info", input);
    log.debug(ast);
  }, "parse")
};

// src/diagrams/info/infoDb.ts
var DEFAULT_INFO_DB = {
  version: "11.13.0" + (true ? "" : "-tiny")
};
var getVersion = /* @__PURE__ */ __name(() => DEFAULT_INFO_DB.version, "getVersion");
var db = {
  getVersion
};

// src/diagrams/info/infoRenderer.ts
var draw = /* @__PURE__ */ __name((text, id, version) => {
  log.debug("rendering info diagram\n" + text);
  const svg = selectSvgElement(id);
  configureSvgSize(svg, 100, 400, true);
  const group = svg.append("g");
  group.append("text").attr("x", 100).attr("y", 40).attr("class", "version").attr("font-size", 32).style("text-anchor", "middle").text(`v${version}`);
}, "draw");
var renderer = { draw };

// src/diagrams/info/infoDiagram.ts
var diagram = {
  parser,
  db,
  renderer
};
export {
  diagram
};
