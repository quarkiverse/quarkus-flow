# track-reporting-date workflow — Testing Guide

## Prerequisites

Make sure the setup from the guide is complete:
- `GH_TOKEN` secret is set in the repository (PAT with `project` and `read:org` scopes)
- The project (`https://github.com/orgs/quarkiverse/projects/11`) has both a **`Reporting Date`** (Date) and a **`Reporting Log`** (Text) field

---

## Testing steps

1. **Go to your project** → `https://github.com/orgs/quarkiverse/projects/11`

2. **Pick any issue/item** in the project and change one of the tracked fields:
   - Status, Priority, Estimate, Remaining Work, or Time Spent

3. **Wait until 05:00 UTC** for the scheduled workflow to trigger, or use the manual trigger below to run it immediately

4. **Check the workflow ran** → go to your repository → **Actions** tab → open the latest run of `Track Reporting Date on Field Changes` and inspect the logs. You should see the item listed with a change detected and an update confirmation.

5. **Verify the result** → go back to the project item and confirm:
   - `Reporting Date` is set to today
   - `Reporting Log` has a new entry prepended in the format `YYYY-MM-DD, Status, Priority, Estimate, Remaining Work, Time Spent`, separated from older entries by ` | `, with a maximum of 5 entries total

---

## Manual trigger (skip the scheduled wait)

1. Go to **Actions → Track Reporting Date on Field Changes → Run workflow**
2. Click **"Run workflow"**
3. The workflow runs immediately against the latest project state

---

## Negative test (optional)

Change a field that is **not** in the tracked list (e.g. title or assignee). After the next workflow run, the logs should show the item was processed but skipped with `No change detected. Skipping.`

---

## Troubleshooting

- **Workflow fails with auth error** → the `GH_TOKEN` secret is missing or the PAT doesn't have `project` and `read:org` scopes
- **`Reporting Date` field not found** → the field name in the project doesn't exactly match `Reporting Date` (case-sensitive)
- **`Reporting Log` field not found** → the field name in the project doesn't exactly match `Reporting Log` (case-sensitive), or the field hasn't been created yet
- **Item not processed** → the item may not appear in the first 100 results; increase the `items(first: 100)` limit in the workflow if the project has more than 100 items
