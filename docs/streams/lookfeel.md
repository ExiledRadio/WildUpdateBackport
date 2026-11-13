# Stream Lf: 1.19.x look and feel

Package `lookfeel` (client part in `lookfeel.client`), patches in `asm` (the mod's first coremod, `WildUpdateCore`).
No config category: the options are Backport Core's `presentation` options, saved by Core. No blocks, items, entities
or packets (ids 75-79 are claimed and unused). Dev-server port 25875. Also changed outside the package: one line each
in the mod class's pre-init, `ClientSetup.init` and `SelfTestRunner.TESTS`; the coremod in `build.gradle` (manifest
and `fml.coreMods.load`); `docs/PATCHES.md` (new); `docs/CORE_REQUESTS.md` Lf-1 to Lf-4.

Assets come from `tools/lookfeel/generate.py <new download folder>` (Mojang's 1.18.2, 1.19, 1.19.2 and 1.19.4).

## For the integrator

1.  **The coremod is new.** `asm.WildUpdateCore` is named in the jar manifest and in the run tasks. Stream C's planned
    patches and any Darkness lightmap patch (W) go into this coremod: their own transformer class in
    `getASMTransformerClass`, their rows in `docs/PATCHES.md` from P4 on.
2.  **Darkness Pulsing is registered here, the warden stream applies it.** 1.19's Darkness effect should scale its
    pulse by `LookAndFeel.darknessScale()` (0 to 1, default 1; 1.19.4's `darknessEffectScale().get()`), or read the
    option by id: `Presentation.option("wildupdatebackport:darkness_effect_scale")`.
3.  **The swamp music files are here.** `assets/wildupdatebackport/sounds/music/game/swamp/{aerie,firebugs,labyrinthine}.ogg`
    (49 MB of the jar). Stream Sw's swamp music (1.19's `music.overworld.swamp`) should name these files, not ship
    copies. No sound event names them yet: Core's menu music plays them by file name.
4.  **The 1.19 biome music for 1.12.2's own biomes is not here**: 1.19 gave forests, flower forests, birch and dark
    forests, jungles, old growth taigas and swamps their own music (`music.overworld.forest`, `jungle_and_forest`,
    `swamp`, ... with 1.18's tracks among them). That is game music, Core's `GameMusic` step "1.19 biome music", like
    Caves & Cliffs' `MusicChoice`; it belongs with Sw (and needs Caves & Cliffs' 1.18 tracks, by name, when installed).
5.  **Languages**: the eight option keys are Mojang's own (`options.glintSpeed` ...); `vanilla_keys.json` lists them as
    ours only because `vanilla_keys.py` reads 1.19.2's English, which lacks the 1.19.4 ones. The family's
    `build_langs.py` matches them by key once this mod's languages are built. The vanilla text is already in all 19.
6.  **Core version**: built against Core 1.1.0's dev jar (this repo's `core_version`); every API used is in 1.1.0.

## Changelog section

*   **The title screen of 1.19**: the mangrove swamp panorama, and 1.19's splashes ("Who let the frogs out?", "RIBBIT!",
    "Technoblade never dies!", "You are valid!" and the rest). One splash 1.19 removed is gone.
*   **Title screen music**: *Aerie*, *Firebugs* and *Labyrinthine* by Lena Raine play on the title screen. With
    Caves & Cliffs Backport installed, its five tracks leave the title screen, as in 1.19.
*   **New accessibility settings**: Darkness Pulsing, Damage Tilt, Glint Speed and Glint Strength (the last two also in
    Video Settings). Panorama Scroll Speed shows too.
*   **The enchantment glint is slower and softer by default**, as in 1.19.4 (Glint Speed 50%, Glint Strength 75%).
    Set both to 100% for the old glint.
*   **Auto-Jump is off by default**, as from 1.19.4. It is switched off once, the first time the game starts with this
    mod; after that it is your setting.
*   **F3 + F no longer changes the render distance**, as from 1.19. The F3 screen shows the allocation rate.
*   **Menus and messages use 1.19.4's wording** ("Realms News & Invites", "Villages, Shipwrecks, etc.", advancement
    descriptions, death messages), in all 19 languages, from Mojang's own translations.

## How it works

*   **Registration** (`LookAndFeel.preInit`, pre-init, both sides): `Presentation.version("1.19")` gets the panorama
    (`textures/gui/title/background/panorama_0..5.png`, 1024x1024, from the 1.19 asset index; 1.19.4's six are the
    same and the generator checks it), the overlay, the title logo, the Mojang Studios logo, `widgets.png`,
    `checkbox.png` and the Accessibility icon (all 1.19.4's jar, byte for byte what 1.16 to 1.18 had: the mod alone
    shows 1.19's title screen), the multiplayer warning keys, the vanilla text folder, 1.19's splash files, F3 + F's
    removal, the menu tracks and Darkness Pulsing. `1.19.1` and `1.19.3` get their splash files; `1.19.4` gets
    Auto-Jump's default and the other three options. Core does the rest: newest installed version wins each single
    slot, lists are each version's additions and removals in version order.
*   **Splashes** (`texts/`): the differences between the jars' `splashes.txt`: 1.18.2 to 1.19 (7 added, "Привет Россия!"
    removed, which 1.12.2 has), 1.19 to 1.19.2 ("Technoblade never dies!", 1.19.1-pre3), 1.19.2 to 1.19.4 (6 added in
    1.19.3).
