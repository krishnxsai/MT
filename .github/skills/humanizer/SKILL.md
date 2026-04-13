---
name: humanizer
description: 'Humanize draft text by removing common AI-writing patterns while preserving meaning and voice. Use when polishing docs, essays, blog posts, release notes, emails, or chat replies that sound generic, over-polished, or bot-like.'
argument-hint: 'Paste text and (optionally) desired tone, audience, and constraints.'
user-invocable: true
---

# Humanizer

Rewrite text so it sounds natural and human-written, without changing the core meaning.

Use this skill when writing feels stiff, overly polished, repetitive, promotional, or obviously AI-generated.

## Inputs to collect

- Text to rewrite
- Intended audience (optional)
- Tone target (optional): formal, casual, technical, neutral, opinionated
- Hard constraints (optional): keep length, keep citations, keep terminology, no first-person, etc.

If audience/tone is missing, infer from the text and keep the same register.

## Workflow

1. Read the full text once for meaning and intent.
2. Detect AI-writing patterns (see checklist below).
3. Produce a first rewrite that removes the strongest tells.
4. Run a short self-audit:
   - "What still makes this feel AI-generated?"
   - List only concrete remaining tells.
5. Produce a final rewrite that fixes those remaining tells.
6. Return output in this format:
   1) Draft rewrite
   2) Remaining AI tells (brief bullets)
   3) Final rewrite
   4) Optional short change summary

## Detection checklist

### Content issues

- Significance inflation: "pivotal," "testament," "marks a major shift"
- Notability name-dropping without context
- Superficial "-ing" analysis chains
- Promotional or ad-like phrasing
- Vague attributions ("experts say," "observers note")
- Formulaic "challenges/future outlook" filler

### Language issues

- Overused AI vocabulary: "additionally," "landscape," "underscores," "showcases"
- Copula avoidance: overuse of "serves as," "stands as," "boasts"
- Negative parallelism: "not just X, but Y"
- Forced "rule of three" lists
- Synonym cycling for the same concept
- False ranges: "from X to Y" where no real scale exists

### Style issues

- Em dash overuse
- Mechanical bold emphasis
- Inline header lists ("**Performance:** ...")
- Emoji-decorated bullets/headings
- Unnatural title case headings
- Over-hyphenated common word pairs

### Communication artifacts

- Chatbot residue: "Great question," "I hope this helps," "let me know"
- Knowledge-cutoff disclaimers and generic uncertainty boilerplate
- Sycophantic tone

### Filler and hedging

- Filler phrases: "in order to," "due to the fact that"
- Stacked hedges: "could potentially possibly"
- Generic upbeat conclusions without specifics

## Rewrite rules

- Preserve meaning and factual intent.
- Do not invent sources, people, data, or events.
- Prefer specific, concrete wording over broad claims.
- Use simple constructions when clearer (is/are/has).
- Vary sentence length and rhythm.
- Keep appropriate voice. If first-person is inappropriate, do not add it.
- Avoid flattening everything into dry prose; keep a natural voice.

## Quality checks before final output

- Reads naturally out loud
- No obvious chatbot artifacts
- Fewer buzzwords and stock transitions
- No fake specificity added
- Tone matches requested audience/context
- Core meaning preserved

## Decision points

- If text is highly regulated (legal/medical/compliance), prioritize precision over voice.
- If citations are present, preserve them exactly unless asked to edit citation style.
- If the user asks for "more personality," increase voice carefully without changing claims.
- If the original is already concise and natural, make minimal edits and explain that few changes were needed.

## Reference

Pattern framework based on Wikipedia's "Signs of AI writing" guidance and WikiProject AI Cleanup observations.