# Configurable Timestamp Format for Structured Logging

**Date:** 2026-04-21  
**Issue:** #473  
**Status:** Design Approved

## Problem

Quarkus Flow structured logging currently emits timestamps in ISO 8601 format exclusively. Different log processors have varying timestamp parsing capabilities:

- **FluentBit's pgsql output plugin**: Expects Unix epoch timestamps for `TIMESTAMP WITH TIME ZONE` columns
- **Elasticsearch/OpenSearch**: Works with ISO 8601 but also accepts epoch milliseconds
- **Custom processors**: May have specific format requirements

This creates friction when using logs-as-transport patterns to route workflow events to databases or analytics platforms via log forwarders.

## Solution

Add configuration to control timestamp format in structured logging events, supporting:
- ISO 8601 (default, backward compatible)
- Unix epoch variants (seconds, milliseconds, nanoseconds)
- Custom DateTimeFormatter patterns

## Design

### Configuration

Add two new properties to `FlowStructuredLoggingConfig`:

```java
/**
 * Timestamp format options for structured logging events.
 */
enum TimestampFormat {
    /** ISO 8601 format with nanosecond precision (e.g., "2026-04-21T21:36:06.427832969Z") */
    ISO8601,
    /** Unix epoch as double with fractional seconds (e.g., 1776807366.427833) */
    EPOCH_SECONDS,
    /** Unix epoch milliseconds as long (e.g., 1776807366428) */
    EPOCH_MILLIS,
    /** Unix epoch nanoseconds as long (e.g., 1776807366427832969) */
    EPOCH_NANOS,
    /** Custom pattern using DateTimeFormatter */
    CUSTOM
}

/**
 * Timestamp format for structured logging events.
 * Controls the format of all timestamp fields (timestamp, startTime, endTime, lastUpdateTime).
 * Default: iso8601
 */
@WithDefault("iso8601")
TimestampFormat timestampFormat();

/**
 * Custom timestamp pattern for DateTimeFormatter.
 * Only used when timestampFormat() is CUSTOM.
 * Example: yyyy-MM-dd'T'HH:mm:ss.SSSXXX
 * 
 * If timestamp-format is CUSTOM and this is not set or invalid,
 * the application will fail at startup.
 */
Optional<String> timestampPattern();
```

**User-facing configuration:**
```properties
# Default: ISO 8601 (backward compatible)
quarkus.flow.structured-logging.timestamp-format=iso8601

# Unix epoch with fractional seconds (PostgreSQL)
quarkus.flow.structured-logging.timestamp-format=epoch-seconds

# Unix epoch milliseconds (Elasticsearch)
quarkus.flow.structured-logging.timestamp-format=epoch-millis

# Unix epoch nanoseconds (high-precision time-series)
quarkus.flow.structured-logging.timestamp-format=epoch-nanos

# Custom format
quarkus.flow.structured-logging.timestamp-format=custom
quarkus.flow.structured-logging.timestamp-pattern=yyyy-MM-dd'T'HH:mm:ss.SSSXXX
```

### EventFormatter Changes

**1. Constructor validation:**

Validate custom patterns at construction time (fail-fast):

```java
public EventFormatter(FlowStructuredLoggingConfig config, ObjectMapper objectMapper) {
    this.config = config;
    this.objectMapper = objectMapper;
    
    // Validate custom pattern if CUSTOM format is selected
    if (config.timestampFormat() == TimestampFormat.CUSTOM) {
        if (config.timestampPattern().isEmpty()) {
            throw new IllegalArgumentException(
                "quarkus.flow.structured-logging.timestamp-pattern must be set when timestamp-format is 'custom'"
            );
        }
        try {
            DateTimeFormatter.ofPattern(config.timestampPattern().get());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid timestamp pattern '" + config.timestampPattern().get() + "': " + e.getMessage(),
                e
            );
        }
    }
}
```

**2. Centralized formatting method:**

