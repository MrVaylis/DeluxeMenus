# DeluxeMenus-GemstoneGG: проектный анализ

Дата анализа: 2026-06-12.

Область анализа: read-only анализ кода DeluxeMenus-GemstoneGG и документационная фиксация результатов в `docs/project-analysis.md`. Документ основан на независимых отчетах субагентов и точечной сверке ключевых файлов репозитория. Изменения Java-кода, Gradle, ресурсов, README, Git metadata и build outputs не выполнялись.

Цель документа: дать самостоятельное описание архитектуры, runtime-потоков, конфигураций, build/CI, рисков и roadmap так, чтобы читатель мог оценить проект без доступа к рабочему чату.

## 1. Executive Summary

DeluxeMenus-GemstoneGG - Paper/Bukkit-плагин для построения inventory-based GUI меню в Minecraft. Проект загружает YAML-конфигурации меню, регистрирует команды открытия, строит inventories для игроков, применяет PlaceholderAPI, проверяет requirements и выполняет action chains по кликам, открытию и закрытию меню.

Ключевые подсистемы:

- plugin lifecycle и bootstrap: `src/main/java/com/extendedclip/deluxemenus/DeluxeMenus.java`;
- конфигурационный слой: `src/main/java/com/extendedclip/deluxemenus/config/DeluxeMenusConfig.java`, `src/main/java/com/extendedclip/deluxemenus/config/GeneralConfig.java`;
- runtime меню: `src/main/java/com/extendedclip/deluxemenus/menu/Menu.java`, `src/main/java/com/extendedclip/deluxemenus/menu/MenuHolder.java`, `src/main/java/com/extendedclip/deluxemenus/menu/MenuItem.java`;
- команды и listeners: `src/main/java/com/extendedclip/deluxemenus/command/DeluxeMenusCommand.java`, `src/main/java/com/extendedclip/deluxemenus/command/subcommand/`, `src/main/java/com/extendedclip/deluxemenus/menu/command/RegistrableMenuCommand.java`, `src/main/java/com/extendedclip/deluxemenus/listener/PlayerListener.java`;
- расширения домена: `action`, `requirement`, `hooks`, `scheduler`, `persistentmeta`, `placeholder`, `dupe`, `nbt`, `utils`.

Главный вывод по зрелости: проект функционально широкий и уже содержит важные production-механизмы, включая data model для options, item hooks, dupe protection, generation guards и abstraction над scheduler. При этом зрелость engineering quality ограничена: отсутствуют тесты, CI не запускает `check`, есть существенные runtime/security риски вокруг async Bukkit API, unsandboxed JavaScript, command actions из YAML, статического registry и несовпадений plugin descriptors.

## 2. Карта Репозитория

Root files:

