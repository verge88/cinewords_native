# Flutter → Jetpack Compose: визуальное соответствие

Этот документ сравнивает реальные файлы Flutter с экранами Compose. Цель — визуально близкая структура и интерактивность без Flutter runtime. Точное 1:1 соответствие пикселей пока не подтверждено на устройстве.

| Экран | Flutter — источник | Compose — реализация | Перенесено |
| --- | --- | --- | --- |
| Тема | lib/theme/app_theme.dart | CineTheme.kt | Seed #6750A4, акценты #00BFA5 и #FFB74D, dynamic color (Android 12+), радиусы 16/24/32 |
| Основные вкладки | lib/screens/home/home_screen.dart | MainActivity.kt | 5 вкладок: Главная, Видео, Фильмы, Слова, Профиль |
| Главная | lib/screens/home/home_screen.dart | CineHome.kt | Градиентный hero, аватар, кольцо дневной цели, метрики, сетка быстрого доступа 2×2, план дня, полки видео, добавление URL |
| Видеокарточки | lib/widgets/video_card.dart | CineHome.kt (CineVideoCard) | Большое превью 16:9, метки сложности/длительности, избранное, метаданные под превью |
| Фильмы | lib/screens/catalog/movie_catalog_screen.dart | Screens.kt | Сетка постеров 2 колонки, переключение источника, поиск |
| Детали фильма | lib/screens/catalog/movie_detail_screen.dart | Screens.kt | Большой постер с градиентом, название и описание, кнопка просмотра |
| Слова | lib/screens/vocabulary/vocabulary_screen.dart | Screens.kt | New/Learning/Review/Mastered, Words/Phrases, вкладки, карточки, SRS |
| Профиль | lib/screens/profile/profile_screen.dart; lib/widgets/stats_bento.dart | CineProfile.kt | Карточка пользователя, bento 2×2, кольцо дневной цели, настройки |
| Упражнения | lib/screens/exercises/exercises_hub_screen.dart | Exercises.kt | Четыре группы, цветные иконки, крупные скруглённые плитки |
| Вход | lib/screens/auth/login_screen.dart | MainActivity.kt; res/drawable/cinewords_login_logo.xml | Вектор из Flutter SVG, Welcome back!, поля с иконками, Sign In / Create Account |
| Плеер | lib/screens/player/player_screen.dart | PlayerScreen.kt | Видео 16:9, EN/RU overlay, транскрипт, управление скоростью; компоновка ещё отличается |

## Контроль визуального качества

- Светлая, тёмная, системная и монохромная темы. Dynamic color зависит от Android и обоев.
- Экран 360–400dp: hero, плитки 2×2, пять табов, постеры 2 колонки.
- Увеличенный системный шрифт 1.3×/1.5× без обрезания текста.
- Пустые/ошибочные состояния медиатеки, словаря и внешних API.
- Плеер, EN/RU субтитры, горизонтальная ориентация, TalkBack.
- Визуальное сравнение на одинаковых данных Flutter/Compose.

## Не устранённые различия

- Flutter использует Plus Jakarta Sans для заголовков и Inter для текста; Compose пока использует системный Sans с близкими метриками. Шрифты не встроены в APK.
- Анимации Flutter Animate отсутствуют в нескольких Compose-переходах.
- В Flutter есть плавающие кнопки управления плеером и собственный полноэкранный маршрут; нативный плеер пока проще.
- Некоторые элементы словаря, адаптивные состояния и интерактивные переходы ещё требуют доработки и screenshot/golden-тестов.

До сравнения экранов на Android-устройстве не считать визуальный перенос завершённым 1:1.
