# Quarkus Flow Knowledge Graph

This project maintains a **knowledge graph** of the entire codebase to accelerate development, code review, and architectural understanding.

## What is the Knowledge Graph?

A persistent, queryable representation of the codebase structure:

- **Thousands of nodes**: Classes, functions, workflows, concepts, documentation
- **Thousands of edges**: Relationships (calls, inherits, references, configures)
- **Hundreds of communities**: Automatically detected module clusters
- **Location**: `.graphify/graph.json`

See `.graphify/GRAPH_REPORT.md` for current statistics (nodes, edges, communities, god nodes, etc.).

**Why use it?** Answer architectural questions in seconds without reading dozens of files.

---

## Repository Scope

**The committed knowledge graph covers quarkus-flow only** (this repository).

**Why not include the SDK?** Different contributors may:
- Not have the SDK cloned locally
- Have it in different paths
- Work with different SDK versions

**The committed graph is reproducible by all contributors.**

### Optional: Cross-Repository Graph (Local Use)

If you want to explore quarkus-flow ↔ SDK relationships locally:

```bash
# Let graphify clone the SDK for you (recommended)
/graphify . https://github.com/serverlessworkflow/sdk-java

# Or use your local SDK clone
/graphify . ../serverlessworkflow/sdk-java
```

**This creates a merged graph locally. Keep it for your analysis but don't commit it.**

Before committing, rebuild with quarkus-flow only:
```bash
/graphify .  # Back to quarkus-flow only
git add .graphify/graph.json
```

---

## Quick Start: Querying the Graph

### Prerequisites

```bash
# Install graphify (once)
pip install graphifyy

# Or with uv
uv tool install graphifyy
```

### Common Queries

**1. Find how a feature is implemented**
```bash
/graphify query "How does OAuth2 authentication work in workflows?"
```

**2. Understand module relationships**
```bash
/graphify query "What modules depend on the persistence layer?"
```

**3. Find examples**
```bash
/graphify query "Which examples demonstrate LangChain4j integration?"
```

**4. Trace execution paths**
```bash
/graphify path "RestEndpoint" "DatabasePersistence"
```

**5. Understand a specific component**
```bash
/graphify explain "FlowDSL"
```

### Query from Claude Code

If using Claude Code (recommended):

```
/graphify query "your question here"
```

Claude Code will:
1. Load the graph
2. Traverse relevant nodes/edges
3. Answer using only graph structure (no file reads needed)

---

## Updating the Graph

### When to Update

Update the graph after:
- ✅ Adding new features
- ✅ Refactoring code structure
- ✅ Updating documentation
- ✅ Adding new examples
- ✅ Major dependency changes

**Don't update** after:
- ❌ Small bug fixes in existing methods
- ❌ Code formatting changes
- ❌ Comment updates (unless in docs/)

### Incremental Update (Fast - Recommended)

After making code changes:

```bash
# Only re-extracts changed files (uses cache for the rest)
/graphify --update

# Or with Claude Code:
/graphify --update
```

**Performance**: 
- First build: ~2-3 minutes (all files)
- Incremental update: ~5-15 seconds (changed files only)

**What gets updated**:
- ✅ Modified .java files (AST re-extracted)
- ✅ Modified .md/.adoc files (semantic re-extracted)
- ✅ Modified images (vision re-extracted)
- ✅ New files added to the repo
- ⏭️ Unchanged files (served from cache)

### Full Rebuild (Slow - Rarely Needed)

Only needed if:
- Graph structure seems corrupted
- Major graphify version upgrade
- You want to regenerate all labels/communities

```bash
# Full rebuild (ignores cache)
rm -rf .graphify/cache/
/graphify
```

---

## Graph Outputs

After building/updating, you'll find:

```
.graphify/
├── graph.json              # Complete knowledge graph (commit this)
├── GRAPH_REPORT.md         # Analysis report (commit this)
├── cache/                  # Extraction cache (commit this)
│   ├── ast/               # AST extraction cache
│   └── semantic/          # Semantic extraction cache
├── cost.json              # Token usage tracking (ignored)
└── .graphify_labels.json   # Community labels (ignored)
.graphify_manifest.json     # File manifest for incremental updates (commit this)
```

**What to commit**:

**Note**: The graph is auto-updated on merge to `main` via GitHub Actions. You can optionally include graph updates in your PR:

```bash
# Optional - graph will auto-update on merge anyway
git add .graphify/graph.json
git add .graphify/GRAPH_REPORT.md

# Recommended - helps team reuse your extraction work
git add .graphify/cache/           # Team shares extraction cache
git add .graphify_manifest.json   # Enables incremental updates
```

