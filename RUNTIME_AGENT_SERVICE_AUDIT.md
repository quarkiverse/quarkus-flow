# Runtime Agent Service Override Audit

## Missing Method Overrides

### RuntimeFlowConditionalAgentService

**Currently have:**
- ✅ `subAgents(Predicate<AgenticScope>, Object...)`
- ✅ `subAgents(Predicate<AgenticScope>, Collection<?>)` - JUST ADDED

**Missing:**
- ❌ `subAgents(Object...)` - no predicate variant (defaults to always-true)
- ❌ `subAgents(Collection<?>)` - collection variant, no predicate
- ❌ `subAgents(String conditionDescription, Predicate<AgenticScope>, Object...)`
- ❌ `subAgents(Predicate<AgenticScope>, List<AgentExecutor>)` - internal, can skip
- ❌ `subAgents(String conditionDescription, Predicate<AgenticScope>, List<AgentExecutor>)` - internal, can skip

**Action:** Add the non-predicate variants and description variants

---

### RuntimeFlowLoopAgentService

**Currently have:**
- ✅ `subAgents(Object...)`
- ✅ `subAgents(Collection<?>)`
- ✅ `maxIterations(int)`
- ✅ `exitCondition(BiPredicate<AgenticScope, Integer>)`
- ✅ `testExitAtLoopEnd(boolean)`

**Missing:**
- ❌ `exitCondition(Predicate<AgenticScope>)` - simple predicate, no loop counter
- ❌ `exitCondition(String description, Predicate<AgenticScope>)` - with description
- ❌ `exitCondition(String description, BiPredicate<AgenticScope, Integer>)` - with description

**Action:** Add all exitCondition variants

---

### RuntimeFlowParallelAgentService

**Currently have:**
- ✅ `subAgents(Object...)`
- ✅ `subAgents(Collection<?>)`

**Missing:** *(checking...)*

---

### RuntimeFlowSequentialAgentService

**Currently have:**
- ✅ `subAgents(Object...)`
- ✅ `subAgents(Collection<?>)`

**Missing:** *(checking...)*

---

## Recommendations

1. **Override ALL public API methods** that configure the agent flow
2. **Skip internal API methods** (those dealing with `AgentExecutor` directly)
3. **Description variants**: May not be critical for runtime usage, but should be added for completeness
4. **Priority**:
   - HIGH: Non-predicate variants (users might call these)
   - MEDIUM: Predicate-only variants (already have BiPredicate)
   - LOW: Description variants (nice to have)