*   **Menu music**: 1.19's `music.menu` is menu1 to menu4 plus `music/game/swamp/aerie`, `firebugs`, `labyrinthine`,
    weight 1 each; 1.18's five (Caves & Cliffs' names) are removed. 1.19.3 plays the three at volume 0.4; Core plays
    every added track at 1.0 (`CORE_REQUESTS` Lf-1), so here they play as in 1.19 to 1.19.2.
*   **Options**: Core's `SliderOption`s, 0 to 100%, continuous, OFF at zero, each with 1.19.4's tooltip. Accessibility
    Settings order as 1.19.4's: after Nether Update's FOV Effects (or at the end without it) Darkness Pulsing, Damage
    Tilt, Glint Speed, Glint Strength. The two glint sliders also end Video Settings (1.19.4's list ends Autosave
    Indicator, Glint Speed, Glint Strength; 1.12.2 has no autosave indicator).
*   **Damage Tilt** (P1): 1.19.4's `bobHurt` multiplies the tilt's 14 degrees by the option; the patch passes 1.12.2's
    `14.0F` through `LookHooks.damageTilt`.
*   **Glint** (P2 items, P3 armor and elytra): 1.19.4 scrolls the glint texture by `millis * glintSpeed * 8` where
    1.19.3 used `millis * 8`, and its glint shader multiplies the added color by `GlintAlpha` (Glint Strength). The
    patches scale 1.12.2's clocks (`getSystemTime()`, `ticksExisted + partialTicks`) by Glint Speed and its glint
    colors (`0xFF8040CC`; `0.38, 0.19, 0.608`) by Glint Strength. At 100% both are 1.12.2's glint.
*   **Auto-Jump** (1.19.4, 23w03a): `gameOptionDefault("autoJump", "false")`. Core writes it once per instance when the
    title screen first opens; the player's choice stands after that (memory note: config defaults don't migrate).
*   **F3** (`lookfeel.client.LookAndFeelClient`): Core's `DebugText` step "1.19" puts 1.19's
    `Allocation rate: %03dMB /s` between "Mem" and "Allocated", measured as 1.19's `AllocationRateCalculator` does
    (every 500 ms, the used heap's change per second, kept when a GC ran in between). F3 + F is removed from Core's key
    map from 1.19 on, so it neither acts nor shows in F3 + Q.
*   **Vanilla text** (`vanilla_text/`, 109 keys, 19 languages): the keys the earlier family backports reword plus the
    ones 1.19 to 1.19.4 reworded (`V119` in the generator), each with 1.19.4's text in Mojang's file for each language
    (English where Mojang's file lacks a key: 18 lines in all). Left out: command messages (1.13's commands take other
    arguments), "World Type" (1.12.2's button adds no colon), 1.19.3's creative tab names (`CORE_REQUESTS` Lf-3), and
    F3 + F's help line (gone in 1.19; the earlier backport's wording stays).

### Decided from the sources

*   **Glint speed**: halving it at the default is what 1.19.4's code does (`RenderStateShard.setupGlintTexturing`,
    1.19.2 against 1.19.4/1.20.1), not a choice here.
*   **"Preparing for world creation"** (1.19): 1.19 shows it while it loads the world presets before Create New World
    opens. 1.12.2 has nothing to load, so vanilla's own rule would show it for no frame; it is not drawn.
*   **The out-of-memory screen's translation** (1.19): 1.12.2's out-of-memory screen has no lang keys; not done.
*   **Telemetry, chat signing and reporting, Realms, the Player Reporting button**: no such systems in 1.12.2 (out of
    scope in the research file).

## Tested headlessly

*   `runServer -Pselftest=all` (port 25875): 863 checks, 0 failures, this stream's `lookfeel` test included.
*   Registration: the four versions owned by this mod, 1.19.4 newest; Core serves the four vanilla assets from ours
    (sizes checked), the Accessibility icon, the warning keys, the vanilla text, 1.16's startup layout.
*   Panorama: six 1024x1024 faces as Core's newest, the 1x1 overlay.
*   Splashes: file sizes 7/1/1/6; Core's pool is 1.12.2's set less "Привет Россия!" plus the 14, exactly.
*   Menu music: Core's pool is menu1-4 plus the three, alone and with a stand-in for Caves & Cliffs' 1.18 contribution
    (its five removed); the three files are Ogg files in the jar.
*   F3 keys: F3 + F gone from Core's key map, F3 + Q kept.
*   Options: ids, screens, `since`, labels, tooltips, OFF at zero, defaults, ranges, order on Accessibility Settings,
    the two glint sliders (only) on Video Settings, Core's Panorama Scroll Speed shown.
*   Auto-Jump's default is pending (a server never applies a client setting).
*   Hooks: Damage Tilt, Glint Speed and Glint Strength at 0, 50, 75 and 100%.
*   Patches P1-P3 on the dev jar's classes and Forge's SRG jar: the hook calls in the right method, as many as
    expected, ASM's stack verifier on every method; the server never loads the patched classes.
*   Lang: the option keys in en_us, the vanilla text's values, every language complete with no empty line.
*   `python tools/check_assets.py`: 0 errors. Release build (`build --rerun-tasks`): reobfuscated (`func_` names),
    manifest names the coremod, no self-test classes. The jar is 54.7 MB; the three tracks are 49 MB of it.

## Not tested (needs a client)

*   The panorama, logo and splashes on the title screen; the menu tracks actually playing (Core's player).
*   The options on the Accessibility and Video screens, their order and tooltips.
*   P1-P3 in a running client: the tilt when hurt, the glint's speed and brightness on items, armor and elytra.
*   Auto-Jump switched off on first launch.
*   The F3 allocation-rate line and F3 + F doing nothing.
*   The vanilla text in game.
