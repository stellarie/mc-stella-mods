# mc-stella-mods - Claude instructions

`AGENTS.md` holds the standing instructions for this repository. Follow it.

## Commit identity

Author and commit every change as
`stellarie <76443517+stellarie@users.noreply.github.com>`.
This repository is public. Use only the GitHub name and the GitHub noreply email.

- Never put a real name or a personal email in an author line or a committer line.
- Never name an agent or a model as author or committer. This includes Claude,
  Codex, DeepSeek, Chloe, Sherry, and Yuu.
- Never add a `Co-Authored-By` trailer for an agent or a model.
- Never add a `Generated with` footer or a session link, such as `Claude-Session:`.
- A local `user.name` or `user.email` in the repository config overrides the
  global identity. Remove that override.
- After each commit, check the identity:
  `git log -1 --format='%an <%ae> | %cn <%ce>'`.
