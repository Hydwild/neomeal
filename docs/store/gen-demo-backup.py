# -*- coding: utf-8 -*-
"""
Генератор демо-бэкапа NeoMeal для скриншотов витрины магазина.

Зачем: на свежей установке дневник пуст, снимать нечего. Скрипт делает файл
бэкапа, который штатно импортируется через «Настройки → Данные → Импорт».

Особенности:
  * берёт РЕАЛЬНЫЕ продукты из ProductDatabase.kt, поэтому КБЖУ сходятся
    с базой приложения, а названия переводятся штатным словарём;
  * калорийность каждого дня попадает в коридор стрика 85–110 % нормы,
    поэтому на главном экране видна серия дней;
  * random.seed фиксирован — повторный запуск даёт тот же набор блюд,
    сдвинутый на актуальные даты.

ВАЖНО: даты «вморожены» в файл относительно дня генерации. Перед съёмкой
скриншотов запустите скрипт заново, иначе последние дни окажутся в прошлом
и серия прервётся.

Запуск:  python docs/store/gen-demo-backup.py
"""
import re, io, json, random, datetime, os

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "app", "src", "main", "java", "com", "nutritrack",
                   "app", "data", "ProductDatabase.kt")
OUT = os.path.join(ROOT, "docs", "store", "demo-backup.json")

random.seed(20260803)

DAYS = 14          # дней истории (7 — если нужна ровно неделя)
PROFILE_ID = 1

# ─── профиль ───────────────────────────────────────────────────────────────
# Mifflin-St Jeor: BMR = 10*82 + 6.25*180 - 5*30 + 5 = 1800
# TDEE = 1800 * 1.55 (moderate) = 2790 ; goal "lose" → 2790 - 500 = 2290 ккал
PROFILE = {
    "id": PROFILE_ID, "name": "Алексей", "age": 30,
    "height": 180.0, "weight": 82.0, "gender": "male",
    "goal": "lose", "targetWeight": 75.0, "activityLevel": "moderate",
    "isActive": True, "allergens": "", "diets": "",
}
CAL_GOAL = 2290
WATER_GOAL = int(82 * 35)  # 2870 мл

# ─── чтение реальных продуктов ─────────────────────────────────────────────
src = io.open(SRC, encoding="utf-8").read()
rows = re.findall(
    r'\bp\(\s*"([^"]+)",\s*([0-9.]+)f?,\s*([0-9.]+)f?,\s*([0-9.]+)f?,\s*([0-9.]+)f?,\s*"([^"]+)"',
    src)
BY_CAT = {}
for name, cal, pr, fat, cb, cat in rows:
    BY_CAT.setdefault(cat, []).append({
        "name": name, "cal": float(cal), "prot": float(pr),
        "fat": float(fat), "carb": float(cb), "cat": cat,
    })


def pick(cat, lo=0, hi=10000):
    """Случайный продукт категории с калорийностью на 100 г в диапазоне."""
    pool = [p for p in BY_CAT.get(cat, []) if lo <= p["cal"] <= hi]
    if not pool:
        pool = BY_CAT.get(cat, [])
    return random.choice(pool)


# Состав дня: (приём, продукт, базовые граммы, максимум граммов).
# Базовая сумма подобрана ~2150 ккал, чтобы масштаб к норме был около 1.0
# и порции оставались реалистичными (масло не превращалось в 30 г и т.п.).
def compose_day():
    return [
        # завтрак ≈ 590
        ("breakfast", pick("Крупы",   320, 400),  60,  90),
        ("breakfast", pick("Яйца",     60, 200), 100, 140),
        ("breakfast", pick("Хлеб",    200, 300),  50,  80),
        ("breakfast", pick("Фрукты",   30,  90), 130, 200),
        # обед ≈ 760
        ("lunch",     pick("Птица",   100, 200), 180, 240),
        ("lunch",     pick("Крупы",   320, 400),  70, 100),
        ("lunch",     pick("Овощи",    10,  60), 200, 280),
        ("lunch",     pick("Масла",   800, 900),  10,  15),
        # ужин ≈ 510
        ("dinner",    pick("Рыба",     80, 250), 180, 240),
        ("dinner",    pick("Овощи",    10,  60), 200, 280),
        ("dinner",    pick("Бобовые", 100, 400),  50,  80),
        # перекус ≈ 290
        ("snack",     pick("Орехи",   500, 700),  25,  40),
        ("snack",     pick("Молочные", 40, 130), 150, 220),
    ]


