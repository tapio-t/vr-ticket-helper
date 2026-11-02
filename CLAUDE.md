# VR Ticket Helper

Personal Android app: compares VR train fares for a return trip and hands the chosen trains to vr.fi's own booking, opened on the seat selection. The user picks seats and pays on vr.fi.

The full specification and the security requirements are in `CLAUDE.local.md` (on the owner's computer only, not in git). Read it before starting work, and follow its security requirements as hard rules.

## Stack

- Kotlin, Jetpack Compose, Material 3
- Android 16, target SDK 36
- Android WebView with AndroidX WebKit
- Jetpack DataStore for settings
- JUnit for unit tests

## Commands

- Build: `./gradlew assembleDebug` (Windows: `gradlew.bat assembleDebug`)
- Unit tests: `./gradlew test`
- Install on the connected phone: `./gradlew installDebug`

## Rules for this repository

This repository is private but may be made public later. Everything committed must be safe to publish:

- Never commit secrets, signing keys, keystores, tokens, `local.properties` or personal data.
- Commit as `tapio-t <tapio-t@users.noreply.github.com>`. Check `git config user.email` before the first commit of a session.
- README, repository description, commit messages and code comments must not describe security mechanisms, their limits, or how the app talks to vr.fi. Keep comments to what a maintainer needs.
- Keep everything specific to vr.fi (addresses, parameters, page markers, page wording) in one file, so site changes are fixed in one place.
- Add no analytics, crash-reporting or tracking libraries.
- Use only libraries from Google Maven and Maven Central, with pinned versions.

## Working style

- Work in the phases from `CLAUDE.local.md`; keep each phase buildable.
- Write unit tests for ranking, date handling and parsing, using saved sample data with no personal details.
- Never automate summary, confirmation or payment steps on vr.fi.
