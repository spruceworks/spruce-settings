# Changelog

All notable changes to this project are documented in this file. The format is
based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Initial v1.0 implementation per SPEC-settings.md: `/settings` GUI with
  per-player toggles stored in SQLite behind a versioned storage interface,
  and `/settingsadmin reload|reset`.
- Four built-in toggles: hide public chat, hide other players (re-applied in
  both directions on every join so it survives rejoin), hide join/leave
  messages, and PvP opt-out behind a config master switch.
- Combat gate on PvP opt-out: protection cannot be enabled within
  `pvp-opt-out.combat-window-seconds` of taking player damage. Disabling is
  never gated, since it can only make a player more vulnerable. 14 unit tests
  cover the boundary conditions, including an invariant sweep asserting that
  "blocked" always implies "remaining > 0".
- **Public toggle-registration API** (`dev.spruceworks.settings.api`),
  published through Bukkit's ServicesManager and deliberately left
  unrelocated in the shaded jar. Two ways in: a typed `register(SettingToggle)`
  for callers happy to add a `compileOnly` dependency, and
  `registerSimple(...)` taking only JDK types so a caller can register through
  reflection with no dependency at all.
- PlaceholderAPI expansion resolving dynamically, so toggles registered by
  other plugins get placeholders without extra wiring.
- 22 unit tests: combat gate boundaries plus real-SQLite persistence
  (close/reopen round-trip, upsert-not-duplicate, per-player isolation).

### Notes

- bStats service id `32935`, relocated to `dev.spruceworks.settings.libs.bstats`.
  Server owners can opt out with `metrics: false` in `config.yml`.