def macros(p, grams):
    k = grams / 100.0
    return (round(p["cal"] * k, 1), round(p["prot"] * k, 1),
            round(p["fat"] * k, 1), round(p["carb"] * k, 1))


today = datetime.date.today()
diary, water, weight, fasting = [], [], [], []
did = wid = gid = fid = 1

for back in range(DAYS - 1, -1, -1):
    day = today - datetime.timedelta(days=back)
    ds = day.strftime("%Y-%m-%d")

    items = compose_day()
    base_total = sum(macros(p, g)[0] for _, p, g, _ in items)
    # Целимся в 88–107 % нормы: коридор стрика 85–110 %, с запасом на округления
    target = CAL_GOAL * random.uniform(0.88, 1.07)
    scale = target / base_total if base_total else 1.0

    for meal, p, g, gmax in items:
        # округляем до 5 г и не даём порции уйти за разумный максимум
        grams = min(gmax, max(10, int(round(g * scale / 5.0)) * 5))
        cal, prot, fat, carb = macros(p, grams)
        hour = {"breakfast": 8, "lunch": 13, "dinner": 19, "snack": 16}[meal]
        ts = int(datetime.datetime.combine(
            day, datetime.time(hour, random.randint(0, 50))).timestamp() * 1000)
        diary.append({
            "id": did, "productId": 0, "productName": p["name"],
            "grams": float(grams), "calories": cal, "protein": prot,
            "fat": fat, "carbs": carb, "date": ds, "mealType": meal,
            "profileId": PROFILE_ID, "timestamp": ts,
            "fiber": round(carb * random.uniform(0.05, 0.12), 1),
            "sugar": round(carb * random.uniform(0.10, 0.30), 1),
        })
        did += 1

    # вода: подходы по 200–300 мл, сумма около нормы
    left = int(WATER_GOAL * random.uniform(0.82, 1.06))
    hour = 8
    while left > 150 and hour < 23:
        amt = min(random.choice([200, 250, 250, 300]), left)
        ts = int(datetime.datetime.combine(
            day, datetime.time(hour, random.randint(0, 55))).timestamp() * 1000)
        water.append({"id": wid, "amountMl": amt, "date": ds,
                      "profileId": PROFILE_ID, "timestamp": ts})
        wid += 1
        left -= amt
        hour += random.randint(1, 2)

    # вес: раз в 3 дня, плавное снижение 84.6 → 82.0
    if back % 3 == 0:
        progress = (DAYS - 1 - back) / float(DAYS - 1)
        w = 84.6 - progress * 2.6 + random.uniform(-0.25, 0.25)
        weight.append({"id": gid, "weight": round(w, 1), "date": ds,
                       "profileId": PROFILE_ID})
        gid += 1

    # голодание 16:8 — три завершённые сессии за период
    if back in (2, 6, 10):
        start = datetime.datetime.combine(day - datetime.timedelta(days=1),
                                          datetime.time(20, 0))
        end = datetime.datetime.combine(day, datetime.time(12, 0))
        fasting.append({
            "id": fid, "profileId": PROFILE_ID,
            "startTime": int(start.timestamp() * 1000),
            "endTime": int(end.timestamp() * 1000),
            "targetHours": 16, "protocolName": "16:8",
            "isCompleted": True, "durationMinutes": 16 * 60,
        })
        fid += 1

