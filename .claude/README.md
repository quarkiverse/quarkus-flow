# Claude Code Configuration

This directory contains configuration files for [Claude Code](https://claude.ai/code), an AI-powered coding assistant.

## Files

- **`settings.json`**: Hooks and automation configuration
- **`CLAUDE.md`** (or `../CLAUDE.md`): Project guidelines and conventions loaded at every Claude Code session start

## For Contributors

If you're using Claude Code to contribute to this project:

1. **CLAUDE.md is automatically loaded** - it contains our project conventions and workflows
2. **Hooks enforce quality gates** - they prevent common mistakes like creating PRs without passing tests
3. **You can add personal preferences** in `.claude/settings.local.json` (gitignored, not committed)

**Settings hierarchy**:
- `.claude/settings.json` - Project settings (committed to Git, shared with team)
- `.claude/settings.local.json` - Your personal project overrides (gitignored)
- `~/.claude/settings.json` - Your global settings (applies to all projects)

## For Maintainers

To add new hooks or modify existing ones, edit `settings.json`. Common hook types:

- `PreToolUse`: Run before Claude executes a tool (e.g., before editing files, running commands)
- `PostToolUse`: Run after Claude executes a tool (e.g., auto-format after edits)
- `PermissionRequest`: Auto-approve or deny specific actions

See the [Claude Code Hooks Guide](https://code.claude.com/docs/en/hooks-guide.md) for more details.

## Testing Hooks

To verify hooks are working:

1. Start a Claude Code session: `claude`
2. Run `/hooks` to see active hooks
3. Try the action that should trigger the hook (e.g., attempt PR creation)

## Disabling Hooks (Emergency Override)

If you need to bypass hooks temporarily:

```bash
# Use environment variable
CLAUDE_DISABLE_HOOKS=true claude

# Or edit settings.json and set "hooksEnabled": false
```

Use this sparingly - hooks exist to maintain code quality!
