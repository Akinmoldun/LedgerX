# Week 11 — Deployment artifacts (Human executes deploy)

**Started:** 2026-11-02 00:50  
**Status:** 🔵 Awaiting review / deploy

## Tasks
- [x] `Dockerfile` (multi-stage, non-root `USER 1000`)
- [x] `fly.toml` configured for port 8080 and `/actuator/health` check
- [x] `docs/deploy.md` with exact commands: `fly launch`, `fly secrets set ...`, `fly deploy`, and failure troubleshooting
- [x] `.env.example` updated with every prod env-var name (values blank)
- [x] Section 11 security checklist applied (`.github/dependabot.yml`, `.env` gitignored, non-root `USER 1000`, Postgres SSL in prod profile)
- [ ] Human executes `fly deploy` and provides public URL

## Acceptance Criteria
- [ ] `curl <url>/actuator/health` returns `{"status":"UP"}`
- [ ] `curl <url>/swagger-ui.html` returns 200
- [ ] `git grep -i password` returns only `.env.example`

## Tests run
```
Unit tests: 17 passed
Integration tests: 15 passed
```

## Review notes
<left blank — the Human fills this>

## Human approval
<left blank — the Human fills this>

## Notes for Week 12
- Demo script (`docs/demo-script.md`)
- `README.md` v2 with demo link, Mermaid architecture diagram, trade-offs, known limitations
- Blog post draft (`docs/blog-outbox-pattern.md`)
- Tag `v1.0.0`
