# Lessons

## Context-low handoff must be proactive

**Pattern:** When Hien says the session is close to context limit, the agent must not keep doing new implementation work or leave important PR/context details only in chat.

**Rule:** At ~75% context usage, or when Hien mentions context is nearly full, immediately preserve continuity: review current status, update/create the next session note, include phase status, save coder/reviewer prompts under `docs/`, and provide a copy-paste new-session prompt.
