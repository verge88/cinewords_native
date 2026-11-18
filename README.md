# CineWords Native

Нативная Android-версия [CineWords](https://github.com/verge88/cinewords) на **Kotlin + Jetpack Compose** (без Flutter runtime).

## Сборка

Нужны Android SDK Platform 35, JDK 17 и Gradle 8.10.2. Откройте репозиторий в Android Studio или выполните:

```bash
gradle :app:assembleDebug
gradle :app:testDebugUnitTest
```

В `local.properties` укажите путь к Android SDK и **свои** ключи, если нужны соответствующие каталоги:

```properties
sdk.dir=/path/to/Android/Sdk
YOUTUBE_API_KEY=...
KINOPOISK_API_KEY=...
DDGS_BASE_URL=http://10.0.2.2:4479
```

Эти значения автоматически попадают в BuildConfig во время сборки. `local.properties` добавлен в `.gitignore`. Публичный Supabase URL и *publishable* key совпадают с Flutter-версией; данные аккаунта и словарь остаются в тех же таблицах.

После успешного запуска [GitHub Actions](https://github.com/verge88/cinewords_native/actions/workflows/android.yml) в артефактах workflow доступен `cinewords-native-debug-apk` (тестовая сборка для Android). Для воспроизведения YouTube и поиска Kinopoisk с вашими ключами необходимо собрать APK со своими `local.properties`; ключи не следует публиковать в репозитории.

## Реализовано

- Авторизация/регистрация через Supabase, хранение и обновление сессии, выход.
- Главная страница и каталог: избранное, поиск/пагинация YouTube Data API, видео из Supabase, поиск фильмов в Kinopoisk, каталог Internet Archive и DDGS-метапоиск (нужен запущенный DDGS API-сервер).
- Страница видео; воспроизведение YouTube через iframe/WebView и прямых HLS/MP4 через Media3 ExoPlayer, публичные фильмы Internet Archive, попытка извлечь HLS через Rivestream и запасной сторонний iframe для фильмов.
- EN/RU субтитры из Supabase, прямые SRT/VTT и VDRK для TMDB, автоматическое сохранение загруженных YouTube-дорожек в Supabase, синхронизация по позиции, настраиваемый EN/RU-оверлей поверх видео (с отступом и масштабом), транскрипт с переходом на таймкод, нажатие на слово для добавления в словарь.
- Словарь: фильтрация, поиск, слова и фразы, добавление, удаление, транскрипция/перевод, карточки SM-2 через существующий Supabase RPC.
- 11 вариантов в разделе упражнений (включая карточки, семантические кластеры, определения, shadowing, диктант, минимальные пары, конструктор, времена, cloze, читалку и невидимые слова). TTS — системный Android.
- Профиль, статистика, системная/светлая/тёмная/монохромная темы.

## Ограничения текущего переноса

Это **первый нативный порт**, а не подтверждённый 1:1 перенос всех внутренних алгоритмов Flutter-проекта. Сторонние источники видео (Rivestream/VDRK/Internet Archive/DDGS/SearXNG), автоматическое получение YouTube-транскриптов и автоперевод всех субтитров пока не обеспечены в полном объёме. Текущая реализация сначала ищет дорожки json3 в YouTube watch page, затем пробует legacy timedtext; оба способа доступны не для всех видео. Для фильмов открывается исходный embed-сервис, а не нативный поток Rivestream. Некоторые упражнения упрощены относительно Flutter-версии (например, shadowing без автоматического распознавания произношения).

Перед публикацией необходимы проверка Android-сборки и ручная проверка на устройстве: Supabase авторизация, обновление токена, поведение плеера, доступность EN/RU субтитров, корректность избранного и RPC SM-2, работа сетевых API. Доступность внешних провайдеров зависит от региона и их API. DDGS использует по умолчанию `http://10.0.2.2:4479` (Android-эмулятор); cleartext HTTP разрешён только для localhost/эмулятора через Android Network Security Config. Прочие HTTP-адреса следует публиковать через HTTPS. Не храните приватные API-ключи в репозитории.

## Архитектура

- `CineApi.kt` — модели, доступ к Supabase REST/Auth/RPC, YouTube, Kinopoisk, DDGS, Archive.org, Rivestream, словарь и парсер субтитров.
- `YoutubeCaptions.kt` — получение captionTracks/json3 с YouTube watch page.
- `CineState.kt` — общее состояние приложения и загрузка данных.
- `MainActivity.kt` — приложение, темы и навигация.
- `Screens.kt` — каталог, профиль, словарь и SRS.
- `PlayerScreen.kt` — видео и синхронизированные субтитры.
- `Exercises.kt` — тренажёры.

Реализация рассчитана на Android 7.0+ (API 24), minSdk 24.
