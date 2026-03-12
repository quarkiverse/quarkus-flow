import {
  GitGraphModule,
  createGitGraphServices
} from "./chunks/mermaid-parser.core/chunk-7E7YKBS2.mjs";
import {
  InfoModule,
  createInfoServices
} from "./chunks/mermaid-parser.core/chunk-EGIJ26TM.mjs";
import {
  PacketModule,
  createPacketServices
} from "./chunks/mermaid-parser.core/chunk-C72U2L5F.mjs";
import {
  PieModule,
  createPieServices
} from "./chunks/mermaid-parser.core/chunk-XIRO2GV7.mjs";
import {
  ArchitectureModule,
  createArchitectureServices
} from "./chunks/mermaid-parser.core/chunk-R5LLSJPH.mjs";
import {
  RadarModule,
  createRadarServices
} from "./chunks/mermaid-parser.core/chunk-L3YUKLVL.mjs";
import {
  TreemapModule,
  createTreemapServices
} from "./chunks/mermaid-parser.core/chunk-OZEHJAEY.mjs";
import {
  AbstractMermaidTokenBuilder,
  AbstractMermaidValueConverter,
  Architecture,
  ArchitectureGrammarGeneratedModule,
  Branch,
  Commit,
  CommonTokenBuilder,
  CommonValueConverter,
  GitGraph,
  GitGraphGrammarGeneratedModule,
  Info,
  InfoGrammarGeneratedModule,
  Merge,
  MermaidGeneratedSharedModule,
  Packet,
  PacketBlock,
  PacketGrammarGeneratedModule,
  Pie,
  PieGrammarGeneratedModule,
  PieSection,
  Radar,
  RadarGrammarGeneratedModule,
  Statement,
  Treemap,
  TreemapGrammarGeneratedModule,
  __name,
  isArchitecture,
  isBranch,
  isCommit,
  isGitGraph,
  isInfo,
  isMerge,
  isPacket,
  isPacketBlock,
  isPie,
  isPieSection,
  isTreemap
} from "./chunks/mermaid-parser.core/chunk-XZSTWKYB.mjs";

// src/parse.ts
var parsers = {};
var initializers = {
  info: /* @__PURE__ */ __name(async () => {
    const { createInfoServices: createInfoServices2 } = await import("./chunks/mermaid-parser.core/info-3K5VOQVL.mjs");
    const parser = createInfoServices2().Info.parser.LangiumParser;
    parsers.info = parser;
  }, "info"),
  packet: /* @__PURE__ */ __name(async () => {
    const { createPacketServices: createPacketServices2 } = await import("./chunks/mermaid-parser.core/packet-RMMSAZCW.mjs");
    const parser = createPacketServices2().Packet.parser.LangiumParser;
    parsers.packet = parser;
  }, "packet"),
  pie: /* @__PURE__ */ __name(async () => {
    const { createPieServices: createPieServices2 } = await import("./chunks/mermaid-parser.core/pie-UPGHQEXC.mjs");
    const parser = createPieServices2().Pie.parser.LangiumParser;
    parsers.pie = parser;
  }, "pie"),
  architecture: /* @__PURE__ */ __name(async () => {
    const { createArchitectureServices: createArchitectureServices2 } = await import("./chunks/mermaid-parser.core/architecture-PBZL5I3N.mjs");
    const parser = createArchitectureServices2().Architecture.parser.LangiumParser;
    parsers.architecture = parser;
  }, "architecture"),
  gitGraph: /* @__PURE__ */ __name(async () => {
    const { createGitGraphServices: createGitGraphServices2 } = await import("./chunks/mermaid-parser.core/gitGraph-HDMCJU4V.mjs");
    const parser = createGitGraphServices2().GitGraph.parser.LangiumParser;
    parsers.gitGraph = parser;
  }, "gitGraph"),
  radar: /* @__PURE__ */ __name(async () => {
    const { createRadarServices: createRadarServices2 } = await import("./chunks/mermaid-parser.core/radar-KQ55EAFF.mjs");
    const parser = createRadarServices2().Radar.parser.LangiumParser;
    parsers.radar = parser;
  }, "radar"),
  treemap: /* @__PURE__ */ __name(async () => {
    const { createTreemapServices: createTreemapServices2 } = await import("./chunks/mermaid-parser.core/treemap-KZPCXAKY.mjs");
    const parser = createTreemapServices2().Treemap.parser.LangiumParser;
    parsers.treemap = parser;
  }, "treemap")
};
async function parse(diagramType, text) {
  const initializer = initializers[diagramType];
  if (!initializer) {
    throw new Error(`Unknown diagram type: ${diagramType}`);
  }
  if (!parsers[diagramType]) {
    await initializer();
  }
  const parser = parsers[diagramType];
  const result = parser.parse(text);
  if (result.lexerErrors.length > 0 || result.parserErrors.length > 0) {
    throw new MermaidParseError(result);
  }
  return result.value;
}
__name(parse, "parse");
var MermaidParseError = class extends Error {
  constructor(result) {
    const lexerErrors = result.lexerErrors.map((err) => {
      const line = err.line !== void 0 && !isNaN(err.line) ? err.line : "?";
      const column = err.column !== void 0 && !isNaN(err.column) ? err.column : "?";
      return `Lexer error on line ${line}, column ${column}: ${err.message}`;
    }).join("\n");
    const parserErrors = result.parserErrors.map((err) => {
      const line = err.token.startLine !== void 0 && !isNaN(err.token.startLine) ? err.token.startLine : "?";
      const column = err.token.startColumn !== void 0 && !isNaN(err.token.startColumn) ? err.token.startColumn : "?";
      return `Parse error on line ${line}, column ${column}: ${err.message}`;
    }).join("\n");
    super(`Parsing failed: ${lexerErrors} ${parserErrors}`);
    this.result = result;
  }
  static {
    __name(this, "MermaidParseError");
  }
};
export {
  AbstractMermaidTokenBuilder,
  AbstractMermaidValueConverter,
  Architecture,
  ArchitectureGrammarGeneratedModule as ArchitectureGeneratedModule,
  ArchitectureModule,
  Branch,
  Commit,
  CommonTokenBuilder,
  CommonValueConverter,
  GitGraph,
  GitGraphGrammarGeneratedModule as GitGraphGeneratedModule,
  GitGraphModule,
  Info,
  InfoGrammarGeneratedModule as InfoGeneratedModule,
  InfoModule,
  Merge,
  MermaidGeneratedSharedModule,
  MermaidParseError,
  Packet,
  PacketBlock,
  PacketGrammarGeneratedModule as PacketGeneratedModule,
  PacketModule,
  Pie,
  PieGrammarGeneratedModule as PieGeneratedModule,
  PieModule,
  PieSection,
  Radar,
  RadarGrammarGeneratedModule as RadarGeneratedModule,
  RadarModule,
  Statement,
  Treemap,
  TreemapGrammarGeneratedModule as TreemapGeneratedModule,
  TreemapModule,
  createArchitectureServices,
  createGitGraphServices,
  createInfoServices,
  createPacketServices,
  createPieServices,
  createRadarServices,
  createTreemapServices,
  isArchitecture,
  isBranch,
  isCommit,
  isGitGraph,
  isInfo,
  isMerge,
  isPacket,
  isPacketBlock,
  isPie,
  isPieSection,
  isTreemap,
  parse
};
