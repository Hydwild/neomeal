#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Проверка результата R8 для классов, которые Gson читает по рефлексии.

Зачем. Дважды подряд функция работала в debug и была сломана в release:
  * восстановление из бэкапа — R8 переименовал поля BackupData
    (products → c), Gson не находил их по именам;
  * шаблоны приёмов и свои рецепты — R8 срезал generic-сигнатуру у
    анонимных `object : TypeToken<List<X>>() {}` и слил их в один класс,
    Gson бросал исключение, load() молча возвращал пустой список.
Юнит-тесты гоняются на неминифицированном коде и такое не ловят в принципе.
Скрипт смотрит на то, что реально уходит пользователям.

Проверки:
  1. У классов из GSON_CLASSES поля в mapping.txt не переименованы.
  2. У каждого класса приложения, наследующего TypeToken, в dex есть
     аннотация dalvik.annotation.Signature (иначе Gson не узнает тип).

Запуск после сборки релизного R8 (достаточно задачи minifyReleaseWithR8):
    ./gradlew :app:minifyReleaseWithR8
    python3 scripts/check_r8_gson.py

Код возврата 0 — всё в порядке, 1 — найдены нарушения.
"""
import glob
import io
import os
import re
import subprocess
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
MAPPING = os.path.join(ROOT, "app", "build", "outputs", "mapping", "release", "mapping.txt")
DEX_DIR = os.path.join(ROOT, "app", "build", "intermediates", "dex", "release", "minifyReleaseWithR8")

# Классы, которые Gson (де)сериализует по именам полей. Добавляя новый —
# добавьте сюда и keep-правило в app/proguard-rules.pro.
GSON_CLASSES = [
    "com.nutritrack.app.utils.BackupManager$BackupData",
    "com.nutritrack.app.utils.BackupManager$Envelope",
    "com.nutritrack.app.utils.MealTemplates$Template",
    "com.nutritrack.app.utils.MealTemplates$Item",
    "com.nutritrack.app.data.Recipe",
    "com.nutritrack.app.data.RecipeIngredient",
]

TYPE_TOKEN = "Lcom/google/gson/reflect/TypeToken;"
APP_PREFIX = "Lcom/nutritrack/"


def find_dexdump():
    exe = "dexdump.exe" if os.name == "nt" else "dexdump"
    homes = [os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
             os.path.join(os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk")]
    for home in filter(None, homes):
        found = sorted(glob.glob(os.path.join(home, "build-tools", "*", exe)))
        if found:
            return found[-1]  # самая свежая версия build-tools
    return None


def renamed_fields(mapping_text, cls):
    """Список «поле->новое имя» для класса; None — класса нет в маппинге."""
    m = re.search(r"^" + re.escape(cls) + r" -> (\S+):$", mapping_text, re.M)
    if not m:
        return None
    out = []
    for line in mapping_text[m.end():].split("\n")[1:]:
        s = line.strip()
        if not s or s.startswith("#"):
            continue
        if not line.startswith(" "):
            break  # начался следующий класс
        f = re.match(r"^[\w.$\[\]]+\s+(\w+)\s+->\s+(\w+)$", s)
        if f and f.group(1) != f.group(2):
            out.append("%s->%s" % (f.group(1), f.group(2)))
    return out


def dex_classes(dexdump, dex_files):
    """{дескриптор: (суперкласс, есть_ли_Signature)} по всем dex."""
    result = {}
    for dex in dex_files:
        text = subprocess.run([dexdump, "-a", dex], capture_output=True,
                              text=True, encoding="utf-8", errors="ignore").stdout
        # dexdump печатает блок аннотаций класса ПЕРЕД его заголовком
        blocks = re.split(r"(?=^Class #\d+)", text, flags=re.M)
        pending_annotations = ""
        for b in blocks:
            if "Class descriptor  :" not in b:
                pending_annotations += b
                continue
            desc = re.search(r"Class descriptor  : '([^']+)'", b).group(1)
            sup = re.search(r"Superclass        : '([^']+)'", b)
            # аннотации на сам класс: блок "Annotations on class" до первого
            # "Annotations on field/method"
            ann = pending_annotations + b
            cls_ann = ann.split("Annotations on field")[0].split("Annotations on method")[0]
            has_sig = "Ldalvik/annotation/Signature;" in cls_ann
            result[desc] = (sup.group(1) if sup else "", has_sig)
            pending_annotations = ""
    return result


def main():
    errors = []

    if not os.path.isfile(MAPPING):
        print("Нет %s — сначала соберите: ./gradlew :app:minifyReleaseWithR8" % MAPPING)
        return 1
    mapping = io.open(MAPPING, encoding="utf-8", errors="ignore").read()

    print("1) Поля классов, которые читает Gson:")
    for cls in GSON_CLASSES:
        r = renamed_fields(mapping, cls)
        if r is None:
            # Класс удалён/вшит R8 — значит, Gson работает с тем, чего нет
            errors.append("%s отсутствует в маппинге" % cls)
            print("   FAIL %s — нет в маппинге" % cls)
        elif r:
            errors.append("%s: переименованы поля %s" % (cls, r))
            print("   FAIL %s — %s" % (cls, ", ".join(r)))
        else:
            print("   ok   %s" % cls)

    print("2) Generic-сигнатуры наследников TypeToken:")
    dexdump = find_dexdump()
    dex_files = sorted(glob.glob(os.path.join(DEX_DIR, "classes*.dex")))
    if not dexdump:
        errors.append("не найден dexdump (ANDROID_HOME/build-tools)")
        print("   FAIL dexdump не найден")
    elif not dex_files:
        errors.append("нет dex в %s" % DEX_DIR)
        print("   FAIL нет dex в %s" % DEX_DIR)
    else:
        classes = dex_classes(dexdump, dex_files)
        tokens = {d: sig for d, (sup, sig) in classes.items()
                  if sup == TYPE_TOKEN and d.startswith(APP_PREFIX)}
        if not tokens:
            # В приложении есть TypeToken — если наследников нет, их слили/удалили
            errors.append("наследники TypeToken в dex не найдены")
            print("   FAIL наследники TypeToken не найдены")
        for d, sig in sorted(tokens.items()):
            print("   %s %s" % ("ok  " if sig else "FAIL", d))
            if not sig:
                errors.append("%s: нет generic-сигнатуры" % d)

    if errors:
        print("\nНАРУШЕНИЯ (%d) — в релизе Gson не сможет прочитать данные:" % len(errors))
        for e in errors:
            print("  - " + e)
        print("Поправьте app/proguard-rules.pro.")
        return 1
    print("\nOK: R8 сохранил всё, что нужно Gson.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
