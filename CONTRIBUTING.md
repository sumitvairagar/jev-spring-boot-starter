# Contributing

## Before you start

Read `AGENTS.md` — it has everything: build commands, module structure, conventions, and what not to do.

## Pull request checklist

- [ ] `mvn test` passes with no failures
- [ ] New public API has Javadoc
- [ ] New behaviour has a test
- [ ] No new dependencies added to `jev-client` (must stay zero-dependency)
- [ ] `AGENTS.md` updated if you added a new module, changed the stack, or changed conventions

## Reporting issues

Include: Java version (`java --version`), Spring Boot version, what you expected, what happened.
