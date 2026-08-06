# 🥗 NeoMeal — трекер питания для Android

> **EN:** NeoMeal is a full-featured Android nutrition tracker: food diary with
> barcode scanning and voice input, water/weight/body measurements, intermittent
> fasting timer, meal planner with an auto-generated shopping list, gamification
> (pets, XP, streaks), encrypted database, Health Connect two-way sync, Material
> You theming and RU/EN/KO localization. Source is published **for portfolio
> purposes only** — see [LICENSE](LICENSE).

Приложение для контроля питания: дневник еды, вода, вес, замеры тела,
интервальное голодание, план питания и аналитика — с геймификацией
(питомцы-компаньоны, уровни, серии дней) и упором на приватность данных.

**Стек:** Kotlin · MVVM · Room (SQLCipher) · Coroutines · Navigation ·
ViewBinding · WorkManager · Retrofit (Open Food Facts) · CameraX + ML Kit ·
Health Connect · Material Components

---

## Возможности

- 🍽️ **Дневник питания** — поиск по локальной базе (700+ продуктов) и Open
  Food Facts, сканер штрихкодов (CameraX + ML Kit), голосовой ввод
  («двести грамм гречки»), быстрые чипы «повторить вчера»
- 💧 **Вода** — стаканы, «живой» режим с анимированным питомцем, умные
  напоминания по привычному времени питья
- 📊 **Аналитика** — КБЖУ + клетчатка/сахар, витамины и минералы с % от
  нормы, графики веса и калорий, недельный отчёт с инсайтами,
  PDF-экспорт на 2 страницы
- 🗓️ **План питания** — конструктор меню под цель (масса/форма/похудение),
  напоминания, 🛒 **список покупок**: блюда автоматически раскрываются до
  ингредиентов с суммированием («200 г», «2 шт», «1 ст.л.»)
- ⏱️ **Интервальное голодание** — протоколы 16:8 и другие, фазы (кетоз,
  аутофагия), виджет-таймер
- 🏆 **Геймификация** — достижения, XP и уровни, питомцы-компаньоны,
  серии дней с заморозками и режимом отпуска
- ❤️ **Health Connect** — чтение шагов/пульса/веса и запись питания, воды
  и веса для других приложений здоровья (opt-in)
- 🎨 **Material You** — семантические токены темы, цвета из обоев
  (приложение и виджеты), тёмная тема, выбор шрифта и фона
- 🌐 **Локализация** — русский, английский, корейский (3500+ строк)

## Приватность и безопасность

- База данных зашифрована **SQLCipher**; ключ — случайные 32 байта в
  `EncryptedSharedPreferences` под мастер-ключом **Android Keystore**
- Резервные копии — опционально шифруются **AES-256-GCM** (ключ из пароля
  через PBKDF2, 150k итераций)
- Биометрическая блокировка с re-lock при возврате из фона и `FLAG_SECURE`
- Только HTTPS (network security config), все PendingIntent — immutable
- Данные не покидают устройство (кроме опциональной записи в Health Connect
  и анонимных крашей Firebase Crashlytics)

## Инженерные детали

- **Миграции Room** (20 версий схемы) с экспортом схем и тестами миграций
  на реальных данных (`MigrationTestHelper`)
- **KSP** вместо kapt, R8 minify+shrink в release
- Семантические **токены темы** (`?attr/app*`) вместо прямых цветов —
  510 замен в 60 layout'ах, Material You как ThemeOverlay поверх токенов
- Компонентная декомпозиция экранов (bottom-sheet добавления еды и карточка
  воды — отдельные контроллеры), репозитории для данных и сети
- Юнит-тесты калькулятора калорий, фильтра диет, расчёта серий

## Сборка

```bash
# Debug-сборка (нужен JDK 17 и Android SDK 35)
./gradlew :app:assembleDebug
```

Для сборки нужны `google-services.json` (Firebase) и `keystore.properties`
(release-подпись) — в репозитории их нет, оба привязаны к конкретному
аккаунту. Рядом лежат шаблоны с пояснениями:
`app/google-services.json.example` и `keystore.properties.example`.

Firebase используется **только** для Crashlytics и только с согласия
пользователя; без него debug-сборка работает — достаточно убрать плагин
в `app/build.gradle`. Release-подпись нужна лишь для сборки APK на
публикацию, для чтения кода и debug-сборки она не требуется.

## Лицензия

Код опубликован **только как портфолио**: читать можно, использовать,
копировать и публиковать (в том числе под другим брендом) — нельзя.
Подробности — в [LICENSE](LICENSE).

Copyright © 2026 Hydwild
