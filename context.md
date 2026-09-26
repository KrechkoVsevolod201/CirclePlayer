# CirclePlayer — контекст проекта

## Краткое описание

CirclePlayer — Android-приложение для воспроизведения локальной музыки. Интерфейс стилизован под iPod Classic: круговое Click Wheel, экран проигрывателя и список треков, виниловая анимация, светлая и тёмная темы. Поддерживаются альбомная ориентация, ручной выбор папки с музыкой и звуковые эффекты, обрабатывающие PCM-аудио.

## Стек и конфигурация

- Android-приложение на Kotlin; Gradle Kotlin DSL, Version Catalog: `gradle/libs.versions.toml`.
- Android Gradle Plugin `8.13.0`, Kotlin `2.0.21`, Gradle wrapper `8.13`.
- Jetpack Compose: Compose BOM `2024.09.00`, Material 3.
- AndroidX Media3 / ExoPlayer `1.4.1` (ExoPlayer, UI и session).
- `compileSdk = 36`, `targetSdk = 36`, `minSdk = 25` (Android 7.1); Java/Kotlin target 11.
- Namespace и application ID: `com.example.circleplayer`.
- Версия из `app/build.gradle.kts`: `versionName = "1.6"`, `versionCode = 7`. README должен соответствовать версии Gradle.
- Единственный Gradle-модуль: `:app`.

## Структура приложения

- `app/src/main/java/com/example/circleplayer/MainActivity.kt` — Activity, подключение к MediaSession через MediaController, тема, запрос разрешений, выбор папки через SAF и сохранение предпочтений.
- `.../PlayerComposables.kt` — основной Compose UI и большая часть состояния/поведения: `MusicPlayerApp`, `NowPlayingScreen`, Click Wheel, список треков, настройки, эффекты и виниловый скринсейвер. Адаптация интерфейса к портретной/альбомной ориентации также находится здесь.
- `.../MusicRepository.kt` — чтение аудиотреков и доступных музыкальных папок через `MediaStore`; получает URI обложки альбома и считает треки в каталогах.
- `.../AudioTrack.kt` — модели трека (`id`, `title`, `artist`, `uri`, `duration`, `albumArtUri`) и папки (`path`, `name`, `trackCount`).
- `.../service/PlaybackService.kt` — владелец ExoPlayer и MediaSession; обеспечивает системное уведомление, media controls и команды от гарнитур.
- `.../audio/EffectsManager.kt`, `EffectsRenderersFactory.kt` — набор процессоров и подключение их к Media3 AudioSink.
- `.../audio/*Processor.kt` — PCM 16-bit процессоры Wow & Flutter, Volume Detonation, Chorus и Vintage Noise.
- `.../ui/theme/` — Material-тема и собственные светлая/тёмная палитры через `LocalPlayerPalette`.
- `app/src/main/res/` — манифест, ресурсы иконки и темы; `screenshots/` — снимки UI для README.

## Поведение и доменные детали