**What to ignore** (already in .gitignore):
- `.graphify/*.html` (generated on-demand)
- `.graphify/.graphify_*` (temp files)
- `.graphify/cost.json` (local tracking)

---

## Understanding the Report

`GRAPH_REPORT.md` contains:

### 1. God Nodes (Most Connected Components)

Example:
```
1. Flow - 216 edges
2. FlowDSL - 167 edges
3. FlowWorkflowBuilder - 109 edges
```

**What this means**: Core abstractions that many modules depend on. High connection count = high impact if changed.

### 2. Surprising Connections

Cross-cutting relationships you might not expect:

```
HelloResource → HelloWorkflow [docs example → codestart]
```

**Use case**: Understanding how examples relate to core code, finding cross-module dependencies.

### 3. Suggested Questions

Architecture questions the graph can answer efficiently:

```
"Why does Flow bridge 36 communities?"
"Should Dev UI module be split into smaller modules?" (low cohesion: 0.028)
```

**Use case**: Identifying architectural bottlenecks, refactoring opportunities.

---

## Practical Examples for Contributors

### Example 1: Before Starting a Feature

**Scenario**: You want to add a new persistence provider (e.g., MongoDB)

```bash
# Find how existing persistence providers are structured
/graphify query "How are Redis and JPA persistence providers implemented?"

# Find what interfaces to implement
/graphify path "Flow" "RedisPersistence"

# See which tests cover persistence
/graphify query "Which tests verify persistence behavior?"
```

**Result**: You understand the pattern before reading any code.

### Example 2: Code Review

**Scenario**: Reviewing a PR that changes `FlowInstance`

```bash
# See what depends on FlowInstance
/graphify query "What components depend on FlowInstance?"

# Check for god node (too many dependencies)
# Look in GRAPH_REPORT.md → God Nodes section
```

**Result**: Spot potential breaking changes, assess impact scope.

### Example 3: Documentation

**Scenario**: Writing architecture docs for the runner module

```bash
# Get overview of runner structure
/graphify query "Explain the runner module architecture and its dependencies"

# Find deployment variants
/graphify query "What are the runner deployment variants and how do they differ?"
```

**Result**: Generate accurate architecture diagrams from graph structure.

### Example 4: Debugging Cross-Module Issues

**Scenario**: LangChain4j workflows not persisting correctly

```bash
# Trace integration path
/graphify path "LangChain4jWorkflow" "PersistenceProvider"

# Find related test coverage
/graphify query "Which tests cover LangChain4j persistence integration?"
```

**Result**: Quickly identify integration points without grepping.

---

## Advanced: Cross-Repository Analysis (Optional)

**For personal analysis only - don't commit merged graphs.**

### Merging with the SDK

Explore how quarkus-flow uses the ServerlessWorkflow SDK:

```bash
# Easiest: Let graphify clone the SDK
/graphify . https://github.com/serverlessworkflow/sdk-java

# Or use your local clone
/graphify . ~/code/sdk-java
```

**Useful queries with merged graph:**

```bash
# Find quarkus-flow classes that extend SDK classes
/graphify query "Which classes extend SDK Workflow?"

# Trace integration points
/graphify path "Flow" "io.serverlessworkflow.api.Workflow"

# Impact analysis
/graphify query "What depends on SDK TaskBase?"
```

### Before Committing

Always rebuild with quarkus-flow only before committing:

```bash
# Rebuild single-repo graph
/graphify .

# Verify it's quarkus-flow only (check node count is reasonable)
head -20 .graphify/GRAPH_REPORT.md  # Check statistics summary

# Now safe to commit
git add .graphify/graph.json
```

### Working with Multiple Repos

If you regularly work with both repos, consider:

```bash
# Keep separate graphs in each repo
cd ~/quarkiverse/quarkus-flow && /graphify .
cd ~/serverlessworkflow/sdk-java && /graphify .

# Query each independently
```

This keeps graphs smaller and builds faster.

---

## Advanced: Graph Structure

### Node Types

The graph contains:

- **Code nodes**: Classes, methods, functions, interfaces
  - Example: `flow_dsl_flowdsl`, `oauth2workflow`
  - Extracted from: AST (Abstract Syntax Tree) analysis

- **Concept nodes**: High-level ideas, patterns, mechanisms
  - Example: `retry_mechanism`, `task_lifecycle`
  - Extracted from: Documentation, diagrams

- **Example nodes**: Workflow definitions, demo code
  - Example: `example_oauth2_workflow`, `chatbot_example`
  - Extracted from: YAML files, example projects

