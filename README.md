# SpruceSettings

Free per-player settings plugin for Paper **26.2** / **Java 25**. Players open
`/settings` and flip toggles that affect only themselves.

**Other plugins can add their own toggles** through a small public API — that
is the point of this plugin as much as the built-in toggles are.

**Not yet published to any marketplace.**

## Requirements

- Paper 26.2 on Java 25
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) —
  optional, only needed for the placeholders below

No economy, no database server, no hard dependencies.

## Built-in toggles

| Toggle | What it does |
|---|---|
| Hide public chat | Removes you from the recipient list of public chat. You still see your own messages, private messages, and staff announcements. |
| Hide other players | Hides every other player from view. Useful on laggy clients and busy hubs. |
| Hide join/leave messages | Stops join and leave broadcasts reaching you. |
| PvP protection | Stops other players damaging you, and you damaging them. **Off by default, and gated — see below.** |

### The PvP protection combat gate

PvP protection cannot be switched **on** for
`pvp-opt-out.combat-window-seconds` (default 15) after you last took player
damage. Without that, a losing player opens the menu mid-fight and walks away
untouched.

Switching it **off** is never blocked — that only makes you more vulnerable, so
there is nothing to exploit.

Servers that consider the whole feature game-breaking set
`pvp-opt-out.enabled: false`, which removes the toggle from the GUI entirely
rather than merely permission-gating it. `/settingsadmin reload` applies that
immediately.

## Commands & permissions

| Command | Permission | Notes |
|---|---|---|
| `/settings` (aliases `/prefs`, `/options`) | `sprucesettings.use` (default: true) | Opens the toggle GUI |
| `/settingsadmin reload` | `sprucesettings.admin` (default: op) | Reloads config.yml + messages.yml |
| `/settingsadmin reset <player>` | `sprucesettings.admin` | Clears all stored settings for an online player |

## Placeholders (PlaceholderAPI)

Colons are not legal in placeholder syntax, so the first underscore stands in
for the `:` in a toggle key:

```
%sprucesettings_settings_hide-players%          -> true / false
%sprucesettings_pretty_settings_hide-players%   -> the configured on/off wording
```

This resolves dynamically, so **toggles registered by other plugins get
placeholders for free** — `%sprucesettings_sprucebounty_broadcasts%` works as
soon as SpruceBounty registers its toggle.

## Developer API

Add a toggle from your own plugin and it appears in `/settings` automatically.

### Option A — typed (needs a compileOnly dependency on this plugin)

```java
// plugin.yml:  softdepend: [SpruceSettings]

// Check by NAME first, in a different method from the code that touches
// SpruceSettings types. Bytecode verification is lazy per-METHOD, not
// per-branch: an early return in the same method is NOT enough and still
// throws NoClassDefFoundError when SpruceSettings is absent.
if (getServer().getPluginManager().getPlugin("SpruceSettings") != null) {
    hookSettings();
}

private void hookSettings() {
    RegisteredServiceProvider<SpruceSettingsAPI> rsp =
            getServer().getServicesManager().getRegistration(SpruceSettingsAPI.class);
    if (rsp == null) {
        return;
    }
    SpruceSettingsAPI api = rsp.getProvider();
    api.register(SettingToggle.builder("myplugin:feature")
            .displayName("<green>My feature")
            .description(List.of("<gray>Turn my feature on or off."))
            .icon(Material.LEVER)
            .defaultValue(true)
            .build());
}
```

Then read it anywhere: `api.isEnabled(player.getUniqueId(), "myplugin:feature")`.

### Option B — reflection (zero dependency of any kind)

`registerSimple(...)` takes only JDK types precisely so you can call it through
`Method.invoke` without ever loading our classes. No Maven coordinate, no
shaded jar, no version skew. This is how SpruceBounty hooks in — see
`SettingsHook.java` in that repo for a complete working example.

```java
Class<?> apiClass = Class.forName("dev.spruceworks.settings.api.SpruceSettingsAPI");
Object api = Bukkit.getServicesManager().getRegistration(apiClass).getProvider();
apiClass.getMethod("registerSimple", String.class, String.class, List.class,
                   String.class, boolean.class, String.class)
        .invoke(api, "myplugin:feature", "<green>My feature",
                List.of("<gray>Turn my feature on or off."), "LEVER", true, null);
```

### Rules

- Keys must be namespaced `owner:name` and lowercase. The `settings:` namespace
  is reserved for this plugin's own toggles.
- Registering the same key twice replaces the definition — safe across reloads.
- `isEnabled` returns the toggle's default for a player who has never changed
  it, and `false` for an unknown key, so a registration that silently failed
  still behaves predictably instead of throwing.
- Registrations live for the session. Re-register in your `onEnable`.

## Non-goals

- **Client-side particle and sound master toggles.** The server cannot control
  those. A toggle that silently does nothing is worse than no toggle, so these
  will not be added.
- Blocking other plugins' commands beyond our own API surface.
- Any premium tier.

## Usage

```console
./gradlew build        # plugin jar → build/libs/spruce-settings-<version>.jar (shaded)
./gradlew runServer    # boot a local Paper 26.2 test server with the plugin
./gradlew test         # unit tests (combat gate, storage persistence)
```

The `run/` directory is disposable and gitignored.

## Release checklist

1. Bump `version` in `build.gradle.kts` (semantic versioning).
2. Update `CHANGELOG.md`.
3. bStats service id is registered and compiled in (`32935`). Server owners can
   opt out with `metrics: false` in `config.yml`.
4. `./gradlew build`, then boot on the latest Paper build and check the console.
5. Verify `git ls-files -s gradlew` reads `100755`, not `100644`.
6. Strategy-session code review before any marketplace listing.