- Click Wheel: верхняя кнопка Menu, нижняя Play/Pause, центр подтверждает выбор, боковые кнопки переключают треки. Списки треков и папок управляются кольцевым жестом; курсор списка не меняет активный трек до Play/Pause. Вибрация включается настройкой и срабатывает на кнопки и шаги прокрутки; звук прокрутки настраивается отдельно.
- Списки треков и папок показываются на мини-дисплее. Каталоги доступны иерархически; Menu поднимается на папку выше. Системный выбор через `OpenDocumentTree` остаётся доступен.
- Выбранная папка фильтрует медиатеку; системный выбор URI пытается сохранить разрешение, локальный путь и прочие настройки записываются в `SharedPreferences` (`app_prefs`). Тема, скринсейвер, режим пасхалок и отклики Click Wheel хранятся там же.
- Если у трека доступна обложка, её `content://` URI используется как текстура винила в плеере и скринсейвере.
- Пользователь может выбрать свой звук прокрутки Click Wheel, а также настроить масштаб каждой основной области отдельно для portrait/landscape.
- Цветовые темы поддерживают светлую и тёмную палитры; встроенные и пользовательские пресеты хранятся в JSON в настройках приложения и экспортируются/импортируются через SAF.
- Скорость винила задаётся вручную; BPM-режим использует BPM из metadata-тега трека и стандарт 120 BPM при отсутствии тега.
- Язык интерфейса выбирается между русским и английским.
- Скринсейвер с вращающейся пластинкой включается вручную или после 10 секунд бездействия при воспроизведении, если включён в настройках. Интерактивный скретч включается настройкой «Режим пасхалок».
- `MainActivity` получает `MediaController` для сессии `PlaybackService`; UI и системные уведомления управляют одним и тем же ExoPlayer.
- `PlaybackService` зарегистрирован в `AndroidManifest.xml` как exported Media3 session service. При создании ExoPlayer к нему подключается `EffectsRenderersFactory`; включение/выключение эффектов переключает общий master-gate процессоров без замены плеера.
- MediaSessionService публикует метаданные/обложку в Android media notification/system controls и получает транспортные команды, в том числе от Bluetooth-гарнитур. Activity запрашивает `POST_NOTIFICATIONS` на API 33+.
- Процессоры заявляют поддержку PCM 16-bit и используют `@UnstableApi`; учитывай поток аудиоданных, очистку буферов и ограничения realtime-обработки при изменениях в DSP.

## Разрешения и платформенные особенности

- `MainActivity` запрашивает разрешение на чтение аудио по версии Android и уведомления на API 33+; манифест содержит `FOREGROUND_SERVICE_MEDIA_PLAYBACK` и `WAKE_LOCK` для фонового воспроизведения.
- Фильтрация `MusicRepository` использует `MediaStore.Audio.Media.DATA` как путь файла; SAF-провайдеры и ограничения scoped storage могут не предоставлять путь. При развитии доступа к файлам предпочитай работу с `content://` URI и persisted URI grants, если это совместимо с требуемым UX.
- Activity обрабатывает ряд конфигурационных изменений сама через `android:configChanges`; тестируй поведение при смене ориентации, не предполагая обычного пересоздания Activity.
- Главный экран учитывает `WindowInsets.safeDrawing`, оставляет отступы от системных областей и масштабирует композицию по доступным размерам в portrait/landscape.

## Сборка и проверки

Из корня проекта:

```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew test
./gradlew connectedAndroidTest
```

Release-сборка подписывается ключом из `keystore.properties` или переменных `RELEASE_*`; без него получается неподписанный артефакт. Не добавлять keystore и пароли в Git. Store listing находится в `fastlane/metadata/android/`, store checklist — `docs/STORE_RELEASE.md`, скриншоты — `screenshots/`. В репозитории есть базовые шаблонные тесты: `ExampleUnitTest` проверяет сложение, `ExampleInstrumentedTest` — package name; значимого покрытия логики плеера пока нет.

## Рекомендации для изменений

1. Перед правкой изучи существующее поведение и локальные изменения; не перезаписывай пользовательский diff.
2. Сохраняй пакет `com.example.circleplayer`, каталоги и текущий Kotlin/Compose стиль. Новые UI-компоненты по возможности оформляй небольшими `@Composable`-функциями с параметрами состояния и callback-ами.
3. Для UI проверяй портрет/альбом, светлую/тёмную тему, состояния пустой/неразрешённой медиатеки, жесты и системную кнопку «Назад».
4. При изменениях Media3 считай ExoPlayer в `PlaybackService` источником состояния; Activity управляет им через `MediaController`. Учитывай жизненный цикл контроллера, аудиофокус, фоновые сценарии и синхронизацию Compose с `Player.Listener`.
5. После изменений запускай как минимум `./gradlew :app:assembleDebug`; запускай unit/instrumented тесты, если изменения ими покрыты и доступно устройство/эмулятор.
6. Сборочные и IDE-файлы (`.gradle/`, `.idea/`, `local.properties`, APK) не следует добавлять в изменение без явной необходимости.