```java
private Object formatTimestamp(OffsetDateTime timestamp) {
    if (timestamp == null) {
        return null;
    }
    
    switch (config.timestampFormat()) {
        case ISO8601:
            return timestamp.toString();
            
        case EPOCH_SECONDS:
            Instant instant = timestamp.toInstant();
            double seconds = instant.getEpochSecond() + (instant.getNano() / 1_000_000_000.0);
            return seconds;
            
        case EPOCH_MILLIS:
            return timestamp.toInstant().toEpochMilli();
            
        case EPOCH_NANOS:
            Instant inst = timestamp.toInstant();
            return inst.getEpochSecond() * 1_000_000_000L + inst.getNano();
            
        case CUSTOM:
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                config.timestampPattern().get()
            );
            return timestamp.format(formatter);
            
        default:
            return timestamp.toString();
    }
}
```

**3. Update timestamp field assignments:**

Replace all direct `event.eventDate()` calls with `formatTimestamp(event.eventDate())`.

**Affected methods:**
- `formatWorkflowStarted()` - timestamp, startTime
- `formatWorkflowCompleted()` - timestamp, endTime
- `formatWorkflowFailed()` - timestamp, endTime
- `formatWorkflowCancelled()` - timestamp, endTime
- `formatWorkflowSuspended()` - timestamp
- `formatWorkflowResumed()` - timestamp
- `formatWorkflowStatusChanged()` - timestamp, lastUpdateTime
- `formatTaskStarted()` - timestamp, startTime
- `formatTaskCompleted()` - timestamp, endTime
- `formatTaskFailed()` - timestamp, endTime
- `formatTaskCancelled()` - timestamp, endTime
- `formatTaskSuspended()` - timestamp
- `formatTaskResumed()` - timestamp
- `formatTaskRetried()` - timestamp

## Testing

### Unit Tests (New: TimestampFormatterTest.java)

```java
@ParameterizedTest
@EnumSource(TimestampFormat.class)
void testTimestampFormat(TimestampFormat format)

@Test
void testEpochSecondsPreservesNanosecondPrecision()

@Test
void testCustomPatternValidation()

@Test
void testCustomPatternRequired()

@Test
void testNullTimestampHandling()
```

### Integration Tests (Extend: StructuredLoggingTest.java)

```java
@Test
void testEpochSecondsFormat()

@Test
void testEpochMillisFormat()

@Test
void testCustomPatternFormat()
```

### Existing Tests

Update existing unit tests to ensure default (ISO8601) behavior is unchanged.

## Documentation

Update `docs/modules/ROOT/pages/structured-logging.adoc`:

1. **New section:** "Timestamp Format" with configuration examples and format descriptions
2. **Update FluentBit example:** Show epoch-seconds usage for PostgreSQL
3. **Update event schema examples:** Show different timestamp formats

## Backward Compatibility

**No breaking changes:**
- Default format is `iso8601`
- Existing deployments see no behavior change
- New configuration is purely opt-in

## Implementation Notes

**Precision handling:**
- ISO8601: Nanosecond precision preserved in string
- EPOCH_SECONDS: Double with fractional part (nanosecond precision)
- EPOCH_MILLIS: Long (millisecond precision)
- EPOCH_NANOS: Long (nanosecond precision)
- CUSTOM: Depends on user pattern

**Validation:**
- CUSTOM format without pattern → IllegalArgumentException at startup
- Invalid DateTimeFormatter pattern → IllegalArgumentException at startup
- All other formats require no additional validation

**Performance:**
- Switch statement on enum is efficient (JVM optimizes)
- No additional object allocation for numeric formats
- Custom pattern creates DateTimeFormatter on each call (acceptable for logging use case)

## Why This Approach?

**Centralized formatting method** (chosen over Strategy pattern or Enum with behavior):

**Pros:**
- Simple, minimal code changes
- Single responsibility: EventFormatter handles all event formatting
- Easy to test and maintain
- Pattern validation at construction time (fail-fast)

**Cons:**
- EventFormatter slightly larger (but still cohesive)

**Rejected alternatives:**
- Strategy pattern: Too much indirection for 5 fixed formats
- Enum with behavior: Awkward handling of CUSTOM pattern with state

## Use Case Example: FluentBit → PostgreSQL

**Problem:**
```
ERROR: invalid input syntax for type double precision: "2026-04-21T21:36:06.427832969Z"
```

**Solution:**
```properties
quarkus.flow.structured-logging.timestamp-format=epoch-seconds
```

**Result:**
```json
{
  "timestamp": 1776807366.427833,
  "startTime": 1776807366.427833
}
```

FluentBit can now insert directly into PostgreSQL `TIMESTAMP WITH TIME ZONE` columns without Lua preprocessing.