- **Image nodes**: Architecture diagrams, screenshots
  - Example: `architecture_svg`, `quarkus_flow_dashboard`
  - Extracted from: Vision analysis of images

### Edge Types

Common relationship types:

- `inherits`: Class inheritance (`OAuth2Workflow → Flow`)
- `calls`: Method invocation (`FlowInstance → FlowExecutor`)
- `references`: Type usage (`FlowConfig → PersistenceProvider`)
- `implements`: Interface implementation
- `configures`: Configuration relationship
- `conceptually_related_to`: Semantic relationship from docs

### Communities (Modules)

The graph auto-detects **385 communities** using clustering:

- **Community 0**: Dev UI and Agentic Workflows (247 nodes)
- **Community 1**: REST Resources and JSON (187 nodes)
- **Community 17**: Dashboard and Monitoring (62 nodes)
- **Community 18**: LangChain4j Agent Tools (62 nodes)

**Low cohesion score** (< 0.05) suggests a module could be split.

---

## Troubleshooting

### "Graph is empty" after update

```bash
# Check detection results
/graphify --update
# Look for "Detected 0 files" - means detection failed

# Solution: Run full rebuild
/graphify
```

### "Too many dangling edges" warning

This is expected in large codebases. Common causes:
- External dependencies (Quarkus core, Jackson, etc.)
- Generated code not in the graph
- Cross-language calls (Java ↔ JavaScript in examples)

**Not a blocker** - the graph is still usable.

### Graph takes too long to build

```bash
# First run: ~2-3 minutes (normal)
# Subsequent updates: ~5-15 seconds (normal)

# If still slow, check corpus size:
/graphify
# Look for "Detected X files · ~Y words"

# Consider narrowing scope:
/graphify core/  # Only core module
```

### Cache issues

```bash
# Clear cache if results seem stale
rm -rf .graphify/cache/
rm -f .graphify_manifest.json
/graphify --update
```

---

## Integration with CI/CD

### Automated Updates (Active)

This repository has **automated graph updates** enabled via GitHub Actions.

**How it works:**
- Every push to `main` triggers `.github/workflows/update-graph.yml`
- The workflow runs `/graphify --update` (incremental, uses cache)
- If the graph changes, it's automatically committed and pushed
- The commit includes `[skip ci]` to avoid triggering other workflows

**What this means for contributors:**

✅ **You don't need to update the graph in your PR** - it will update automatically when merged
  
✅ **Optional: Include graph updates in your PR** if you want reviewers to see architectural impact

```bash
# Optional: Update graph before pushing PR
/graphify --update
git add .graphify/graph.json .graphify/GRAPH_REPORT.md
git commit -m "feat: add MongoDB persistence + update graph"
```

**Workflow triggers:**
- Pushes to `main` that change `.java`, `.yaml`, `.yml`, `.adoc`, `.md`, `.png`, `.jpg`, `.svg` files
- Manual trigger via GitHub Actions UI (workflow_dispatch)

**Performance:**
- First run after setup: ~2-3 minutes (full build)
- Typical incremental update: ~15-30 seconds (only changed files)
- Cache is preserved between runs for efficiency

### Manual Updates (Fallback)

If the automated workflow is disabled or you want to update locally:

```bash
# Update the graph
/graphify --update

# Commit the changes
git add .graphify/graph.json .graphify/GRAPH_REPORT.md
git add .graphify/cache/ .graphify_manifest.json
git commit -m "chore: update knowledge graph"
```

---

## Token Economics

**Why this saves time and AI costs:**

| Approach | Tokens per Query | Use Case |
|----------|------------------|----------|
| **Read all files** | 50,000+ | Impossible for large questions |
| **RAG (embeddings)** | 5,000-20,000 | Semantic search, may miss structure |
| **Knowledge Graph** | 500-2,000 | Structural queries, exact relationships |

**Example**: "What modules depend on persistence?"
- Without graph: Read 30+ files (15,000 tokens)
- With graph: Query traversal (800 tokens)

After **~25 queries**, the upfront graph build cost is recovered.

---

## Resources

- **Graphify Documentation**: See `.claude/skills/graphify/SKILL.md` in this repo
- **Graph Query Syntax**: [Graphify Query Reference](https://github.com/safishamsi/graphify)
- **Graph Report**: `.graphify/GRAPH_REPORT.md` (updated with each build)

---

## Questions?

- **For graph usage questions**: Open a discussion in GitHub Discussions
- **For graph bugs/issues**: Check if graphify needs updating (`pip install -U graphifyy`)
- **For contributing to the graph structure**: See CONTRIBUTING.md

---

**Last updated**: Graph statistics as of the last build (see `.graphify/GRAPH_REPORT.md` for current stats)