# Перечисляем ВСЕ поля BackupData: Gson не применяет Kotlin-дефолты, и
# пропущенный список стал бы null — импорт упал бы на data.avatars.isNotEmpty().
backup = {
    "version": 1,
    "exportedAt": today.strftime("%Y-%m-%d 09:30"),
    # Пустой список: импорт чистит таблицу продуктов, а NutriApp при следующем
    # запуске заливает встроенную базу заново (count < 50 → repopulate).
    "products": [],
    "diary": diary,
    "water": water,
    "weight": weight,
    "profiles": [PROFILE],
    "achievements": [],   # приложение пересчитает само
    "fasting": fasting,
    "mealPlans": [],
    "mealItems": [],
    "dailyGoals": [],
    "fitness": [],
    "settings": {
        "dark_theme": "true",
        "language": "ru",
        "circular_calorie_mode": "true",
        "meal_templates_json": "",
    },
    "avatars": {},
    "measurements": [],
    "progressPhotos": [],
}

# ─── совместимость с release-сборкой ───────────────────────────────────────
# BackupData лежит в com.nutritrack.app.utils и НЕ подпадает под keep-правила,
# поэтому R8 переименовывает его поля: products → c, diary → d, avatars → o…
# Gson читает JSON по именам полей, значит в release-сборке файл с настоящими
# именами даёт сплошные null и падает на data.avatars.isNotEmpty().
# Поля самих сущностей R8 не трогает (-keep ...data.db.entity.**), их не дублируем.
#
# Пишем ОБА набора ключей: release возьмёт короткие, debug — длинные,
# лишние ключи Gson молча игнорирует.
MAPPING = os.path.join(ROOT, "app", "build", "outputs", "mapping", "release", "mapping.txt")


def backup_data_aliases():
    if not os.path.exists(MAPPING):
        return {}
    t = io.open(MAPPING, encoding="utf-8", errors="ignore").read()
    marker = "com.nutritrack.app.utils.BackupManager$BackupData ->"
    if marker not in t:
        return {}
    out = {}
    lines = t[t.index(marker):].split("\n")[1:]  # строки после заголовка класса
    for line in lines:
        s = line.strip()
        if not s:
            continue
        # Комментарии R8 идут без отступа — пропускаем их ДО проверки на конец блока
        if s.startswith("#"):
            continue
        # Конец блока класса: следующая строка без отступа
        if not line.startswith(" "):
            break
        # Поле: "<тип> <имя> -> <короткое>" (у методов есть скобки и префикс "N:M:")
        m = re.match(r'^[\w.$\[\]]+\s+(\w+)\s+->\s+(\w+)$', s)
        if m and m.group(1) != m.group(2):
            out[m.group(1)] = m.group(2)
    return out


aliases = backup_data_aliases()
for orig, short in aliases.items():
    if orig in backup:
        backup[short] = backup[orig]

os.makedirs(os.path.dirname(OUT), exist_ok=True)
io.open(OUT, "w", encoding="utf-8", newline="\n").write(
    json.dumps(backup, ensure_ascii=False, indent=2))

# ─── сводка (ASCII, чтобы не ломалась консоль Windows) ─────────────────────
by_day = {}
for e in diary:
    by_day[e["date"]] = by_day.get(e["date"], 0) + e["calories"]
in_band = sum(1 for v in by_day.values() if 0.85 <= v / CAL_GOAL <= 1.10)
print("file      :", OUT)
print("size      : %.1f KB" % (os.path.getsize(OUT) / 1024.0))
print("goal kcal :", CAL_GOAL, " water:", WATER_GOAL)
print("period    : %s .. %s" % (min(by_day), max(by_day)))
print("counts    : diary=%d water=%d weight=%d fasting=%d" %
      (len(diary), len(water), len(weight), len(fasting)))
print("streak    : %d / %d days in band" % (in_band, len(by_day)))
print("kcal/day  : %d .. %d" % (min(by_day.values()), max(by_day.values())))
if aliases:
    print("r8 aliases: %d added for an OLD release build, e.g. %s" %
          (len(aliases), ", ".join("%s->%s" % kv for kv in sorted(aliases.items())[:4])))
elif os.path.exists(MAPPING):
    print("r8 aliases: not needed - BackupData field names are kept (proguard-rules.pro)")
else:
    print("r8 aliases: mapping.txt not found (release not built yet) - fine as long as")
    print("            the installed APK is built with the current proguard-rules.pro")