- `build.gradle.kts` - Gradle Kotlin DSL, зависимости, Java 21, ShadowJar и relocations.
- `settings.gradle.kts` - single-project build с `rootProject.name = "DeluxeMenus"` и `TYPESAFE_PROJECT_ACCESSORS`.
- `gradle.properties` - задает UTF-8.
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar` - Gradle wrapper.
- `gradle/libs.versions.toml` - version catalog зависимостей.
- `README.md`, `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md`, `LICENSE` - публичная документация и лицензия.
- `.gitignore` - правила исключений.
- `build/` - локальные generated outputs, не является source of truth.

`src/main/java`:

- `src/main/java/com/extendedclip/deluxemenus/DeluxeMenus.java` - главный `JavaPlugin`.
- `src/main/java/com/extendedclip/deluxemenus/config/` - parsing/validation YAML и общий config.
- `src/main/java/com/extendedclip/deluxemenus/menu/` - runtime меню, holders, items, menu options, per-menu command registration.
- `src/main/java/com/extendedclip/deluxemenus/command/` - основной `/deluxemenus` command и subcommands.
- `src/main/java/com/extendedclip/deluxemenus/listener/` - Bukkit/Paper listeners для команд, inventory open/click/close, quit.
- `src/main/java/com/extendedclip/deluxemenus/action/` - click/open/close action model и executor.
- `src/main/java/com/extendedclip/deluxemenus/requirement/` - requirement types и evaluation.
- `src/main/java/com/extendedclip/deluxemenus/hooks/` - hooks для сторонних item providers и Vault.
- `src/main/java/com/extendedclip/deluxemenus/scheduler/` - abstraction над Bukkit/Paper/Folia scheduling.
- `src/main/java/com/extendedclip/deluxemenus/dupe/` - защита от дублирования menu items.
- `src/main/java/com/extendedclip/deluxemenus/nbt/` - NBT/NMS provider.
- `src/main/java/com/extendedclip/deluxemenus/persistentmeta/` - persistent metadata actions.
- `src/main/java/com/extendedclip/deluxemenus/placeholder/` - PlaceholderAPI expansion.
- `src/main/java/com/extendedclip/deluxemenus/updatechecker/` - update checker.
- `src/main/java/com/extendedclip/deluxemenus/utils/` - общие утилиты, messages, dump, strings, items, sounds.

`src/main/resources`:

- `src/main/resources/plugin.yml` - Bukkit descriptor.
- `src/main/resources/paper-plugin.yml` - Paper descriptor.
- `src/main/resources/default_menu.yml` - минимальное 9-slot меню.
- `src/main/resources/basics_menu.yml` - tutorial по menu YAML/items/requirements/click commands/external menus.
- `src/main/resources/requirements_menu.yml` - tutorial по open/view/click requirements, economy/shop/cooldown.
- `src/main/resources/advanced_menu.yml` - dynamic items, priorities, view requirements, placeholders, open actions.
- `src/main/resources/actions_menu.yml`, `src/main/resources/items_menu.yml` - stub/example resources.

`.github`:

- `.github/workflows/build.yml` - единственный найденный GitHub Actions workflow.

Отсутствующие области:

- `src/test` отсутствует.
- Нет найденных `testImplementation`, JUnit, MockBukkit или Jacoco в build-конфигурации по сводке CI/quality.
- В resources нет `bungee.yml`, `config.yml`, `messages.yml`; `config.yml` генерируется/заполняется runtime-логикой.
- Нет release workflow, `SECURITY.md`, `CHANGELOG.md`, `RELEASE.md`, `CODEOWNERS`.

## 3. Архитектура И Слои

### Core / Lifecycle

`src/main/java/com/extendedclip/deluxemenus/DeluxeMenus.java` - главный `JavaPlugin` и bootstrap root.

Подтвержденные обязанности:

- `onLoad()` проверяет доступность `NbtProvider`, то есть NMS/NBT hook.
- `onEnable()` загружает `GeneralConfig`, требует успешный hook в PlaceholderAPI, создает `PersistentMetaHandler`, `MenuItemMarker`, `DupeFixer`, Adventure audiences, scheduler-facing runtime, Vault/item hooks, затем запускает `DeluxeMenusConfig.loadDefConfig()` и `loadGUIMenus()`.
- `onEnable()` регистрирует `PlayerListener`, главный command, Bungee plugin messaging, update checker и metrics.
- `onDisable()` unregister outgoing BungeeCord channel, отменяет scheduler tasks, закрывает Adventure audiences, вызывает shutdown-unload меню, чистит item hooks и unregister listeners.
- `reload()` на уровне plugin class перегружает `GeneralConfig`; полноценная reload-команда дополнительно чистит caches, вызывает `plugin.reloadConfig()`, `Menu.unload(...)` и `loadGUIMenus()`.

`DeluxeMenus.java` также является service locator для scheduler, hooks, persistent meta, marker и configuration. Это удобно для текущей структуры, но усиливает coupling и усложняет unit testing.

### Config

`src/main/java/com/extendedclip/deluxemenus/config/DeluxeMenusConfig.java` - крупный класс примерно на 1396 строк. Он совмещает:

- создание/проверку `config.yml`;
- создание example menu files;
- чтение `gui_menus`;
- загрузку внешних menu YAML;
- parsing menu-level options;
- parsing item-level options;
- validation deprecated options;
- построение `MenuOptions`, `MenuItemOptions`, `RequirementList`, `ClickHandler`;
- debug/logging решений загрузки.

`src/main/java/com/extendedclip/deluxemenus/config/GeneralConfig.java` отвечает за общие настройки plugin runtime, включая debug/update-related behavior по сводке.

Архитектурный вывод: config слой уже имеет явную модель данных (`MenuOptions`, `MenuItemOptions`), но parser перегружен ответственностями. Основной кандидат на декомпозицию - выделение отдельных parser/validator classes для menu, item, requirements и click actions.

### Menu Runtime

`src/main/java/com/extendedclip/deluxemenus/menu/Menu.java`:

- хранит static registry menus, menu holders, last opened menus и generation counters;
- в constructor кладет menu в registry;
- при `register_command` создает `RegistrableMenuCommand`;
- открывает меню через `openMenu(...)`;
- выполняет open/close command actions;
- закрывает и выгружает меню;
- поддерживает `refreshForAll()`.

`src/main/java/com/extendedclip/deluxemenus/menu/MenuHolder.java`:

- хранит viewer, optional placeholder player, typed args, inventory, active items, текущую menu name и generation;
- применяет placeholders и arguments;
- обновляет active items через `refreshMenu()`;
- запускает/останавливает refresh task и placeholder update task.

`src/main/java/com/extendedclip/deluxemenus/menu/MenuItem.java`:

- представляет runtime item;
- отвечает за создание `ItemStack` и логику material/item hook/metadata/lore/display processing по сводке;
- участвует в priority/view requirement selection.

### Commands / Listeners

`src/main/java/com/extendedclip/deluxemenus/command/DeluxeMenusCommand.java` - главный `/deluxemenus` entrypoint.

`src/main/java/com/extendedclip/deluxemenus/command/subcommand/`:

- `OpenCommand.java`;
- `ReloadCommand.java`;
- `RefreshCommand.java`;
- `ListCommand.java`;
- `MetaCommand.java`;
- `DumpCommand.java`;
- `ExecuteCommand.java`;
- `HelpCommand.java`;
- `SubCommand.java`.

`src/main/java/com/extendedclip/deluxemenus/menu/command/RegistrableMenuCommand.java` - runtime registration для per-menu commands через Bukkit `CommandMap`.

`src/main/java/com/extendedclip/deluxemenus/listener/PlayerListener.java`:

- fallback open через `PlayerCommandPreprocessEvent`, если команда совпала с menu command;
- обработка `InventoryOpenEvent`, `InventoryCloseEvent`, `InventoryClickEvent`, `PlayerQuitEvent`;
- отмена кликов в menu inventory;
- маршрутизация click handlers по типам клика;
- debounce через cache;
- evaluation click requirements и deny handler.

### Domain Extensions

`src/main/java/com/extendedclip/deluxemenus/action/`:

- `ActionType.java` задает action identifiers;
- `ClickAction.java`, `ClickActionTask.java`, `ClickHandler.java` реализуют action chain и scheduling.

`src/main/java/com/extendedclip/deluxemenus/requirement/`:

- requirement API и implementations: permission, money, item, exp, meta, JavaScript, string/numeric/regex/object/location checks;
- `RequirementList.java` связывает requirements с deny actions.

`src/main/java/com/extendedclip/deluxemenus/hooks/`:

- item/economy integrations: Vault, HeadDatabase, CraftEngine, ItemsAdder, Nexo, Oraxen, MMOItems, ExecutableItems, ExecutableBlocks, Score/SCore, SimpleItemGenerator and head hooks.

`src/main/java/com/extendedclip/deluxemenus/scheduler/`:

- abstraction для Bukkit/Paper/Folia/Canvas-style scheduling;
- снижает привязку runtime к конкретному scheduler API, но требует строгих thread-affinity правил.

`src/main/java/com/extendedclip/deluxemenus/dupe/`, `src/main/java/com/extendedclip/deluxemenus/nbt/`:

- marker/PDC/NMS fallback для защиты menu items от выноса/дублирования;
- NBT reflection compatibility остается отдельным риском.

`src/main/java/com/extendedclip/deluxemenus/persistentmeta/`, `src/main/java/com/extendedclip/deluxemenus/placeholder/`, `src/main/java/com/extendedclip/deluxemenus/utils/`:

- persistent meta actions;
- PlaceholderAPI expansion;
- utility layer для strings/items/sounds/messages/dumps/pagination/version.

## 4. Runtime-Потоки

### onLoad / onEnable / onDisable / reload

`onLoad()`:

1. Проверяет `NbtProvider.isAvailable()`.
2. Логирует успешную или неуспешную настройку NMS/NBT hook.

`onEnable()`:

1. Загружает `GeneralConfig`.
2. Проверяет PlaceholderAPI через `hookIntoPlaceholderAPI()`.
3. Если PlaceholderAPI недоступен, отключает plugin.
4. Создает persistent meta, menu item marker и dupe fixer.
5. Создает Adventure audiences.
6. Настраивает scheduler.
7. Регистрирует Vault и item hooks.
8. Создает `DeluxeMenusConfig`.
9. Выполняет `loadDefConfig()`, затем `loadGUIMenus()`.
10. Регистрирует listeners и `/deluxemenus` command.
11. Настраивает Bungee messaging, update checker и metrics.

`onDisable()`:

1. Unregister outgoing BungeeCord channel.
2. Отменяет scheduler tasks plugin-а.
3. Закрывает Adventure audiences.
4. Выполняет shutdown-unload меню через `Menu.unloadForShutdown(this)`.
5. Чистит item hooks.
6. Unregister all handlers plugin-а.

Reload:

- `DeluxeMenus.reload()` перегружает только `GeneralConfig`.
- `ReloadCommand.java` делает полный operational reload: проверяет `config.yml`, чистит caches, вызывает `plugin.reloadConfig()`, `plugin.saveConfig()`, `plugin.reload()`, `Menu.unload(plugin)`, затем `plugin.getConfiguration().loadGUIMenus()`.
- Reload одного меню: `Menu.unload(plugin, menuName)` и `loadGUIMenu(menuName)`.

### Загрузка YAML И Registry Меню

1. `DeluxeMenusConfig.loadDefConfig()` проверяет/создает `config.yml`.
2. При необходимости создает example menu files из resources.
3. `loadGUIMenus()` читает `gui_menus`.
4. Для каждого menu key выбирается источник: inline section в `config.yml` или external file.
5. `loadMenu(...)` парсит menu options: title, size/type, commands, requirements, handlers, update/refresh options.
6. `loadMenuItems(...)` парсит item sections и строит `MenuItemOptions`.
7. Requirements собираются в `RequirementList`.
8. Click commands собираются в `ClickHandler`.
9. Создается `Menu`.
10. Constructor `Menu` кладет объект в static registry и при включенном register command создает `RegistrableMenuCommand`.

Архитектурный риск: static registry делает runtime простым, но усложняет reload isolation, тесты и lifecycle cleanup.

### Открытие Меню

`Menu.openMenu(...)`:

1. Проверяет, что menu содержит items.
2. Вызывает `DeluxeMenusPreOpenMenuEvent`.
3. Создает `MenuHolder`.
4. Устанавливает placeholder player, typed args и parse modes.
5. Проверяет argument requirements и open requirements.
6. Устанавливает open generation.
7. Запускает async task для выбора active items и построения inventory.
8. В async-фазе выбираются active items по priority/view requirements и создаются item stacks.
9. Затем scheduler переводит управление на task для viewer.
10. Закрывается предыдущий holder, если был.
11. Новый holder добавляется в registry, player открывает inventory.
12. Выполняются open handler, `gui_open_commands`, `DeluxeMenusOpenMenuEvent`.
13. При необходимости стартуют refresh task и placeholder update task.

Важный риск: по отчету и точечной сверке часть работы с inventory/item stack происходит в async-фазе. Для Bukkit/Paper API это требует аудита thread affinity.

### Обработка Клика / Action Execution

`PlayerListener.onClick(...)`:

1. Проверяет, что actor - `Player`.
2. Находит текущий `MenuHolder`.
3. Если holder обновляется, отменяет event и выходит.
4. Отменяет inventory click event.
5. Определяет raw slot и `MenuItem`.
6. Выбирает handler по click type: generic click, shift-left, shift-right, left, right, middle.
7. Проверяет click requirements.
8. При fail выполняет deny handler, если он задан.
9. При success обновляет debounce cache и вызывает `ClickHandler.onClick(holder)`.
10. `ClickHandler` передает actions в `ClickActionTask`.

ActionType покрывает команды, сообщения, открытие/закрытие/refresh меню, Vault money, permissions, exp, sounds, metadata, json и другие действия по сводке. Config-driven actions могут иметь задержки через `<delay=ticks>`.

### Refresh / Update / Close Lifecycle

Refresh:

- `MenuHolder.startRefreshTask()` запускает async timer.
- `refreshMenu()` пересобирает active items, view requirements и inventory contents.
- При изменении необходимости placeholder updates запускает или останавливает placeholder update task.

Placeholder update:

- `startUpdatePlaceholdersTask()` запускает регулярный task.
- Обновляет dynamic amount, display name и lore для items, где включено update placeholders.

Close:

- `Menu.closeMenu(...)` останавливает placeholder update и refresh tasks.
- Опционально выполняет close handler.
- Опционально закрывает inventory и чистит marked items.
- Удаляет holder из registry.
- Сохраняет last opened menu.
- Опционально выполняет `gui_close_commands`.

Shutdown close:

- `Menu.unloadForShutdown(...)` вызывается из `onDisable()`.
- По сводке и исходникам shutdown path закрывает/чистит menu state без обычного пользовательского сценария.

### Per-Menu Commands И Fallback

Основной command:

- `/deluxemenus` описан в `src/main/resources/plugin.yml`;
- aliases: `dm`, `deluxemenu`, `dmenu`;
- subcommands находятся в `src/main/java/com/extendedclip/deluxemenus/command/subcommand/`.

Per-menu commands:

- YAML `open_command` может быть string, list или empty list.
- При `register_command` menu constructor создает `RegistrableMenuCommand`.
- Registration идет через Bukkit `CommandMap` reflection/интеграцию.

Fallback:

- `PlayerListener.onCommandExecute()` слушает `PlayerCommandPreprocessEvent`;
- если raw command соответствует menu command, event cancel и вызывается `menu.openMenu(player)`;
- это страхует сценарии, где runtime command registration не сработал или не покрывает конкретный case.

Риск: при unregister runtime commands CraftBukkit может сохранять references для `/help`; это прямо отражено в комментарии в `Menu.java` и требует отдельной проверки при reload.

## 5. Конфигурации И Ресурсы

### plugin.yml И paper-plugin.yml

`src/main/resources/plugin.yml`:

- Bukkit metadata: `name: DeluxeMenus`, `main: com.extendedclip.deluxemenus.DeluxeMenus`, `version: ${version}`;
- `api-version: 1.13`;
- `folia-supported: true`;
- authors: `HelpChat`;
- softdepend: PlaceholderAPI, Vault, HeadDatabase, CraftEngine, ItemsAdder, Nexo, Oraxen, ExecutableItems, ExecutableBlocks, Score, SimpleItemGenerator, MMOItems;
- command `/deluxemenus` with aliases `dm`, `deluxemenu`, `dmenu`;
- permissions: `deluxemenus.admin`, `deluxemenus.open`, `deluxemenus.open.others`, `deluxemenus.open.bypass`, `deluxemenus.menu.*`, `deluxemenus.openrequirement.bypass.*`, `deluxemenus.placeholdersfor`, `deluxemenus.placeholdersfor.exempt`.

`src/main/resources/paper-plugin.yml`:

- Paper metadata с тем же main/name/version;
- `api-version: '1.21'`;
- `folia-supported: true`;
- dependency model через `dependencies.server`;
- PlaceholderAPI указан как `required: true`;
- Vault и item hooks указаны как optional.

Ключевое расхождение: `plugin.yml` говорит `api-version: 1.13` и `softdepend` на PlaceholderAPI, а `paper-plugin.yml` говорит `api-version: 1.21` и required PlaceholderAPI. Runtime `onEnable()` также фактически требует PlaceholderAPI, иначе plugin отключается.

### Bundled Menu Resources

`src/main/resources/default_menu.yml`:

- минимальное 9-slot меню `menu`;
- permission gate;
- exit item.

`src/main/resources/basics_menu.yml`:

- tutorial по базовому menu YAML;
- items, requirements, click commands;
- external `gui_menus` examples.

`src/main/resources/requirements_menu.yml`:

- tutorial по `open_requirement`, `view_requirement`, click requirements;
- examples economy/shop/cooldown.

`src/main/resources/advanced_menu.yml`:

- dynamic items через priority/view requirements/placeholders;
- open commands/actions;
- содержит `update: true` по сводке, но отдельный риск - отсутствие явного `update_interval` в примере.

`src/main/resources/actions_menu.yml`, `src/main/resources/items_menu.yml`:

- преимущественно stub/example resources.

Отсутствуют bundled `bungee.yml`, `config.yml`, `messages.yml`.

### Формат Menu YAML

Menu-level поля по сводке:

- `menu_title`;
- `open_command` как string/list/empty list;
- `open_commands`;
- `size`;
- `items`;
- также используются requirements, close/open handlers, refresh/update options и `gui_menus` mappings в `config.yml`.

Item-level поля:

- `id`;
- `material`;
- `slot` / `slots`;
- `display_name`;
- `lore`;
- `amount`;
- `data`;
- `priority`;
- `update`;
- `flags` / item flags;
- `enchantments`;
- `banner_meta`;
- additional modern item metadata по parser-слою, включая model/custom model data options.

Requirements:

- menu-level: `open_requirement`;
- item-level: `view_requirement`;
- click-level: `left_requirement`, `right_requirement`, `shift_*`, `middle_requirement`, `click_requirement`;
- supported types включают `javascript`, `has item`, `has money`, `has permission`, string checks, numeric comparisons, regex matches и inverted forms вроде `!has permission`.

Click commands:

- `left_click_commands`;
- `right_click_commands`;
- `shift_left_click_commands`;
- `shift_right_click_commands`;
- `middle_click_commands`;
- `click_commands`;
- delay syntax: `<delay=ticks>`.

Action identifiers по сводке:

- `[console]`;
- `[player]`;
- `[commandevent]`;
- `[message]`;
- `[openguimenu]`;
- `[connect]`;
- `[close]`;
- `[refresh]`;
- `[broadcastsound]`;
- `[sound]`;
- `[json]`;
- `[takemoney]` и `[givemoney]` используются, но отсутствуют в basics reference по отчету.

Placeholders:

- PlaceholderAPI обязателен фактически на runtime;
- `MenuHolder` применяет placeholders и typed arguments;
- есть permissions для parsing placeholders for other players: `deluxemenus.placeholdersfor`, `deluxemenus.placeholdersfor.exempt`.

### Commands, Permissions, Hooks / Dependencies

Commands:

- `/deluxemenus`;
- aliases: `/dm`, `/deluxemenu`, `/dmenu`;
- per-menu commands через YAML `open_command` и runtime registration;
- fallback per-menu handling через `PlayerCommandPreprocessEvent`.

Permissions declared in `plugin.yml`:

- `deluxemenus.admin`;
- `deluxemenus.open`;
- `deluxemenus.open.others`;
- `deluxemenus.open.bypass`;
- `deluxemenus.menu.*`;
- `deluxemenus.openrequirement.bypass.*`;
- `deluxemenus.placeholdersfor`;
- `deluxemenus.placeholdersfor.exempt`.

Permissions used but missing from `plugin.yml` по сводке:

- `deluxemenus.reload`;
- `deluxemenus.list`;
- `deluxemenus.refresh`;
- `deluxemenus.meta`.

Hooks/dependencies:

- PlaceholderAPI;
- Vault;
- HeadDatabase;
- CraftEngine;
- ItemsAdder;
- Nexo;
- Oraxen;
- MMOItems;
- ExecutableItems;
- ExecutableBlocks;
- Score/SCore;
- SimpleItemGenerator.

Examples assume external server commands/providers such as Essentials/economy commands and LuckPerms. Это важно для risk review, потому что example YAML может выполнять реальные economy/permission/player inventory actions.

## 6. Build / Dependencies

Build model:

- Gradle Kotlin DSL;
- single-project build;
- `rootProject.name = "DeluxeMenus"`;
- `group = "com.extendedclip"`;
- version formula: `1.14.2-DEV-$BUILD_NUMBER`, потому что `release = false`;
- без `BUILD_NUMBER` локальная версия становится `1.14.2-DEV-null`;
- Shadow artifact: `DeluxeMenus-${rootProject.version}.jar`;
- по сводке текущий output: `build/libs/DeluxeMenus-1.14.2-DEV-null.jar`.

Toolchain / wrapper:

- Java source/target compatibility: 21;
- Gradle wrapper: 9.4.1;
- Kotlin используется только для Gradle DSL;
- по сводке в `src/main/java` 97 Java files, Kotlin source отсутствует.

Gradle plugins:

- `java`;
- `com.gradleup.shadow` version `9.4.1`;
- `com.github.ben-manes.versions` version `0.54.0`.

Repositories:

- Maven Central;
- PaperMC public repository;
- ExtendedClip PlaceholderAPI repository;
- PhoenixDev/Nexus, Momirealms, Nexo, Oraxen, devs.beer, JitPack;
- Paper repository указан повторно.

Dependencies:

- `compileOnly`: Paper API, Vault, Authlib, HeadDatabase, CraftEngine, ItemsAdder, Nexo, Oraxen, MythicLib, MMOItems, SCore/Score, SimpleItemGenerator, PlaceholderAPI, JetBrains annotations.
- `implementation`: Nashorn, Adventure Bukkit platform, Adventure MiniMessage, bStats.

Shadow relocations:

- `org.objectweb.asm` -> `com.extendedclip.deluxemenus.libs.asm`;
- `org.openjdk.nashorn` -> `com.extendedclip.deluxemenus.libs.nashorn`;
- `net.kyori` -> `com.extendedclip.deluxemenus.libs.adventure`;
- `org.bstats` -> `com.extendedclip.deluxemenus.libs.bstats`.

Known Gradle tasks:

- `clean`;
- `processResources`;
- `classes`;
- `jar`;
- `shadowJar`;
- `assemble`;
- `build`;
- `check`;
- `test`;
- `dependencyUpdates`;
- `dependencies`.

Validation commands:

```powershell
.\gradlew.bat clean shadowJar
.\gradlew.bat check
.\gradlew.bat dependencyUpdates
.\gradlew.bat --stop
```

Практический baseline на текущий проект: `.\gradlew.bat shadowJar`; более чистая локальная проверка: `.\gradlew.bat clean shadowJar`.

Build risks:

- no `distributionSha256Sum` in wrapper properties по сводке;
- нет dependency verification;
- есть SNAPSHOT/external dependency exposure по сводке;
- Java 21 runtime constraint должен быть явно отражен в docs и server requirements;
- возможен jar/shadowJar artifact naming conflict, если downstream automation ожидает только один jar;
- plugin descriptor mismatch между Bukkit/Paper descriptors.

## 7. CI / Testing / Release

GitHub Actions:

- единственный workflow: `.github/workflows/build.yml`;
- `push` и `pull_request` triggers только для branch `main`;
- текущая проверенная ветка репозитория: `master` tracking `origin/master`;
- это означает риск, что CI не запускается на фактической основной ветке, если remote default branch действительно `master`.

Build job:

- runner: `ubuntu-latest`;
- JDK 21 через `actions/setup-java@v4`, distribution `temurin`;
- Gradle caching через `gradle/actions/setup-gradle@v3`;
- build command: `./gradlew shadowJar`;
- env `BUILD_NUMBER: PR-${{ github.event.number }}-${{ env.SHORT_SHA }}`;
- artifact upload: `build/libs`.

Dependency submission job:

- комментарий описывает dependency graph submission;
- фактический YAML использует `gradle/actions/setup-gradle@v3`;
- по отчету нет явного specialized dependency submission action, поэтому эффективность этого job требует отдельной проверки.

Testing:

- `src/test` отсутствует;
- нет test sources;
- нет `testImplementation`/JUnit/MockBukkit/Jacoco по сводке;
- CI не запускает `test` или `check`, только `shadowJar`.

Release / Security / Docs:

- release workflow отсутствует;
- `SECURITY.md`, `CHANGELOG.md`, `RELEASE.md`, `CODEOWNERS` отсутствуют по сводке;
- `CONTRIBUTING.md` говорит Java 17+, но build требует Java 21;
- `README.md` содержит внешние HTTP CI/Jenkins references по сводке.

## 8. Сильные Стороны

- Понятное package separation: core, config, menu, commands/listeners, actions, requirements, hooks, scheduler, utils.
- Есть data model для menu/item configuration: `MenuOptions`, `MenuItemOptions`.
- Есть `ItemHook` extension model для сторонних item providers.
- Scheduler abstraction учитывает Bukkit/Paper/Folia/Canvas-style execution.
- Есть generation counters для open attempts/current holders, что снижает риск race conditions при быстром открытии/закрытии меню.
- Есть dupe protection через marker model с PDC/NMS fallback.
- Runtime поддерживает богатые DSL capabilities: requirements, placeholders, typed args, click actions, delayed actions, per-menu commands.
- `paper-plugin.yml` уже использует современную dependency model для Paper.
- Shadow relocation покрывает embedded Nashorn, Adventure и bStats, снижая риск classpath conflicts.

## 9. Риски

### Critical / P0

- Async Bukkit/Inventory/ItemStack work: `Menu.openMenu(...)` и `MenuHolder.refreshMenu()` выполняют значимую часть построения active items/inventory вне main/region thread. Bukkit/Paper API обычно не гарантирует thread-safety для `Inventory`, `ItemStack`, item meta, PlaceholderAPI и player state. Это главный runtime-риск для race conditions, undefined behavior и Folia/Paper compatibility.
- Unsandboxed JavaScript requirements: `JavascriptRequirement` использует Nashorn и по сводке предоставляет `BukkitServer`/`BukkitPlayer`. Это дает конфигурации возможность выполнять произвольную логику с доступом к server/player API, что является security boundary risk, если YAML может менять не полностью доверенный пользователь.
- Console/player commands from config: action execution позволяет `[console]`, `[player]`, economy/permission actions и command events из YAML. Это ожидаемая функциональность, но без allowlist/safety review может приводить к разрушительным side effects: выдача/очистка предметов, economy transfers, LuckPerms/permission changes, command escalation.
- External dump without explicit secret redaction: `DumpUtils` по сводке отправляет config/menu на внешний paste. Без явной редактуры secrets/tokens/player-sensitive values это риск утечки конфигурации и operational data.

### High / P1

- Update checker external dependency: `UpdateChecker` обращается к внешнему Spigot API. По сводке есть риск отсутствия явных network timeouts и зависимости startup/runtime от внешнего сервиса.
- Static registry/reload/testability: `Menu` держит static maps для menus/holders/last opened/generations. Это упрощает доступ, но усложняет deterministic tests, reload isolation, memory cleanup и parallel test execution.
- Reflection CommandMap/dangling `/help` references: per-menu commands регистрируются runtime через `CommandMap`; в `Menu.java` отмечен риск references, которые CraftBukkit хранит для `/help`. Возможны stale commands после reload.
- NMS/NBT reflection compatibility: `NbtProvider` и NMS/PDC marker fallback требуют постоянной проверки на новых Minecraft/Paper versions.
- PlaceholderAPI required vs softdepend mismatch: `plugin.yml` декларирует PlaceholderAPI как `softdepend`, `paper-plugin.yml` как required, а runtime отключает plugin без PlaceholderAPI. Это вводит администраторов и dependency resolvers в разные режимы ожиданий.
- `api-version` mismatch: `plugin.yml` использует `1.13`, `paper-plugin.yml` использует `1.21`. Для одного artifact это может менять поведение loader/API compatibility и должно быть явно выровнено или объяснено.
- No tests/check gates: отсутствуют automated tests и CI не запускает `check`. Для проекта с YAML DSL, command execution, async runtime и multi-platform scheduler это высокий regression risk.

### Medium / P2

- `1.14.2-DEV-null` version without `BUILD_NUMBER`: локальные builds получают `DEV-null`, что может ломать support diagnostics, artifact sorting и release hygiene.
- Gradle wrapper без `distributionSha256Sum`: supply-chain hardening не завершен.
- Dependency verification отсутствует: внешние Maven repos и SNAPSHOT/external dependencies увеличивают supply-chain surface.
- Duplicate Paper repository: в `build.gradle.kts` PaperMC repository указан дважды, что не критично, но показывает build hygiene gap.
- SNAPSHOT/external dependencies: по сводке есть риск нестабильности и воспроизводимости.
- CI branch mismatch: workflow слушает `main`, а текущая ветка `master` отслеживает `origin/master`. Если `master` является active branch, CI может не исполняться на push/PR.
- `plugin.yml` missing permissions: используются permissions вроде `deluxemenus.reload`, `deluxemenus.list`, `deluxemenus.refresh`, `deluxemenus.meta`, но они не объявлены в descriptor.
- Resource examples with real commands: examples используют реальные `give`, `clear`, `eco`, `lp`/permission commands. Это полезно для обучения, но должно быть помечено как destructive/production-impacting.
- Advanced example update config gap: `advanced_menu.yml` по сводке включает `update: true`, но не задает явный `update_interval`; это может формировать неоптимальные ожидания.
- Descriptor/build docs mismatch: `CONTRIBUTING.md` говорит Java 17+, build использует Java 21.

### Low / P3

- Docs gaps: нет `SECURITY.md`, `CHANGELOG.md`, `RELEASE.md`, `CODEOWNERS`.
- Resource comments могут быть malformed/misleading по сводке; это стоит исправлять вместе с config reference.
- `actions_menu.yml` и `items_menu.yml` преимущественно stub examples, их ценность как документации ограничена.
- Legacy example fields: `data: 1` и skull command examples требуют migration notes для modern Minecraft.
- README references external HTTP CI/Jenkins по сводке; это documentation hygiene risk.
- Dependency submission job comment/YAML mismatch требует уточнения, но не блокирует build.

## 10. Future Tasks / Roadmap

### Immediate / P0

- Провести thread-affinity audit для `Menu.openMenu(...)`, `MenuHolder.refreshMenu()`, PlaceholderAPI calls, ItemStack/meta mutation и inventory operations.
- Перенести небезопасные Bukkit/Paper API операции на main/region scheduler task; async оставить только для pure data decisions, если они действительно thread-safe.
- Формализовать scheduler contract: какие callbacks могут выполнять Bukkit API, какие должны быть pure computation.
- Ввести security policy для JavaScript requirements: disable-by-default option, explicit enable flag, sandbox/limited bindings или documented trusted-config-only mode.
- Добавить redaction layer в dump workflow: secrets, tokens, database URLs, player identifiers и sensitive command values должны маскироваться перед external paste.
- Проверить и выровнять CI branch trigger: `main` vs `master`/default branch.
- Добавить минимальный CI gate `./gradlew check` даже до появления полноценного test suite.

### Short-Term / P1

- Разделить `DeluxeMenusConfig.java` на parser classes: menu parser, item parser, requirement parser, action parser, config file resolver.
- Вынести static `Menu` registry за interface/service, чтобы облегчить reload tests и lifecycle cleanup.
- Добавить tests для YAML parsing: bundled examples, invalid configs, requirements, click commands, external files.
- Добавить MockBukkit/Paper-oriented integration tests для open/click/close/reload happy paths.
- Выровнять `plugin.yml` и `paper-plugin.yml`: `api-version`, PlaceholderAPI required/softdepend semantics, declared permissions.
- Добавить все используемые permissions в `plugin.yml`: `deluxemenus.reload`, `deluxemenus.list`, `deluxemenus.refresh`, `deluxemenus.meta` и другие найденные при полном grep-аудите.
- Добавить command action safety docs: trusted config model, examples с явными warnings для `[console]`, economy и permission commands.
- Установить network timeouts/error handling contract для `UpdateChecker`.
- Обновить `CONTRIBUTING.md` под Java 21.

### Medium-Term / P2

- Добавить dependency verification и `distributionSha256Sum` для Gradle wrapper.
- Пересмотреть external repositories и SNAPSHOT dependencies; закрепить версии, где возможно.
- Разделить release и development versioning так, чтобы локальные builds не получали `DEV-null`.
- Добавить release workflow: changelog generation, tagged builds, artifact signing/checksum, GitHub release draft.
- Подготовить migration guide для legacy YAML: `data`, skull formats, material changes, PlaceholderAPI behavior.
- Расширить bundled examples: safe sandbox examples, non-destructive economy mock examples, explicit `update_interval`.
- Добавить config reference document: menu options, item options, requirements, actions, permissions, placeholders.
- Добавить operational docs: server requirements, Java 21, Paper/Paper-plugin mode, PlaceholderAPI requirement, Vault optional behavior.

### Long-Term / P3

- Сформировать public API boundary для extensions, hooks и actions.
- Ввести compatibility matrix по Minecraft/Paper versions и hooks.
- Добавить performance benchmarks для menu open/refresh/update intervals на больших меню.
- Добавить security hardening docs и `SECURITY.md`.
- Добавить `CHANGELOG.md`, `RELEASE.md`, `CODEOWNERS`.
- Рассмотреть declarative command action policy: allowlist/denylist per server, dry-run validation, config lint.
- Добавить config linter CLI/task для проверки menu YAML до запуска server.
- Документировать reload semantics и ограничения runtime command registration.

## 11. Acceptance / Validation Checklist Для Будущих Изменений

Для любого изменения runtime меню:

- Нужно проверить thread-affinity impact: Bukkit/Paper API не вызывается из async task без доказанного safe contract.
- Должны быть проверены open, click, refresh, placeholder update и close flows.
- Должны быть проверены generation guards при быстром повторном открытии меню.
- Нужно проверить reload одного меню и полный reload.
- Нужно проверить отсутствие stale holders/tasks после close/reload/disable.

Для изменения YAML parser/config:

- Должны быть добавлены или обновлены parser tests.
- Должны быть проверены bundled examples: `default_menu.yml`, `basics_menu.yml`, `requirements_menu.yml`, `advanced_menu.yml`, `actions_menu.yml`, `items_menu.yml`.
- Должна быть обновлена config reference или migration note.
- Должно быть проверено, что ошибки invalid config логируются с понятным path/menu/item context.

Для изменения actions/requirements:

- Для destructive actions должен быть explicit documentation warning.
- Для JavaScript changes должно быть выполнено security review.
- Для Vault/permission/player command behavior должны быть проверены side effects.
- Должны быть проверены deny handlers и delayed actions.

Для build/CI:

- Локально должно быть выполнено `.\gradlew.bat clean shadowJar`.
- Для code changes должно быть выполнено `.\gradlew.bat check`.
- Должно быть проверено, что artifact name/version ожидаемые и не `DEV-null` в release context.
- Должны быть проверены descriptors после `processResources`: `plugin.yml`, `paper-plugin.yml`.
- CI triggers должны соответствовать default branch.

Для release/security/docs:

- Должен быть обновлен changelog или release notes.
- Должны быть обновлены docs для новых config keys/permissions/actions.
- Должна быть проверена redaction для dump/export behavior.
- Должна быть проверена compatibility с Java 21 и заявленными server versions.

## 12. Приложение: Команды Для Локальной Проверки

PowerShell commands из root репозитория:

```powershell
# Проверить текущее состояние Git без изменения файлов
git status --short
git branch -vv

