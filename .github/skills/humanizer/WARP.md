# WARP.md

This file provides guidance for tools/agents working inside this skill folder.

## What this folder is

This directory contains a **VS Code Copilot skill**.

Primary files:

- `SKILL.md` - runtime skill definition used by the agent
- `README.md` - human-facing usage and maintenance notes
- `WARP.md` - maintenance guidance for this folder

## Source of truth

Treat `SKILL.md` as canonical behavior.
If wording in `README.md` conflicts with `SKILL.md`, update `README.md`.

## Safe edit rules

1. Preserve YAML frontmatter validity in `SKILL.md`.
2. Keep `name: humanizer` aligned with the folder name.
3. Do not remove the four-part output contract unless intentionally changing behavior.
4. Keep default mode as balanced unless asked otherwise.
5. When behavior changes, update `README.md` in the same change.

## Quick maintenance checklist

- Frontmatter parses correctly
- Description still includes trigger keywords
- Workflow steps remain explicit and actionable
- Output format section is still accurate
- README examples match current behavior

## Notes

This is a workspace-scoped skill under `.github/skills/humanizer/`. For personal scope, copy the same skill structure to a personal skills directory and keep behavior identical unless intentionally diverging.