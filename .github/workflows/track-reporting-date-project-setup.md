# GitHub Project Setup Guide

## Project

**URL:** `https://github.com/orgs/quarkiverse/projects/11`

---

## Required fields

The workflow reads and writes specific fields on each project item. All field names are **case-sensitive** and must match exactly.

### Tracked fields (read by the workflow)

Changes to any of these fields trigger an update to `Reporting Date` and a new entry in `Reporting Log`.

| Field name       | Type          | Notes                                      |
|------------------|---------------|--------------------------------------------|
| `Status`         | Single select | e.g. Backlog, In Progress, Done            |
| `Priority`       | Single select | e.g. Low, Medium, High                     |
| `Estimate`       | Number        | Estimated effort or story points           |
| `Remaining Work` | Number        | Remaining effort                           |
| `Time Spent`     | Number        | Time already spent                         |

### Workflow-managed fields (written by the workflow)

These fields are updated automatically and should not be edited manually.

| Field name       | Type   | Purpose                                                       |
|------------------|--------|---------------------------------------------------------------|
| `Reporting Date` | Date   | Set to today whenever a tracked field changes                 |
| `Reporting Log`  | Text   | Log of changes, newest entry first, max 5 entries             |

#### Reporting Log entry format

Entries are separated by ` | `, ordered **newest first**. Field values within each entry are separated by `, `:

```
DATE, Status, Priority, Estimate, Remaining Work, Time Spent
```

Multiple entries example (newest → oldest, max 5):
```
2026-03-03, In Progress, High, 8, 5, 3 | 2026-03-01, Backlog, High, 8, 8, 0
```

The oldest entry is automatically discarded when the log exceeds 5 entries.

---

## How to add a field to the project

1. Go to **`https://github.com/orgs/quarkiverse/projects/11`**
2. Click **"+"** at the right end of the column headers → **"New field"**
3. Enter the field name and select the correct type
4. Click **"Save"**