# Собрать shadow artifact
.\gradlew.bat clean shadowJar

# Запустить стандартные Gradle checks
.\gradlew.bat check

# Посмотреть dependency updates
.\gradlew.bat dependencyUpdates

# Остановить Gradle daemon после проверки
.\gradlew.bat --stop
```

PowerShell commands для read-only inspection без `rg`:

```powershell
# Список Java files
Get-ChildItem -Recurse -File -LiteralPath src\main\java

# Список resources
Get-ChildItem -Recurse -File -LiteralPath src\main\resources

# Поиск по исходникам через Get-ChildItem + Select-String
Get-ChildItem -Recurse -File -LiteralPath src\main\java -Filter *.java | Select-String -Pattern "runTaskAsynchronously|JavascriptRequirement|CommandMap|PlayerCommandPreprocessEvent"

# Поиск через git grep, если нужен индекс Git
git grep -n "deluxemenus.reload"
```

## 13. Приложение: Ключевые Файлы

Lifecycle:

- `src/main/java/com/extendedclip/deluxemenus/DeluxeMenus.java`

Config:

- `src/main/java/com/extendedclip/deluxemenus/config/DeluxeMenusConfig.java`
- `src/main/java/com/extendedclip/deluxemenus/config/GeneralConfig.java`

Menu runtime:

- `src/main/java/com/extendedclip/deluxemenus/menu/Menu.java`
- `src/main/java/com/extendedclip/deluxemenus/menu/MenuHolder.java`
- `src/main/java/com/extendedclip/deluxemenus/menu/MenuItem.java`
- `src/main/java/com/extendedclip/deluxemenus/menu/options/MenuOptions.java`
- `src/main/java/com/extendedclip/deluxemenus/menu/options/MenuItemOptions.java`
- `src/main/java/com/extendedclip/deluxemenus/menu/command/RegistrableMenuCommand.java`

Commands/listeners:

- `src/main/java/com/extendedclip/deluxemenus/command/DeluxeMenusCommand.java`
- `src/main/java/com/extendedclip/deluxemenus/command/subcommand/ReloadCommand.java`
- `src/main/java/com/extendedclip/deluxemenus/command/subcommand/OpenCommand.java`
- `src/main/java/com/extendedclip/deluxemenus/command/subcommand/DumpCommand.java`
- `src/main/java/com/extendedclip/deluxemenus/command/subcommand/MetaCommand.java`
- `src/main/java/com/extendedclip/deluxemenus/command/subcommand/RefreshCommand.java`
- `src/main/java/com/extendedclip/deluxemenus/listener/PlayerListener.java`

Actions/requirements:

- `src/main/java/com/extendedclip/deluxemenus/action/ActionType.java`
- `src/main/java/com/extendedclip/deluxemenus/action/ClickAction.java`
- `src/main/java/com/extendedclip/deluxemenus/action/ClickActionTask.java`
- `src/main/java/com/extendedclip/deluxemenus/action/ClickHandler.java`
- `src/main/java/com/extendedclip/deluxemenus/requirement/Requirement.java`
- `src/main/java/com/extendedclip/deluxemenus/requirement/RequirementList.java`
- `src/main/java/com/extendedclip/deluxemenus/requirement/JavascriptRequirement.java`

Hooks/scheduler/platform:

- `src/main/java/com/extendedclip/deluxemenus/hooks/ItemHook.java`
- `src/main/java/com/extendedclip/deluxemenus/hooks/VaultHook.java`
- `src/main/java/com/extendedclip/deluxemenus/scheduler/UniversalScheduler.java`
- `src/main/java/com/extendedclip/deluxemenus/scheduler/scheduling/schedulers/TaskScheduler.java`
- `src/main/java/com/extendedclip/deluxemenus/nbt/NbtProvider.java`
- `src/main/java/com/extendedclip/deluxemenus/dupe/MenuItemMarker.java`
- `src/main/java/com/extendedclip/deluxemenus/dupe/DupeFixer.java`

Resources:

- `src/main/resources/plugin.yml`
- `src/main/resources/paper-plugin.yml`
- `src/main/resources/default_menu.yml`
- `src/main/resources/basics_menu.yml`
- `src/main/resources/requirements_menu.yml`
- `src/main/resources/advanced_menu.yml`
- `src/main/resources/actions_menu.yml`
- `src/main/resources/items_menu.yml`

Build/CI:

- `build.gradle.kts`
- `settings.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `gradle/wrapper/gradle-wrapper.properties`
- `.github/workflows/build.yml`
