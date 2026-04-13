# Humanizer Skill

A VS Code Copilot skill for rewriting text so it sounds natural and human-written while preserving meaning.

## Location

This skill is workspace-scoped and lives at:

- `.github/skills/humanizer/SKILL.md`

## What it does

The skill removes common AI-writing patterns and returns a four-part result:

1. Draft rewrite
2. Remaining AI tells (brief bullets)
3. Final rewrite
4. Optional short change summary

Default behavior is **balanced**: preserve structure, remove obvious AI tells.

## When to use

Use this skill when text sounds:

- generic or over-polished
- repetitive or promotional
- bot-like in rhythm or phrasing
- filled with hedge/filler language

Typical inputs: docs, release notes, emails, essays, chat replies.

## Invocation

Use the slash command in chat:

- `/humanizer`

Then provide text, and optionally include audience/tone constraints.

## Authoring notes

- `SKILL.md` is the source of truth for behavior.
- Keep this `README.md` aligned with workflow/output changes.
- If output format or defaults change, update both files in the same edit.

## Suggested prompts

- `Humanize this release note for engineers. Keep terminology and length.`
- `Rewrite this email to sound less AI-generated and more direct.`
- `Humanize this essay in a formal tone. Keep citations unchanged.`

## Changelog

- `1.0.0` Initial workspace skill docs for humanizer (README + WARP companion docs).