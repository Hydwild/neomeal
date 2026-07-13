package com.nutritrack.app.data

/**
 * Статичная база рецептов / составных блюд.
 * Не в Room — это справочник (как база продуктов).
 *
 * goal: для какой цели подходит блюдо (может подходить нескольким):
 * "mass" — набор массы (атлеты)
 * "fit" — поддержание формы
 * "lose" — снижение веса
 * "light" — лёгкое питание (мягкие продукты)
 *
 * mealType: "breakfast" | "lunch" | "dinner" | "snack"
 */
data class Recipe(
    val id: String,
    val name: String,
    val emoji: String,
    val goals: List<String>,
    val mealTypes: List<String>,
    val servings: Int,            // на сколько порций рассчитан рецепт
    // КБЖУ на ВСЕ порции
    val calories: Int,
    val protein: Int,
    val fat: Int,
    val carbs: Int,
    val timeMinutes: Int,
    val ingredients: List<RecipeIngredient>,
    val steps: List<String>,
    val tip: String? = null,
    val lenten: Boolean = false   // постное (растительное) блюдо — без мяса, рыбы, яиц, молочного
) {
    // КБЖУ на 1 порцию
    val caloriesPerServing get() = calories / servings
    val proteinPerServing  get() = protein / servings
    val fatPerServing      get() = fat / servings
    val carbsPerServing    get() = carbs / servings
}

data class RecipeIngredient(
    val name: String,
    val amount: String   // "200 г", "2 шт", "1 ст.л."
)

object RecipeData {

    val all: List<Recipe> = listOf(

        // НАБОР МАССЫ (атлеты)
        Recipe(
            id = "mass_oatmeal_gainer",
            name = "Овсянка-гейнер с бананом и арахисовой пастой",
            emoji = "🥣",
            goals = listOf("mass", "fit"),
            mealTypes = listOf("breakfast"),
            servings = 1,
            calories = 720, protein = 28, fat = 24, carbs = 95,
            timeMinutes = 10,
            ingredients = listOf(
                RecipeIngredient("Овсяные хлопья", "100 г"),
                RecipeIngredient("Молоко 2.5%", "300 мл"),
                RecipeIngredient("Банан", "1 шт"),
                RecipeIngredient("Арахисовая паста", "1 ст.л."),
                RecipeIngredient("Мёд", "1 ч.л."),
                RecipeIngredient("Протеин (по желанию)", "1 мерная ложка")
            ),
            steps = listOf(
                "Залей овсянку молоком, доведи до кипения и вари 5 минут на слабом огне.",
                "Сними с огня, дай настояться 2 минуты под крышкой.",
                "Добавь нарезанный банан, арахисовую пасту и мёд.",
                "Если используешь протеин — добавь, когда каша немного остынет, и размешай."
            ),
            tip = "Отличный высококалорийный завтрак для набора массы."
        ),
        Recipe(
            id = "mass_chicken_rice",
            name = "Куриная грудка с рисом и овощами",
            emoji = "🍗",
            goals = listOf("mass", "fit"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 1,
            calories = 650, protein = 50, fat = 12, carbs = 80,
            timeMinutes = 30,
            ingredients = listOf(
                RecipeIngredient("Куриная грудка", "200 г"),
                RecipeIngredient("Рис (сухой)", "80 г"),
                RecipeIngredient("Брокколи", "150 г"),
                RecipeIngredient("Оливковое масло", "1 ст.л."),
                RecipeIngredient("Соль, специи", "по вкусу")
            ),
            steps = listOf(
                "Отвари рис согласно инструкции на упаковке.",
                "Нарежь грудку, обжарь на оливковом масле до готовности (8-10 мин).",
                "Брокколи отвари или приготовь на пару 5-7 минут.",
                "Подавай всё вместе, посоли и приправь по вкусу."
            ),
            tip = "Классическое блюдо для роста мышц: много белка и сложных углеводов."
        ),
        Recipe(
            id = "mass_cottage_shake",
            name = "Творожно-банановый коктейль",
            emoji = "🥤",
            goals = listOf("mass", "fit"),
            mealTypes = listOf("snack"),
            servings = 1,
            calories = 420, protein = 35, fat = 9, carbs = 50,
            timeMinutes = 5,
            ingredients = listOf(
                RecipeIngredient("Творог 5%", "200 г"),
                RecipeIngredient("Банан", "1 шт"),
                RecipeIngredient("Молоко", "200 мл"),
                RecipeIngredient("Овсяные хлопья", "30 г"),
                RecipeIngredient("Мёд", "1 ч.л.")
            ),
            steps = listOf(
                "Сложи все ингредиенты в блендер.",
                "Взбей до однородности 30-40 секунд.",
                "Перелей в стакан и сразу пей."
            ),
            tip = "Удобный белковый перекус между приёмами пищи."
        ),

        // ПОДДЕРЖАНИЕ ФОРМЫ
        Recipe(
            id = "fit_eggs_toast",
            name = "Яичница с цельнозерновым тостом и авокадо",
            emoji = "🍳",
            goals = listOf("fit", "mass"),
            mealTypes = listOf("breakfast"),
            servings = 1,
            calories = 420, protein = 22, fat = 26, carbs = 26,
            timeMinutes = 10,
            ingredients = listOf(
                RecipeIngredient("Яйца", "2 шт"),
                RecipeIngredient("Цельнозерновой хлеб", "2 ломтика"),
                RecipeIngredient("Авокадо", "1/2 шт"),
                RecipeIngredient("Оливковое масло", "1 ч.л."),
                RecipeIngredient("Соль, перец", "по вкусу")
            ),
            steps = listOf(
                "Поджарь хлеб в тостере или на сухой сковороде.",
                "Приготовь яичницу на оливковом масле.",
                "Разомни авокадо вилкой, намажь на тосты, посоли.",
                "Выложи яичницу сверху или рядом."
            )
        ),
        Recipe(
            id = "fit_salmon_quinoa",
            name = "Лосось с киноа и овощами",
            emoji = "🐟",
            goals = listOf("fit", "lose", "dry"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 1,
            calories = 520, protein = 38, fat = 22, carbs = 40,
            timeMinutes = 25,
            ingredients = listOf(
                RecipeIngredient("Филе лосося", "150 г"),
                RecipeIngredient("Киноа (сухая)", "60 г"),
                RecipeIngredient("Огурец", "1 шт"),
                RecipeIngredient("Помидоры черри", "100 г"),
                RecipeIngredient("Лимон", "1/2 шт"),
                RecipeIngredient("Оливковое масло", "1 ч.л.")
            ),
            steps = listOf(
                "Отвари киноа (примерно 15 минут).",
                "Запеки лосось в духовке 12-15 мин при 200°C или обжарь.",
                "Нарежь огурец и черри, заправь маслом и лимоном.",
                "Подавай лосось с киноа и салатом."
            ),
            tip = "Богато белком и полезными омега-3 жирами."
        ),
        Recipe(
            id = "fit_greek_yogurt",
            name = "Греческий йогурт с ягодами и орехами",
            emoji = "🫐",
            goals = listOf("fit", "lose", "light"),
            mealTypes = listOf("snack", "breakfast"),
            servings = 1,
            calories = 280, protein = 18, fat = 12, carbs = 25,
            timeMinutes = 3,
            ingredients = listOf(
                RecipeIngredient("Греческий йогурт", "200 г"),
                RecipeIngredient("Ягоды (любые)", "80 г"),
                RecipeIngredient("Грецкие орехи", "15 г"),
                RecipeIngredient("Мёд", "1 ч.л.")
            ),
            steps = listOf(
                "Выложи йогурт в миску.",
                "Добавь ягоды и измельчённые орехи.",
                "Полей мёдом."
            )
        ),

        // СНИЖЕНИЕ ВЕСА
        Recipe(
            id = "lose_veggie_omelet",
            name = "Белковый омлет с овощами",
            emoji = "🍳",
            goals = listOf("lose", "fit"),
            mealTypes = listOf("breakfast"),
            servings = 1,
            calories = 240, protein = 24, fat = 12, carbs = 8,
            timeMinutes = 12,
            ingredients = listOf(
                RecipeIngredient("Яйца", "1 шт"),
                RecipeIngredient("Яичные белки", "3 шт"),
                RecipeIngredient("Шпинат", "50 г"),
                RecipeIngredient("Помидор", "1 шт"),
                RecipeIngredient("Соль, перец", "по вкусу")
            ),
            steps = listOf(
                "Взбей яйцо с белками, посоли.",
                "На сковороде с антипригарным покрытием потуши шпинат и помидор 2 мин.",
                "Залей яичной смесью, готовь под крышкой 4-5 минут."
            ),
            tip = "Много белка, мало калорий — отлично насыщает."
        ),
        Recipe(
            id = "lose_chicken_salad",
            name = "Салат с куриной грудкой и овощами",
            emoji = "🥗",
            goals = listOf("lose", "fit"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 1,
            calories = 320, protein = 35, fat = 14, carbs = 14,
            timeMinutes = 20,
            ingredients = listOf(
                RecipeIngredient("Куриная грудка", "150 г"),
                RecipeIngredient("Листья салата", "100 г"),
                RecipeIngredient("Огурец", "1 шт"),
                RecipeIngredient("Помидоры черри", "100 г"),
                RecipeIngredient("Оливковое масло", "1 ч.л."),
                RecipeIngredient("Лимонный сок", "1 ст.л.")
            ),
            steps = listOf(
                "Отвари или обжарь грудку без масла, нарежь.",
                "Нарежь овощи, порви салат руками.",
                "Смешай, заправь маслом и лимонным соком."
            )
        ),
        Recipe(
            id = "lose_veg_soup",
            name = "Овощной суп с курицей",
            emoji = "🥘",
            goals = listOf("lose", "light"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 4,
            calories = 720, protein = 60, fat = 16, carbs = 76,
            timeMinutes = 40,
            ingredients = listOf(
                RecipeIngredient("Куриная грудка", "300 г"),
                RecipeIngredient("Картофель", "2 шт"),
                RecipeIngredient("Морковь", "1 шт"),
                RecipeIngredient("Лук", "1 шт"),
                RecipeIngredient("Кабачок", "1 шт"),
                RecipeIngredient("Зелень, соль", "по вкусу")
            ),
            steps = listOf(
                "Отвари грудку, достань, нарежь, бульон оставь.",
                "Добавь в бульон нарезанные картофель и морковь, вари 15 мин.",
                "Добавь лук и кабачок, вари ещё 10 минут.",
                "Верни курицу, посоли, добавь зелень."
            ),
            tip = "Лёгкий и сытный суп. Рассчитан на 4 порции."
        ),

        // ЛЁГКОЕ ПИТАНИЕ (мягкие продукты)
        Recipe(
            id = "light_rice_porridge",
            name = "Рисовая каша на воде",
            emoji = "🍚",
            goals = listOf("light"),
            mealTypes = listOf("breakfast"),
            servings = 1,
            calories = 230, protein = 5, fat = 3, carbs = 47,
            timeMinutes = 25,
            ingredients = listOf(
                RecipeIngredient("Рис", "60 г"),
                RecipeIngredient("Вода", "300 мл"),
                RecipeIngredient("Соль", "щепотка")
            ),
            steps = listOf(
                "Промой рис до прозрачной воды.",
                "Залей водой, доведи до кипения.",
                "Вари на слабом огне 20 минут до мягкости.",
                "Слегка посоли."
            ),
            tip = "Мягкая, нейтральная еда, комфортная для многих. Не лечебная диета."
        ),
        Recipe(
            id = "light_banana_oat",
            name = "Овсянка на воде с бананом",
            emoji = "🍌",
            goals = listOf("light", "lose"),
            mealTypes = listOf("breakfast"),
            servings = 1,
            calories = 280, protein = 8, fat = 4, carbs = 56,
            timeMinutes = 10,
            ingredients = listOf(
                RecipeIngredient("Овсяные хлопья", "60 г"),
                RecipeIngredient("Вода", "250 мл"),
                RecipeIngredient("Банан", "1 шт")
            ),
            steps = listOf(
                "Залей овсянку водой, доведи до кипения.",
                "Вари 5 минут, дай настояться.",
                "Добавь размятый банан."
            )
        ),
        Recipe(
            id = "light_steamed_fish",
            name = "Рыба на пару с картофельным пюре",
            emoji = "🐟",
            goals = listOf("light", "fit"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 1,
            calories = 360, protein = 32, fat = 8, carbs = 40,
            timeMinutes = 30,
            ingredients = listOf(
                RecipeIngredient("Филе белой рыбы (треска, минтай)", "180 г"),
                RecipeIngredient("Картофель", "200 г"),
                RecipeIngredient("Молоко", "50 мл"),
                RecipeIngredient("Соль", "по вкусу")
            ),
            steps = listOf(
                "Приготовь рыбу на пару 12-15 минут.",
                "Отвари картофель, разомни с тёплым молоком в пюре.",
                "Слегка посоли. Подавай вместе."
            ),
            tip = "Лёгкое, нежирное блюдо. При болях или дискомфорте обратись к врачу."
        ),
        Recipe(
            id = "light_chicken_broth",
            name = "Куриный бульон с рисом",
            emoji = "🍲",
            goals = listOf("light"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 2,
            calories = 280, protein = 30, fat = 8, carbs = 24,
            timeMinutes = 45,
            ingredients = listOf(
                RecipeIngredient("Куриная грудка", "200 г"),
                RecipeIngredient("Рис", "40 г"),
                RecipeIngredient("Морковь", "1 шт"),
                RecipeIngredient("Соль", "по вкусу")
            ),
            steps = listOf(
                "Отвари грудку с морковью около 25 минут.",
                "Достань мясо, нарежь, бульон процеди.",
                "Добавь рис в бульон, вари 15 минут.",
                "Верни курицу, посоли."
            ),
            tip = "Тёплый лёгкий бульон. Рассчитан на 2 порции."
        ),

        // ПОСТНЫЕ (растительные) блюда
        Recipe(
            id = "lent_oatmeal_fruit",
            name = "Овсянка на воде с фруктами и орехами",
            emoji = "🥣",
            goals = listOf("light", "lose", "fit"),
            mealTypes = listOf("breakfast"),
            servings = 1,
            calories = 340, protein = 9, fat = 10, carbs = 56,
            timeMinutes = 10,
            ingredients = listOf(
                RecipeIngredient("Овсяные хлопья", "70 г"),
                RecipeIngredient("Вода", "250 мл"),
                RecipeIngredient("Банан", "1 шт"),
                RecipeIngredient("Грецкие орехи", "15 г"),
                RecipeIngredient("Изюм", "20 г")
            ),
            steps = listOf(
                "Залей овсянку водой, доведи до кипения.",
                "Вари 5 минут, дай настояться.",
                "Добавь нарезанный банан, орехи и изюм."
            ),
            tip = "Сытный постный завтрак без молочных продуктов.",
            lenten = true
        ),
        Recipe(
            id = "lent_buckwheat_mushroom",
            name = "Гречка с грибами и луком",
            emoji = "🍄",
            goals = listOf("fit", "lose", "light"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 2,
            calories = 560, protein = 20, fat = 14, carbs = 88,
            timeMinutes = 30,
            ingredients = listOf(
                RecipeIngredient("Гречка (сухая)", "150 г"),
                RecipeIngredient("Шампиньоны", "250 г"),
                RecipeIngredient("Лук", "1 шт"),
                RecipeIngredient("Растительное масло", "1 ст.л."),
                RecipeIngredient("Соль, перец", "по вкусу")
            ),
            steps = listOf(
                "Отвари гречку (примерно 15-20 минут).",
                "Нарежь грибы и лук, обжарь на масле до золотистого цвета (10 мин).",
                "Смешай гречку с грибами, посоли."
            ),
            tip = "Классическое сытное постное блюдо с растительным белком.",
            lenten = true
        ),
        Recipe(
            id = "lent_lentil_soup",
            name = "Чечевичный суп",
            emoji = "🍲",
            goals = listOf("lose", "fit", "light"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 4,
            calories = 880, protein = 48, fat = 16, carbs = 132,
            timeMinutes = 40,
            ingredients = listOf(
                RecipeIngredient("Красная чечевица", "250 г"),
                RecipeIngredient("Морковь", "1 шт"),
                RecipeIngredient("Лук", "1 шт"),
                RecipeIngredient("Картофель", "2 шт"),
                RecipeIngredient("Растительное масло", "1 ст.л."),
                RecipeIngredient("Соль, специи", "по вкусу")
            ),
            steps = listOf(
                "Промой чечевицу. Нарежь овощи.",
                "Обжарь лук и морковь на масле 5 минут.",
                "Добавь чечевицу, картофель и воду (1.5 л), вари 25 минут.",
                "Посоли, приправь по вкусу."
            ),
            tip = "Чечевица богата растительным белком. На 4 порции.",
            lenten = true
        ),
        Recipe(
            id = "lent_veggie_stew",
            name = "Овощное рагу",
            emoji = "🥘",
            goals = listOf("lose", "light", "fit"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 3,
            calories = 480, protein = 12, fat = 15, carbs = 78,
            timeMinutes = 35,
            ingredients = listOf(
                RecipeIngredient("Картофель", "3 шт"),
                RecipeIngredient("Кабачок", "1 шт"),
                RecipeIngredient("Морковь", "1 шт"),
                RecipeIngredient("Капуста", "300 г"),
                RecipeIngredient("Томатная паста", "2 ст.л."),
                RecipeIngredient("Растительное масло", "2 ст.л.")
            ),
            steps = listOf(
                "Нарежь все овощи кубиками.",
                "Обжарь морковь и лук, добавь остальные овощи.",
                "Добавь томатную пасту и немного воды, туши 25 минут под крышкой.",
                "Посоли, добавь зелень."
            ),
            tip = "Яркое постное блюдо, много клетчатки. На 3 порции.",
            lenten = true
        ),
        Recipe(
            id = "lent_hummus_veg",
            name = "Хумус с овощами",
            emoji = "🧆",
            goals = listOf("fit", "lose", "light"),
            mealTypes = listOf("snack"),
            servings = 2,
            calories = 360, protein = 14, fat = 18, carbs = 38,
            timeMinutes = 10,
            ingredients = listOf(
                RecipeIngredient("Нут (варёный/консервированный)", "200 г"),
                RecipeIngredient("Тахини (кунжутная паста)", "1 ст.л."),
                RecipeIngredient("Лимонный сок", "1 ст.л."),
                RecipeIngredient("Чеснок", "1 зубчик"),
                RecipeIngredient("Овощи для нарезки (морковь, огурец)", "200 г")
            ),
            steps = listOf(
                "Сложи нут, тахини, лимон и чеснок в блендер, взбей до пасты.",
                "При необходимости добавь воды для консистенции.",
                "Нарежь овощи палочками, подавай с хумусом."
            ),
            tip = "Постный перекус с растительным белком. На 2 порции.",
            lenten = true
        ),
        Recipe(
            id = "lent_rice_veg",
            name = "Рис с овощами",
            emoji = "🍚",
            goals = listOf("fit", "light", "mass"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 2,
            calories = 620, protein = 14, fat = 12, carbs = 116,
            timeMinutes = 25,
            ingredients = listOf(
                RecipeIngredient("Рис (сухой)", "150 г"),
                RecipeIngredient("Морковь", "1 шт"),
                RecipeIngredient("Горошек (зелёный)", "100 г"),
                RecipeIngredient("Кукуруза", "100 г"),
                RecipeIngredient("Растительное масло", "1 ст.л.")
            ),
            steps = listOf(
                "Отвари рис.",
                "Обжарь нарезанную морковь, добавь горошек и кукурузу, туши 5 минут.",
                "Смешай рис с овощами, посоли."
            ),
            tip = "Сытный постный гарнир. На 2 порции.",
            lenten = true
        )        ,
        // НОВЫЕ РЕЦЕПТЫ
        Recipe(
            id = "lose_chicken_buckwheat",
            name = "Куриная грудка с гречкой и овощами",
            emoji = "🍗",
            goals = listOf("lose", "fit", "dry"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 1,
            calories = 420, protein = 42, fat = 9, carbs = 45,
            timeMinutes = 30,
            ingredients = listOf(
                RecipeIngredient("Куриная грудка", "150 г"),
                RecipeIngredient("Гречка", "60 г"),
                RecipeIngredient("Брокколи", "100 г"),
                RecipeIngredient("Морковь", "1 шт"),
                RecipeIngredient("Оливковое масло", "1 ч.л."),
                RecipeIngredient("Соль, специи", "по вкусу")
            ),
            steps = listOf(
                "Отвари гречку (60 г крупы на 120 мл воды) до готовности.",
                "Куриную грудку посоли, приправь и обжарь на сухой сковороде или запеки 20 минут.",
                "Брокколи и морковь отвари на пару 7-10 минут.",
                "Выложи всё на тарелку, сбрызни оливковым маслом."
            ),
            tip = "Идеальный обед для похудения: много белка, сложные углеводы."
        ),
        Recipe(
            id = "mass_pasta_chicken",
            name = "Паста с курицей в сливочном соусе",
            emoji = "🍝",
            goals = listOf("mass", "fit"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 1,
            calories = 650, protein = 38, fat = 22, carbs = 70,
            timeMinutes = 25,
            ingredients = listOf(
                RecipeIngredient("Паста (макароны)", "100 г"),
                RecipeIngredient("Куриная грудка", "150 г"),
                RecipeIngredient("Сливки 10%", "100 мл"),
                RecipeIngredient("Сыр твёрдый", "30 г"),
                RecipeIngredient("Чеснок", "1 зубчик"),
                RecipeIngredient("Соль, перец", "по вкусу")
            ),
            steps = listOf(
                "Отвари пасту согласно инструкции на упаковке.",
                "Нарежь курицу кубиками и обжарь до золотистости.",
                "Добавь измельчённый чеснок, влей сливки, туши 3 минуты.",
                "Добавь тёртый сыр, размешай до загустения.",
                "Смешай соус с пастой, подавай горячей."
            ),
            tip = "Калорийное блюдо для набора массы."
        ),
        Recipe(
            id = "light_veggie_omelette",
            name = "Овощной омлет с зеленью",
            emoji = "🍳",
            goals = listOf("light", "lose", "fit"),
            mealTypes = listOf("breakfast"),
            servings = 1,
            calories = 240, protein = 18, fat = 16, carbs = 6,
            timeMinutes = 12,
            ingredients = listOf(
                RecipeIngredient("Яйца", "3 шт"),
                RecipeIngredient("Помидор", "1 шт"),
                RecipeIngredient("Шпинат", "30 г"),
                RecipeIngredient("Молоко", "30 мл"),
                RecipeIngredient("Зелень, соль", "по вкусу")
            ),
            steps = listOf(
                "Взбей яйца с молоком и солью.",
                "Нарежь помидор, выложи на разогретую сковороду со шпинатом, протуши 2 минуты.",
                "Залей яичной смесью, накрой крышкой.",
                "Готовь на слабом огне 5-6 минут, посыпь зеленью."
            ),
            tip = "Лёгкий белковый завтрак."
        ),
        Recipe(
            id = "mass_protein_pancakes",
            name = "Протеиновые панкейки с бананом",
            emoji = "🥞",
            goals = listOf("mass", "fit"),
            mealTypes = listOf("breakfast", "snack"),
            servings = 1,
            calories = 420, protein = 30, fat = 10, carbs = 50,
            timeMinutes = 15,
            ingredients = listOf(
                RecipeIngredient("Овсяные хлопья", "50 г"),
                RecipeIngredient("Банан", "1 шт"),
                RecipeIngredient("Яйца", "2 шт"),
                RecipeIngredient("Протеин", "1 мерная ложка"),
                RecipeIngredient("Разрыхлитель", "0.5 ч.л.")
            ),
            steps = listOf(
                "Измельчи овсянку в муку блендером.",
                "Добавь банан, яйца, протеин, разрыхлитель — взбей до однородности.",
                "Выпекай панкейки на сухой антипригарной сковороде по 2 минуты с каждой стороны.",
                "Подавай с ягодами или мёдом."
            ),
            tip = "Белковый завтрак для спортсменов."
        ),
        Recipe(
            id = "lose_tuna_salad",
            name = "Салат с тунцом и яйцом",
            emoji = "🥗",
            goals = listOf("lose", "fit", "light"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 1,
            calories = 280, protein = 30, fat = 12, carbs = 10,
            timeMinutes = 15,
            ingredients = listOf(
                RecipeIngredient("Тунец консервированный", "1 банка"),
                RecipeIngredient("Яйцо", "1 шт"),
                RecipeIngredient("Огурец", "1 шт"),
                RecipeIngredient("Листья салата", "50 г"),
                RecipeIngredient("Йогурт греческий", "2 ст.л.")
            ),
            steps = listOf(
                "Отвари яйцо вкрутую, нарежь.",
                "Слей жидкость с тунца, разомни вилкой.",
                "Нарежь огурец и салат.",
                "Смешай всё, заправь греческим йогуртом."
            ),
            tip = "Лёгкий белковый салат без майонеза."
        ),
        Recipe(
            id = "light_veggie_soup",
            name = "Овощной суп-пюре из брокколи",
            emoji = "🥦",
            goals = listOf("light", "lose"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 2,
            calories = 180, protein = 8, fat = 8, carbs = 20,
            timeMinutes = 25,
            ingredients = listOf(
                RecipeIngredient("Брокколи", "300 г"),
                RecipeIngredient("Картофель", "1 шт"),
                RecipeIngredient("Лук", "1 шт"),
                RecipeIngredient("Сливки 10%", "50 мл"),
                RecipeIngredient("Соль, специи", "по вкусу")
            ),
            steps = listOf(
                "Нарежь овощи, залей водой и вари 15 минут до мягкости.",
                "Добавь сливки.",
                "Пробей блендером до однородной массы.",
                "Посоли, поперчи, подавай с зеленью."
            ),
            tip = "Низкокалорийный согревающий суп. На 2 порции.",
            lenten = false
        ),
        Recipe(
            id = "fit_turkey_rice",
            name = "Индейка с рисом и овощами",
            emoji = "🦃",
            goals = listOf("fit", "mass"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 1,
            calories = 460, protein = 38, fat = 10, carbs = 52,
            timeMinutes = 30,
            ingredients = listOf(
                RecipeIngredient("Филе индейки", "150 г"),
                RecipeIngredient("Рис", "60 г"),
                RecipeIngredient("Болгарский перец", "1 шт"),
                RecipeIngredient("Морковь", "1 шт"),
                RecipeIngredient("Соевый соус", "1 ст.л.")
            ),
            steps = listOf(
                "Отвари рис.",
                "Нарежь индейку и обжарь до готовности.",
                "Добавь нарезанные овощи, туши 7 минут.",
                "Влей соевый соус, перемешай с рисом."
            ),
            tip = "Сбалансированное блюдо для набора и поддержания формы."
        ),
        Recipe(
            id = "lose_cottage_bowl",
            name = "Творожный боул с ягодами",
            emoji = "🥣",
            goals = listOf("lose", "fit", "light"),
            mealTypes = listOf("breakfast", "snack"),
            servings = 1,
            calories = 250, protein = 28, fat = 5, carbs = 22,
            timeMinutes = 5,
            ingredients = listOf(
                RecipeIngredient("Творог 2%", "200 г"),
                RecipeIngredient("Ягоды (любые)", "50 г"),
                RecipeIngredient("Мёд", "1 ч.л."),
                RecipeIngredient("Орехи", "10 г")
            ),
            steps = listOf(
                "Выложи творог в миску.",
                "Добавь ягоды и измельчённые орехи.",
                "Полей мёдом, перемешай."
            ),
            tip = "Быстрый белковый перекус или завтрак."
        ),
        Recipe(
            id = "mass_beef_buckwheat",
            name = "Говядина с гречкой по-купечески",
            emoji = "🥩",
            goals = listOf("mass", "fit"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 2,
            calories = 900, protein = 70, fat = 30, carbs = 80,
            timeMinutes = 40,
            ingredients = listOf(
                RecipeIngredient("Говядина", "300 г"),
                RecipeIngredient("Гречка", "150 г"),
                RecipeIngredient("Лук", "1 шт"),
                RecipeIngredient("Морковь", "1 шт"),
                RecipeIngredient("Масло растительное", "1 ст.л.")
            ),
            steps = listOf(
                "Нарежь говядину кубиками, обжарь до румяности.",
                "Добавь лук и морковь, туши 10 минут.",
                "Всыпь гречку, залей водой (1:2), посоли.",
                "Туши под крышкой 20 минут до готовности крупы."
            ),
            tip = "Сытное блюдо для массонабора. На 2 порции."
        ),
        Recipe(
            id = "light_greek_salad",
            name = "Греческий салат",
            emoji = "🥗",
            goals = listOf("light", "lose", "fit"),
            mealTypes = listOf("lunch", "dinner", "snack"),
            servings = 1,
            calories = 270, protein = 8, fat = 22, carbs = 12,
            timeMinutes = 10,
            ingredients = listOf(
                RecipeIngredient("Помидоры", "2 шт"),
                RecipeIngredient("Огурец", "1 шт"),
                RecipeIngredient("Сыр фета", "50 г"),
                RecipeIngredient("Оливки", "50 г"),
                RecipeIngredient("Оливковое масло", "1 ст.л.")
            ),
            steps = listOf(
                "Нарежь помидоры и огурец крупными кубиками.",
                "Добавь оливки и нарезанную фету.",
                "Сбрызни оливковым маслом, добавь орегано."
            ),
            tip = "Свежий средиземноморский салат."
        ),
        Recipe(
            id = "fit_shrimp_rice",
            name = "Рис с креветками и овощами",
            emoji = "🦐",
            goals = listOf("fit", "lose"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 1,
            calories = 400, protein = 30, fat = 8, carbs = 50,
            timeMinutes = 20,
            ingredients = listOf(
                RecipeIngredient("Креветки", "150 г"),
                RecipeIngredient("Рис", "60 г"),
                RecipeIngredient("Горошек зелёный", "50 г"),
                RecipeIngredient("Чеснок", "1 зубчик"),
                RecipeIngredient("Соевый соус", "1 ст.л.")
            ),
            steps = listOf(
                "Отвари рис.",
                "Обжарь чеснок, добавь креветки, готовь 3-4 минуты.",
                "Добавь горошек и рис, влей соевый соус.",
                "Перемешай и прогрей 2 минуты."
            ),
            tip = "Лёгкое блюдо с морепродуктами."
        ),
        // РЕЦЕПТЫ ДЛЯ СУШКИ (высокий белок, мало углеводов)
        Recipe(
            id = "dry_chicken_veggies",
            name = "Куриная грудка с овощами на пару",
            emoji = "🍗",
            goals = listOf("dry", "lose"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 1,
            calories = 280, protein = 45, fat = 6, carbs = 12,
            timeMinutes = 25,
            ingredients = listOf(
                RecipeIngredient("Куриная грудка", "200 г"),
                RecipeIngredient("Брокколи", "150 г"),
                RecipeIngredient("Цветная капуста", "100 г"),
                RecipeIngredient("Соль, специи", "по вкусу")
            ),
            steps = listOf(
                "Куриную грудку посоли и приправь.",
                "Запеки или приготовь на пару 20 минут.",
                "Овощи отвари на пару 8-10 минут.",
                "Подавай вместе. Минимум жира, максимум белка."
            ),
            tip = "Идеально для сушки: чистый белок и клетчатка."
        ),
        Recipe(
            id = "dry_tuna_salad",
            name = "Салат с тунцом и яйцом (без масла)",
            emoji = "🥗",
            goals = listOf("dry", "lose"),
            mealTypes = listOf("lunch", "dinner", "snack"),
            servings = 1,
            calories = 230, protein = 35, fat = 8, carbs = 6,
            timeMinutes = 12,
            ingredients = listOf(
                RecipeIngredient("Тунец в собственном соку", "1 банка"),
                RecipeIngredient("Яичные белки", "3 шт"),
                RecipeIngredient("Огурец", "1 шт"),
                RecipeIngredient("Листья салата", "50 г"),
                RecipeIngredient("Лимонный сок", "1 ст.л.")
            ),
            steps = listOf(
                "Отвари яйца, отдели белки, нарежь.",
                "Слей жидкость с тунца.",
                "Нарежь огурец и салат.",
                "Смешай, заправь лимонным соком вместо масла."
            ),
            tip = "Максимум белка при минимуме калорий."
        ),
        Recipe(
            id = "dry_cottage_egg",
            name = "Творог обезжиренный с яичными белками",
            emoji = "🧀",
            goals = listOf("dry"),
            mealTypes = listOf("breakfast", "snack"),
            servings = 1,
            calories = 180, protein = 32, fat = 2, carbs = 8,
            timeMinutes = 8,
            ingredients = listOf(
                RecipeIngredient("Творог обезжиренный", "150 г"),
                RecipeIngredient("Яичные белки варёные", "2 шт"),
                RecipeIngredient("Зелень", "по вкусу")
            ),
            steps = listOf(
                "Выложи творог в миску.",
                "Добавь нарезанные варёные белки.",
                "Посыпь зеленью, посоли по вкусу."
            ),
            tip = "Белковый завтрак для сушки, почти без жира."
        ),
        Recipe(
            id = "dry_white_fish",
            name = "Белая рыба с зелёным салатом",
            emoji = "🐟",
            goals = listOf("dry", "lose"),
            mealTypes = listOf("lunch", "dinner"),
            servings = 1,
            calories = 240, protein = 40, fat = 5, carbs = 8,
            timeMinutes = 25,
            ingredients = listOf(
                RecipeIngredient("Филе трески или минтая", "200 г"),
                RecipeIngredient("Листовой салат", "80 г"),
                RecipeIngredient("Огурец", "1 шт"),
                RecipeIngredient("Лимон", "0.5 шт")
            ),
            steps = listOf(
                "Рыбу сбрызни лимоном, посоли.",
                "Запеки 15 минут при 200°C или приготовь на пару.",
                "Нарежь салат и огурец.",
                "Подавай рыбу с зелёным салатом."
            ),
            tip = "Постная рыба — основа сушки."
        ),
        Recipe(
            id = "dry_protein_shake",
            name = "Протеиновый коктейль на воде",
            emoji = "🥤",
            goals = listOf("dry"),
            mealTypes = listOf("snack", "breakfast"),
            servings = 1,
            calories = 130, protein = 27, fat = 2, carbs = 3,
            timeMinutes = 3,
            ingredients = listOf(
                RecipeIngredient("Протеин (изолят)", "1.5 мерные ложки"),
                RecipeIngredient("Вода", "300 мл"),
                RecipeIngredient("Лёд", "по желанию")
            ),
            steps = listOf(
                "Засыпь протеин в шейкер.",
                "Залей холодной водой.",
                "Взболтай 20 секунд. Готово."
            ),
            tip = "Быстрый белок без лишних калорий."
        ),

        // ═══════════════ ВЕГАН / ВЕГЕТАРИАНСКИЕ ═══════════════
        Recipe(
            id = "veg_tofu_scramble", name = "Скрэмбл из тофу с овощами", emoji = "🍳",
            goals = listOf("fit","lose","light"), mealTypes = listOf("breakfast","lunch"), servings = 1,
            calories = 320, protein = 22, fat = 18, carbs = 14, timeMinutes = 15,
            ingredients = listOf(RecipeIngredient("Тофу плотный","200 г"), RecipeIngredient("Помидор","1 шт"), RecipeIngredient("Шпинат","50 г"), RecipeIngredient("Куркума","0.5 ч.л."), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Разомни тофу вилкой до крупных хлопьев.","Обжарь на масле с куркумой 3-4 минуты.","Добавь помидор и шпинат, туши 3 минуты.","Посоли по вкусу."),
            tip = "Растительный белковый завтрак.", lenten = true
        ),
        Recipe(
            id = "veg_chickpea_curry", name = "Карри из нута с овощами", emoji = "🍛",
            goals = listOf("fit","lose","light","mass"), mealTypes = listOf("lunch","dinner"), servings = 2,
            calories = 760, protein = 30, fat = 22, carbs = 110, timeMinutes = 30,
            ingredients = listOf(RecipeIngredient("Нут варёный","300 г"), RecipeIngredient("Кокосовое молоко","200 мл"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Лук","1 шт"), RecipeIngredient("Карри приправа","1 ст.л."), RecipeIngredient("Рис басмати","120 г")),
            steps = listOf("Обжарь лук и морковь до мягкости.","Добавь нут, карри и кокосовое молоко, туши 15 минут.","Отвари рис.","Подавай карри с рисом."),
            tip = "Растительный белок и медленные углеводы.", lenten = true
        ),
        Recipe(
            id = "veg_lentil_soup", name = "Чечевичный суп с томатами", emoji = "🍲",
            goals = listOf("lose","light","fit"), mealTypes = listOf("lunch","dinner"), servings = 2,
            calories = 480, protein = 28, fat = 8, carbs = 78, timeMinutes = 35,
            ingredients = listOf(RecipeIngredient("Чечевица красная","150 г"), RecipeIngredient("Томаты в собственном соку","200 г"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Лук","1 шт"), RecipeIngredient("Чеснок","2 зубчика")),
            steps = listOf("Обжарь лук, морковь и чеснок 5 минут.","Добавь чечевицу, томаты и 700 мл воды.","Вари 25 минут.","Посоли, по желанию пробей блендером."),
            tip = "Источник растительного белка и клетчатки.", lenten = true
        ),
        Recipe(
            id = "veg_buddha_bowl", name = "Будда-боул с киноа и авокадо", emoji = "🥗",
            goals = listOf("fit","lose","light"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 540, protein = 18, fat = 24, carbs = 64, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Киноа","80 г"), RecipeIngredient("Авокадо","0.5 шт"), RecipeIngredient("Нут варёный","100 г"), RecipeIngredient("Огурец","1 шт"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Отвари киноа.","Нарежь овощи и авокадо.","Выложи в миску секторами.","Сбрызни маслом и лимоном."),
            tip = "Сбалансированная растительная миска.", lenten = true
        ),
        Recipe(
            id = "veg_oatmeal_berry", name = "Овсянка на растительном молоке с ягодами", emoji = "🥣",
            goals = listOf("fit","lose","light","mass"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 380, protein = 12, fat = 10, carbs = 62, timeMinutes = 10,
            ingredients = listOf(RecipeIngredient("Овсяные хлопья","70 г"), RecipeIngredient("Растительное молоко","250 мл"), RecipeIngredient("Ягоды замороженные","80 г"), RecipeIngredient("Семена чиа","1 ст.л.")),
            steps = listOf("Свари овсянку на растительном молоке 5 минут.","Добавь чиа.","Выложи ягоды сверху."),
            tip = "Веганский завтрак без молочного.", lenten = true
        ),
        Recipe(
            id = "veg_falafel", name = "Фалафель из нута", emoji = "🧆",
            goals = listOf("fit","mass","light"), mealTypes = listOf("lunch","dinner","snack"), servings = 2,
            calories = 620, protein = 26, fat = 26, carbs = 72, timeMinutes = 30,
            ingredients = listOf(RecipeIngredient("Нут сухой замоченный","200 г"), RecipeIngredient("Лук","1 шт"), RecipeIngredient("Чеснок","2 зубчика"), RecipeIngredient("Зелень","пучок"), RecipeIngredient("Кумин","1 ч.л.")),
            steps = listOf("Пробей нут с луком, чесноком и зеленью.","Добавь специи, слепи шарики.","Запекай 20 минут при 200°C.","Подавай с овощами."),
            tip = "Запечённый, а не жареный.", lenten = true
        ),
        Recipe(
            id = "veg_veg_stirfry", name = "Овощи стир-фрай с тофу", emoji = "🥘",
            goals = listOf("fit","lose","light"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 420, protein = 24, fat = 18, carbs = 42, timeMinutes = 20,
            ingredients = listOf(RecipeIngredient("Тофу","150 г"), RecipeIngredient("Брокколи","150 г"), RecipeIngredient("Болгарский перец","1 шт"), RecipeIngredient("Соевый соус","1 ст.л."), RecipeIngredient("Кунжутное масло","1 ч.л.")),
            steps = listOf("Обжарь тофу кубиками до корочки.","Добавь овощи, жарь на сильном огне 5 минут.","Влей соевый соус, перемешай."),
            tip = "Быстрое азиатское блюдо.", lenten = true
        ),
        Recipe(
            id = "veg_bean_chili", name = "Чили из фасоли", emoji = "🌶️",
            goals = listOf("fit","lose","light","mass"), mealTypes = listOf("lunch","dinner"), servings = 2,
            calories = 560, protein = 28, fat = 10, carbs = 92, timeMinutes = 35,
            ingredients = listOf(RecipeIngredient("Красная фасоль варёная","300 г"), RecipeIngredient("Томаты","200 г"), RecipeIngredient("Кукуруза","100 г"), RecipeIngredient("Лук","1 шт"), RecipeIngredient("Паприка","1 ч.л.")),
            steps = listOf("Обжарь лук.","Добавь фасоль, томаты, кукурузу и специи.","Туши 20 минут."),
            tip = "Острое и сытное, много белка.", lenten = true
        ),
        Recipe(
            id = "veg_quinoa_salad", name = "Тёплый салат с киноа и нутом", emoji = "🥗",
            goals = listOf("fit","lose","light"), mealTypes = listOf("lunch","dinner","snack"), servings = 1,
            calories = 460, protein = 18, fat = 16, carbs = 60, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Киноа","70 г"), RecipeIngredient("Нут варёный","100 г"), RecipeIngredient("Помидор","1 шт"), RecipeIngredient("Огурец","1 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Отвари киноа.","Смешай с нутом и нарезанными овощами.","Заправь маслом."),
            tip = "Полноценный растительный белок.", lenten = true
        ),
        Recipe(
            id = "veg_hummus_toast", name = "Тост с хумусом и овощами", emoji = "🥪",
            goals = listOf("fit","lose","light"), mealTypes = listOf("breakfast","snack"), servings = 1,
            calories = 340, protein = 12, fat = 14, carbs = 44, timeMinutes = 8,
            ingredients = listOf(RecipeIngredient("Хлеб цельнозерновой","2 ломтика"), RecipeIngredient("Хумус","60 г"), RecipeIngredient("Помидор","1 шт"), RecipeIngredient("Огурец","1 шт")),
            steps = listOf("Подсуши хлеб.","Намажь хумусом.","Выложи нарезанные овощи."),
            tip = "Быстрый растительный перекус.", lenten = true
        ),
        Recipe(
            id = "veg_veggie_soup", name = "Овощной суп-пюре", emoji = "🍲",
            goals = listOf("lose","light"), mealTypes = listOf("lunch","dinner"), servings = 2,
            calories = 300, protein = 8, fat = 10, carbs = 46, timeMinutes = 30,
            ingredients = listOf(RecipeIngredient("Тыква","300 г"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Лук","1 шт"), RecipeIngredient("Кокосовое молоко","100 мл")),
            steps = listOf("Отвари тыкву, морковь и лук до мягкости.","Пробей блендером с кокосовым молоком.","Прогрей, посоли."),
            tip = "Нежный крем-суп без молочного.", lenten = true
        ),
        Recipe(
            id = "veg_rice_beans", name = "Рис с чёрной фасолью", emoji = "🍚",
            goals = listOf("mass","fit","light"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 580, protein = 22, fat = 8, carbs = 110, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Рис","100 г"), RecipeIngredient("Чёрная фасоль варёная","150 г"), RecipeIngredient("Кукуруза","80 г"), RecipeIngredient("Лайм","0.5 шт")),
            steps = listOf("Отвари рис.","Прогрей фасоль с кукурузой.","Смешай, сбрызни лаймом."),
            tip = "Классическое латино сочетание белков.", lenten = true
        ),
        Recipe(
            id = "veg_pasta_veg", name = "Паста с овощами и томатом", emoji = "🍝",
            goals = listOf("mass","fit"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 540, protein = 16, fat = 12, carbs = 92, timeMinutes = 20,
            ingredients = listOf(RecipeIngredient("Паста","100 г"), RecipeIngredient("Томаты","200 г"), RecipeIngredient("Кабачок","1 шт"), RecipeIngredient("Чеснок","2 зубчика"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Отвари пасту.","Потуши томаты с кабачком и чесноком.","Смешай с пастой."),
            tip = "Простая веганская паста.", lenten = true
        ),
        Recipe(
            id = "veg_smoothie_bowl", name = "Смузи-боул с бананом и ягодами", emoji = "🍓",
            goals = listOf("fit","lose","light"), mealTypes = listOf("breakfast","snack"), servings = 1,
            calories = 320, protein = 8, fat = 8, carbs = 58, timeMinutes = 10,
            ingredients = listOf(RecipeIngredient("Банан","1 шт"), RecipeIngredient("Ягоды замороженные","100 г"), RecipeIngredient("Растительное молоко","150 мл"), RecipeIngredient("Овсяные хлопья","30 г")),
            steps = listOf("Пробей банан, ягоды и молоко блендером.","Вылей в миску.","Посыпь хлопьями."),
            tip = "Освежающий завтрак.", lenten = true
        )
        ,

        // ═══════════════ БЕЗ ГЛЮТЕНА ═══════════════
        Recipe(
            id = "gf_rice_chicken", name = "Рис с курицей и овощами", emoji = "🍚",
            goals = listOf("fit","mass","lose"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 560, protein = 42, fat = 14, carbs = 64, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Куриное филе","150 г"), RecipeIngredient("Рис","80 г"), RecipeIngredient("Брокколи","150 г"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Отвари рис.","Обжарь филе.","Брокколи и морковь на пару.","Собери в миску."),
            tip = "Без муки и соусов.", lenten = false
        ),
        Recipe(
            id = "gf_omelette", name = "Омлет с овощами", emoji = "🍳",
            goals = listOf("fit","lose","light"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 300, protein = 22, fat = 20, carbs = 8, timeMinutes = 12,
            ingredients = listOf(RecipeIngredient("Яйца","3 шт"), RecipeIngredient("Помидор","1 шт"), RecipeIngredient("Шпинат","40 г"), RecipeIngredient("Оливковое масло","1 ч.л.")),
            steps = listOf("Взбей яйца.","Вылей на сковороду.","Добавь помидор и шпинат, готовь под крышкой 5 минут."),
            tip = "Без муки и хлеба.", lenten = false
        ),
        Recipe(
            id = "gf_buckwheat_veg", name = "Гречка с тушёными овощами", emoji = "🥘",
            goals = listOf("lose","light","fit"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 420, protein = 14, fat = 10, carbs = 70, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Гречка","80 г"), RecipeIngredient("Кабачок","1 шт"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Лук","1 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Отвари гречку.","Потуши овощи 10 минут.","Смешай."),
            tip = "Гречка без глютена.", lenten = true
        ),
        Recipe(
            id = "gf_potato_fish", name = "Картофель с запечённой рыбой", emoji = "🐟",
            goals = listOf("fit","mass"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 520, protein = 38, fat = 16, carbs = 56, timeMinutes = 35,
            ingredients = listOf(RecipeIngredient("Филе трески","180 г"), RecipeIngredient("Картофель","2 шт"), RecipeIngredient("Лимон","0.5 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Нарежь картофель дольками, запекай 25 минут.","Рыбу сбрызни лимоном, добавь к картофелю на 15 минут.","Посоли."),
            tip = "Натурально без глютена.", lenten = false
        ),
        Recipe(
            id = "gf_rice_bowl_egg", name = "Рисовая миска с яйцом и авокадо", emoji = "🍚",
            goals = listOf("fit","mass"), mealTypes = listOf("breakfast","lunch"), servings = 1,
            calories = 540, protein = 22, fat = 26, carbs = 52, timeMinutes = 20,
            ingredients = listOf(RecipeIngredient("Рис","70 г"), RecipeIngredient("Яйцо","2 шт"), RecipeIngredient("Авокадо","0.5 шт"), RecipeIngredient("Огурец","1 шт")),
            steps = listOf("Отвари рис.","Свари яйца.","Нарежь авокадо и огурец.","Собери в миску."),
            tip = "Сытная безглютеновая миска.", lenten = false
        ),
        Recipe(
            id = "gf_corn_porridge", name = "Кукурузная каша с ягодами", emoji = "🌽",
            goals = listOf("fit","light","lose"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 360, protein = 8, fat = 6, carbs = 70, timeMinutes = 20,
            ingredients = listOf(RecipeIngredient("Кукурузная крупа","70 г"), RecipeIngredient("Вода","300 мл"), RecipeIngredient("Ягоды","80 г")),
            steps = listOf("Свари кукурузную кашу на воде 15 минут.","Добавь ягоды."),
            tip = "Кукуруза не содержит глютен.", lenten = true
        ),
        Recipe(
            id = "gf_beef_rice", name = "Говядина с рисом и овощами", emoji = "🥩",
            goals = listOf("mass","fit"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 640, protein = 46, fat = 22, carbs = 60, timeMinutes = 40,
            ingredients = listOf(RecipeIngredient("Говядина","150 г"), RecipeIngredient("Рис","80 г"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Лук","1 шт")),
            steps = listOf("Отвари рис.","Потуши говядину с овощами 30 минут.","Подавай вместе."),
            tip = "Без муки и панировки.", lenten = false
        ),
        Recipe(
            id = "gf_veg_frittata", name = "Фриттата с овощами", emoji = "🍳",
            goals = listOf("fit","lose","light"), mealTypes = listOf("breakfast","lunch"), servings = 1,
            calories = 340, protein = 24, fat = 22, carbs = 12, timeMinutes = 20,
            ingredients = listOf(RecipeIngredient("Яйца","3 шт"), RecipeIngredient("Кабачок","1 шт"), RecipeIngredient("Болгарский перец","1 шт"), RecipeIngredient("Оливковое масло","1 ч.л.")),
            steps = listOf("Обжарь овощи.","Залей взбитыми яйцами.","Готовь под крышкой 8 минут."),
            tip = "Запеканка из яиц без муки.", lenten = false
        ),
        Recipe(
            id = "gf_chicken_salad", name = "Салат с курицей и киноа", emoji = "🥗",
            goals = listOf("fit","lose","dry"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 440, protein = 40, fat = 16, carbs = 36, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Куриное филе","150 г"), RecipeIngredient("Киноа","50 г"), RecipeIngredient("Помидор","1 шт"), RecipeIngredient("Листья салата","100 г"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Отвари киноа.","Обжарь филе, нарежь.","Смешай с овощами и киноа."),
            tip = "Безглютеновый сытный салат.", lenten = false
        ),
        Recipe(
            id = "gf_baked_potato_egg", name = "Запечённый картофель с яйцом", emoji = "🥔",
            goals = listOf("fit","mass","light"), mealTypes = listOf("breakfast","lunch"), servings = 1,
            calories = 420, protein = 18, fat = 16, carbs = 52, timeMinutes = 40,
            ingredients = listOf(RecipeIngredient("Картофель","2 шт"), RecipeIngredient("Яйцо","2 шт"), RecipeIngredient("Зелень","пучок")),
            steps = listOf("Запеки картофель 30 минут.","Свари яйца.","Подавай вместе с зеленью."),
            tip = "Простое сытное блюдо.", lenten = false
        ),
        Recipe(
            id = "gf_millet_pumpkin", name = "Пшённая каша с тыквой", emoji = "🎃",
            goals = listOf("fit","light","lose"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 380, protein = 10, fat = 8, carbs = 68, timeMinutes = 30,
            ingredients = listOf(RecipeIngredient("Пшено","70 г"), RecipeIngredient("Тыква","150 г"), RecipeIngredient("Вода","300 мл")),
            steps = listOf("Отвари пшено с тыквой 25 минут.","Разомни, посоли."),
            tip = "Пшено без глютена.", lenten = true
        ),
        Recipe(
            id = "gf_shrimp_rice", name = "Креветки с рисом и овощами", emoji = "🍤",
            goals = listOf("fit","lose","dry"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 420, protein = 34, fat = 10, carbs = 48, timeMinutes = 20,
            ingredients = listOf(RecipeIngredient("Креветки","150 г"), RecipeIngredient("Рис","70 г"), RecipeIngredient("Брокколи","100 г"), RecipeIngredient("Чеснок","2 зубчика")),
            steps = listOf("Отвари рис.","Обжарь креветки с чесноком 4 минуты.","Брокколи на пару.","Собери."),
            tip = "Лёгкий морской белок.", lenten = false
        ),

        // ═══════════════ БЕЗ ЛАКТОЗЫ ═══════════════
        Recipe(
            id = "nl_salmon_rice", name = "Лосось с рисом и спаржей", emoji = "🐟",
            goals = listOf("fit","mass"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 580, protein = 40, fat = 24, carbs = 48, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Филе лосося","150 г"), RecipeIngredient("Рис","70 г"), RecipeIngredient("Спаржа","100 г"), RecipeIngredient("Лимон","0.5 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Запеки лосось с лимоном 15 минут.","Отвари рис.","Спаржа на пару 5 минут.","Подавай вместе."),
            tip = "Без молочного, богато омега-3.", lenten = false
        ),
        Recipe(
            id = "nl_beef_stew", name = "Тушёная говядина с овощами", emoji = "🥩",
            goals = listOf("mass","fit"), mealTypes = listOf("lunch","dinner"), servings = 2,
            calories = 820, protein = 64, fat = 38, carbs = 48, timeMinutes = 60,
            ingredients = listOf(RecipeIngredient("Говядина","300 г"), RecipeIngredient("Картофель","2 шт"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Лук","1 шт"), RecipeIngredient("Томатная паста","1 ст.л.")),
            steps = listOf("Обжарь говядину.","Добавь лук, морковь, пасту.","Туши 40 минут.","Добавь картофель, ещё 20 минут."),
            tip = "Сытно, без молочного.", lenten = false
        ),
        Recipe(
            id = "nl_chicken_veg", name = "Курица с овощами на сковороде", emoji = "🍗",
            goals = listOf("fit","lose","dry"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 420, protein = 42, fat = 14, carbs = 30, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Куриное филе","150 г"), RecipeIngredient("Кабачок","1 шт"), RecipeIngredient("Болгарский перец","1 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Нарежь и обжарь филе.","Добавь овощи, туши 12 минут."),
            tip = "Без молочного, лёгкое.", lenten = false
        ),
        Recipe(
            id = "nl_oat_water", name = "Овсянка на воде с фруктами", emoji = "🥣",
            goals = listOf("lose","light","fit"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 340, protein = 10, fat = 6, carbs = 64, timeMinutes = 10,
            ingredients = listOf(RecipeIngredient("Овсяные хлопья","70 г"), RecipeIngredient("Вода","250 мл"), RecipeIngredient("Банан","1 шт")),
            steps = listOf("Свари овсянку на воде.","Добавь нарезанный банан."),
            tip = "Без молока.", lenten = true
        ),
        Recipe(
            id = "nl_turkey_potato", name = "Индейка с картофелем", emoji = "🍗",
            goals = listOf("mass","fit"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 540, protein = 46, fat = 14, carbs = 56, timeMinutes = 40,
            ingredients = listOf(RecipeIngredient("Филе индейки","150 г"), RecipeIngredient("Картофель","2 шт"), RecipeIngredient("Морковь","1 шт")),
            steps = listOf("Запеки индейку и овощи 30 минут.","Посоли."),
            tip = "Без молочного.", lenten = false
        ),
        Recipe(
            id = "nl_tuna_salad", name = "Салат с тунцом и фасолью", emoji = "🥗",
            goals = listOf("fit","lose","dry"), mealTypes = listOf("lunch","dinner","snack"), servings = 1,
            calories = 380, protein = 36, fat = 12, carbs = 32, timeMinutes = 10,
            ingredients = listOf(RecipeIngredient("Тунец консервированный","120 г"), RecipeIngredient("Фасоль варёная","100 г"), RecipeIngredient("Помидор","1 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Смешай тунец с фасолью.","Добавь нарезанный помидор.","Заправь маслом."),
            tip = "Быстрый белковый салат.", lenten = false
        ),
        Recipe(
            id = "nl_rice_veg", name = "Рис с овощами и нутом", emoji = "🍚",
            goals = listOf("fit","light","mass"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 520, protein = 18, fat = 10, carbs = 92, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Рис","90 г"), RecipeIngredient("Нут варёный","100 г"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Горошек","80 г")),
            steps = listOf("Отвари рис.","Прогрей нут с овощами.","Смешай."),
            tip = "Растительный, без молочного.", lenten = true
        ),
        Recipe(
            id = "nl_egg_veg_pan", name = "Яичница с овощами", emoji = "🍳",
            goals = listOf("fit","lose","light"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 280, protein = 18, fat = 18, carbs = 10, timeMinutes = 10,
            ingredients = listOf(RecipeIngredient("Яйца","2 шт"), RecipeIngredient("Помидор","1 шт"), RecipeIngredient("Шпинат","40 г"), RecipeIngredient("Оливковое масло","1 ч.л.")),
            steps = listOf("Обжарь овощи.","Залей яйцами, готовь 5 минут."),
            tip = "Без молочного.", lenten = false
        ),
        Recipe(
            id = "nl_chicken_buckwheat", name = "Курица с гречкой", emoji = "🍗",
            goals = listOf("fit","mass","lose"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 500, protein = 44, fat = 12, carbs = 54, timeMinutes = 30,
            ingredients = listOf(RecipeIngredient("Куриное филе","150 г"), RecipeIngredient("Гречка","70 г"), RecipeIngredient("Морковь","1 шт")),
            steps = listOf("Отвари гречку.","Обжарь филе с морковью.","Подавай вместе."),
            tip = "Классика без молочного.", lenten = false
        ),
        Recipe(
            id = "nl_fruit_smoothie", name = "Фруктовый смузи на воде", emoji = "🥤",
            goals = listOf("lose","light","fit"), mealTypes = listOf("snack","breakfast"), servings = 1,
            calories = 220, protein = 4, fat = 2, carbs = 50, timeMinutes = 5,
            ingredients = listOf(RecipeIngredient("Банан","1 шт"), RecipeIngredient("Ягоды","100 г"), RecipeIngredient("Вода","150 мл")),
            steps = listOf("Пробей всё блендером."),
            tip = "Освежающий, без молока.", lenten = true
        )
        ,

        // ═══════════════ ДЛЯ БЕРЕМЕННЫХ (общие принципы) ═══════════════
        Recipe(
            id = "preg_oatmeal_nuts", name = "Овсянка с орехами и яблоком", emoji = "🥣",
            goals = listOf("fit","light"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 420, protein = 14, fat = 16, carbs = 58, timeMinutes = 12,
            ingredients = listOf(RecipeIngredient("Овсяные хлопья","70 г"), RecipeIngredient("Молоко 2.5%","200 мл"), RecipeIngredient("Грецкий орех","20 г"), RecipeIngredient("Яблоко","1 шт")),
            steps = listOf("Свари овсянку на молоке.","Добавь яблоко и орехи."),
            tip = "Фолиевая кислота, кальций, клетчатка.", lenten = false
        ),
        Recipe(
            id = "preg_chicken_soup", name = "Куриный суп с овощами", emoji = "🍲",
            goals = listOf("light","fit","lose"), mealTypes = listOf("lunch","dinner"), servings = 2,
            calories = 520, protein = 44, fat = 16, carbs = 48, timeMinutes = 40,
            ingredients = listOf(RecipeIngredient("Куриное филе","200 г"), RecipeIngredient("Картофель","2 шт"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Брокколи","100 г"), RecipeIngredient("Зелень","пучок")),
            steps = listOf("Отвари филе, нарежь.","Добавь овощи в бульон, вари 20 минут.","Верни курицу, добавь зелень."),
            tip = "Лёгкий и питательный.", lenten = false
        ),
        Recipe(
            id = "preg_cottage_berries", name = "Творог с ягодами", emoji = "🥛",
            goals = listOf("fit","light","lose"), mealTypes = listOf("breakfast","snack"), servings = 1,
            calories = 280, protein = 28, fat = 6, carbs = 28, timeMinutes = 5,
            ingredients = listOf(RecipeIngredient("Творог 5%","200 г"), RecipeIngredient("Ягоды","80 г"), RecipeIngredient("Мёд","1 ч.л.")),
            steps = listOf("Смешай творог с мёдом.","Добавь ягоды."),
            tip = "Кальций и белок.", lenten = false
        ),
        Recipe(
            id = "preg_salmon_veg", name = "Лосось с овощами на пару", emoji = "🐟",
            goals = listOf("fit","light"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 460, protein = 38, fat = 24, carbs = 20, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Филе лосося","150 г"), RecipeIngredient("Брокколи","150 г"), RecipeIngredient("Морковь","1 шт")),
            steps = listOf("Приготовь лосось на пару 15 минут.","Овощи на пару отдельно.","Подавай вместе."),
            tip = "Омега-3 для развития мозга малыша.", lenten = false
        ),
        Recipe(
            id = "preg_beef_buckwheat", name = "Говядина с гречкой (железо)", emoji = "🥩",
            goals = listOf("mass","fit"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 560, protein = 46, fat = 18, carbs = 52, timeMinutes = 45,
            ingredients = listOf(RecipeIngredient("Говядина","150 г"), RecipeIngredient("Гречка","70 г"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Лук","1 шт")),
            steps = listOf("Отвари гречку.","Потуши говядину с овощами 35 минут.","Подавай вместе."),
            tip = "Богато железом — профилактика анемии.", lenten = false
        ),
        Recipe(
            id = "preg_yogurt_granola", name = "Йогурт с гранолой и фруктами", emoji = "🥛",
            goals = listOf("fit","light","lose"), mealTypes = listOf("breakfast","snack"), servings = 1,
            calories = 320, protein = 16, fat = 10, carbs = 44, timeMinutes = 5,
            ingredients = listOf(RecipeIngredient("Йогурт натуральный","200 г"), RecipeIngredient("Гранола","40 г"), RecipeIngredient("Банан","1 шт")),
            steps = listOf("Выложи йогурт в миску.","Добавь гранолу и нарезанный банан."),
            tip = "Пробиотики и кальций.", lenten = false
        ),
        Recipe(
            id = "preg_lentil_stew", name = "Чечевица с овощами (фолат)", emoji = "🍲",
            goals = listOf("light","fit","lose"), mealTypes = listOf("lunch","dinner"), servings = 2,
            calories = 480, protein = 28, fat = 8, carbs = 78, timeMinutes = 35,
            ingredients = listOf(RecipeIngredient("Чечевица","150 г"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Лук","1 шт"), RecipeIngredient("Шпинат","60 г")),
            steps = listOf("Обжарь лук и морковь.","Добавь чечевицу и воду, вари 25 минут.","Добавь шпинат в конце."),
            tip = "Чечевица и шпинат — источники фолиевой кислоты.", lenten = true
        ),
        Recipe(
            id = "preg_egg_toast", name = "Тост с яйцом и авокадо", emoji = "🥑",
            goals = listOf("fit","light"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 380, protein = 18, fat = 22, carbs = 30, timeMinutes = 12,
            ingredients = listOf(RecipeIngredient("Хлеб цельнозерновой","2 ломтика"), RecipeIngredient("Яйцо","2 шт"), RecipeIngredient("Авокадо","0.5 шт")),
            steps = listOf("Свари яйца.","Подсуши хлеб, намажь авокадо.","Выложи нарезанные яйца."),
            tip = "Полезные жиры и белок.", lenten = false
        ),
        Recipe(
            id = "preg_rice_chicken_veg", name = "Рис с курицей и тушёными овощами", emoji = "🍚",
            goals = listOf("fit","mass","light"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 540, protein = 40, fat = 14, carbs = 62, timeMinutes = 30,
            ingredients = listOf(RecipeIngredient("Куриное филе","150 г"), RecipeIngredient("Рис","80 г"), RecipeIngredient("Кабачок","1 шт"), RecipeIngredient("Морковь","1 шт")),
            steps = listOf("Отвари рис.","Потуши курицу с овощами.","Подавай вместе."),
            tip = "Сбалансированный обед.", lenten = false
        ),
        Recipe(
            id = "preg_fruit_cottage", name = "Запеканка из творога с яблоком", emoji = "🍮",
            goals = listOf("fit","light"), mealTypes = listOf("breakfast","snack"), servings = 1,
            calories = 340, protein = 26, fat = 10, carbs = 36, timeMinutes = 35,
            ingredients = listOf(RecipeIngredient("Творог 5%","200 г"), RecipeIngredient("Яйцо","1 шт"), RecipeIngredient("Яблоко","1 шт"), RecipeIngredient("Манка","1 ст.л.")),
            steps = listOf("Смешай творог, яйцо, манку.","Добавь нарезанное яблоко.","Запекай 25 минут при 180°C."),
            tip = "Кальций в удобной форме.", lenten = false
        ),
        Recipe(
            id = "preg_veg_omelette", name = "Омлет со шпинатом и сыром", emoji = "🍳",
            goals = listOf("fit","light"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 360, protein = 26, fat = 26, carbs = 6, timeMinutes = 12,
            ingredients = listOf(RecipeIngredient("Яйца","3 шт"), RecipeIngredient("Шпинат","50 г"), RecipeIngredient("Сыр твёрдый","30 г")),
            steps = listOf("Взбей яйца.","Вылей на сковороду, добавь шпинат и сыр.","Готовь под крышкой 5 минут."),
            tip = "Кальций и белок. Сыр — только пастеризованный!", lenten = false
        ),

        // ═══════════════ ДЛЯ КОРМЯЩИХ (общие принципы) ═══════════════
        Recipe(
            id = "nurs_turkey_buckwheat", name = "Индейка с гречкой и морковью", emoji = "🍗",
            goals = listOf("fit","light","lose"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 480, protein = 44, fat = 12, carbs = 50, timeMinutes = 30,
            ingredients = listOf(RecipeIngredient("Филе индейки","150 г"), RecipeIngredient("Гречка","70 г"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Оливковое масло","1 ч.л.")),
            steps = listOf("Отвари гречку.","Потуши индейку и морковь 20 минут.","Подавай вместе."),
            tip = "Гипоаллергенно для периода ГВ.", lenten = false
        ),
        Recipe(
            id = "nurs_rice_porridge", name = "Рисовая каша с яблоком", emoji = "🍚",
            goals = listOf("light","lose"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 320, protein = 6, fat = 4, carbs = 68, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Рис","70 г"), RecipeIngredient("Вода","300 мл"), RecipeIngredient("Яблоко","1 шт")),
            steps = listOf("Свари рис на воде.","Добавь печёное яблоко."),
            tip = "Мягкая каша без аллергенов.", lenten = true
        ),
        Recipe(
            id = "nurs_chicken_rice", name = "Курица с рисом и кабачком", emoji = "🍗",
            goals = listOf("fit","light","mass"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 500, protein = 42, fat = 12, carbs = 56, timeMinutes = 30,
            ingredients = listOf(RecipeIngredient("Куриное филе","150 г"), RecipeIngredient("Рис","80 г"), RecipeIngredient("Кабачок","1 шт")),
            steps = listOf("Отвари рис.","Потуши курицу с кабачком.","Подавай вместе."),
            tip = "Спокойное гипоаллергенное блюдо.", lenten = false
        ),
        Recipe(
            id = "nurs_buckwheat_porridge", name = "Гречневая каша на воде", emoji = "🥣",
            goals = listOf("light","lose","fit"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 300, protein = 12, fat = 4, carbs = 58, timeMinutes = 20,
            ingredients = listOf(RecipeIngredient("Гречка","80 г"), RecipeIngredient("Вода","300 мл")),
            steps = listOf("Свари гречку на воде 15 минут."),
            tip = "Базовая безопасная каша.", lenten = true
        ),
        Recipe(
            id = "nurs_turkey_potato", name = "Индейка с картофельным пюре", emoji = "🥔",
            goals = listOf("fit","mass","light"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 520, protein = 42, fat = 14, carbs = 58, timeMinutes = 35,
            ingredients = listOf(RecipeIngredient("Филе индейки","150 г"), RecipeIngredient("Картофель","2 шт"), RecipeIngredient("Молоко 2.5%","50 мл")),
            steps = listOf("Отвари картофель, разомни с молоком.","Потуши индейку.","Подавай вместе."),
            tip = "Нежное сытное блюдо.", lenten = false
        ),
        Recipe(
            id = "nurs_veg_stew", name = "Тушёные кабачки с морковью", emoji = "🥘",
            goals = listOf("lose","light"), mealTypes = listOf("lunch","dinner","snack"), servings = 1,
            calories = 220, protein = 4, fat = 10, carbs = 30, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Кабачок","2 шт"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Нарежь овощи.","Туши под крышкой 20 минут."),
            tip = "Лёгкий гарнир без аллергенов.", lenten = true
        ),
        Recipe(
            id = "nurs_beef_buckwheat", name = "Говядина с гречкой", emoji = "🥩",
            goals = listOf("mass","fit"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 560, protein = 46, fat = 18, carbs = 52, timeMinutes = 45,
            ingredients = listOf(RecipeIngredient("Говядина","150 г"), RecipeIngredient("Гречка","70 г"), RecipeIngredient("Морковь","1 шт")),
            steps = listOf("Отвари гречку.","Потуши говядину с морковью 35 минут."),
            tip = "Железо для восстановления.", lenten = false
        ),
        Recipe(
            id = "nurs_apple_baked", name = "Печёные яблоки", emoji = "🍎",
            goals = listOf("light","lose"), mealTypes = listOf("snack","breakfast"), servings = 1,
            calories = 160, protein = 2, fat = 1, carbs = 40, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Яблоко","2 шт"), RecipeIngredient("Корица","щепотка")),
            steps = listOf("Удали сердцевину яблок.","Посыпь корицей.","Запекай 20 минут при 180°C."),
            tip = "Безопасный сладкий перекус.", lenten = true
        ),
        Recipe(
            id = "nurs_cottage_plain", name = "Творог с бананом", emoji = "🥛",
            goals = listOf("fit","light"), mealTypes = listOf("breakfast","snack"), servings = 1,
            calories = 280, protein = 28, fat = 6, carbs = 30, timeMinutes = 5,
            ingredients = listOf(RecipeIngredient("Творог 5%","200 г"), RecipeIngredient("Банан","1 шт")),
            steps = listOf("Разомни творог с бананом."),
            tip = "Кальций без частых аллергенов.", lenten = false
        ),
        Recipe(
            id = "nurs_chicken_soup", name = "Лёгкий куриный бульон с рисом", emoji = "🍲",
            goals = listOf("light","lose"), mealTypes = listOf("lunch","dinner"), servings = 2,
            calories = 360, protein = 32, fat = 8, carbs = 40, timeMinutes = 40,
            ingredients = listOf(RecipeIngredient("Куриное филе","150 г"), RecipeIngredient("Рис","50 г"), RecipeIngredient("Морковь","1 шт")),
            steps = listOf("Отвари курицу, вынь.","В бульон добавь рис и морковь, вари 20 минут.","Верни нарезанную курицу."),
            tip = "Восстанавливающий лёгкий суп.", lenten = false
        )
        ,

        // ═══════════════ ДЛЯ ДИАБЕТА (низкий сахар/углеводы) ═══════════════
        Recipe(
            id = "diab_egg_avocado", name = "Яичница с авокадо", emoji = "🍳",
            goals = listOf("fit","lose","light","dry"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 360, protein = 20, fat = 28, carbs = 10, timeMinutes = 12,
            ingredients = listOf(RecipeIngredient("Яйца","2 шт"), RecipeIngredient("Авокадо","0.5 шт"), RecipeIngredient("Помидор","1 шт"), RecipeIngredient("Шпинат","40 г")),
            steps = listOf("Обжарь яйца с помидором и шпинатом.","Подавай с авокадо."),
            tip = "Мало углеводов, без скачка сахара.", lenten = false
        ),
        Recipe(
            id = "diab_chicken_salad", name = "Салат с курицей и овощами", emoji = "🥗",
            goals = listOf("lose","fit","dry"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 380, protein = 38, fat = 18, carbs = 14, timeMinutes = 20,
            ingredients = listOf(RecipeIngredient("Куриное филе","150 г"), RecipeIngredient("Огурец","1 шт"), RecipeIngredient("Листья салата","100 г"), RecipeIngredient("Помидор","1 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Обжарь филе, нарежь.","Смешай с овощами.","Заправь маслом."),
            tip = "Низкоуглеводный белковый салат.", lenten = false
        ),
        Recipe(
            id = "diab_cod_veg", name = "Запечённая треска с овощами", emoji = "🐟",
            goals = listOf("lose","fit","dry","light"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 340, protein = 38, fat = 12, carbs = 16, timeMinutes = 30,
            ingredients = listOf(RecipeIngredient("Филе трески","180 г"), RecipeIngredient("Кабачок","1 шт"), RecipeIngredient("Болгарский перец","1 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Выложи рыбу и овощи на противень.","Сбрызни маслом.","Запекай 25 минут при 190°C."),
            tip = "Постный белок, минимум углеводов.", lenten = false
        ),
        Recipe(
            id = "diab_beef_broccoli", name = "Говядина с брокколи", emoji = "🥩",
            goals = listOf("fit","dry","mass"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 440, protein = 44, fat = 24, carbs = 14, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Говядина","150 г"), RecipeIngredient("Брокколи","200 г"), RecipeIngredient("Чеснок","2 зубчика"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Обжарь полоски говядины.","Добавь брокколи и чеснок, туши 8 минут."),
            tip = "Высокий белок, мало углеводов.", lenten = false
        ),
        Recipe(
            id = "diab_omelette_veg", name = "Омлет с овощами без хлеба", emoji = "🍳",
            goals = listOf("fit","lose","dry","light"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 300, protein = 22, fat = 20, carbs = 8, timeMinutes = 12,
            ingredients = listOf(RecipeIngredient("Яйца","3 шт"), RecipeIngredient("Шпинат","50 г"), RecipeIngredient("Помидор","1 шт")),
            steps = listOf("Взбей яйца.","Добавь овощи, готовь под крышкой 5 минут."),
            tip = "Без быстрых углеводов.", lenten = false
        ),
        Recipe(
            id = "diab_turkey_salad", name = "Салат с индейкой и авокадо", emoji = "🥗",
            goals = listOf("fit","lose","dry"), mealTypes = listOf("lunch","dinner","snack"), servings = 1,
            calories = 400, protein = 36, fat = 24, carbs = 12, timeMinutes = 20,
            ingredients = listOf(RecipeIngredient("Филе индейки","130 г"), RecipeIngredient("Авокадо","0.5 шт"), RecipeIngredient("Огурец","1 шт"), RecipeIngredient("Листья салата","80 г")),
            steps = listOf("Обжарь индейку, нарежь.","Смешай с овощами и авокадо."),
            tip = "Сытно при низких углеводах.", lenten = false
        ),
        Recipe(
            id = "diab_zucchini_eggs", name = "Кабачковые оладьи с яйцом", emoji = "🥒",
            goals = listOf("lose","fit","light"), mealTypes = listOf("breakfast","lunch"), servings = 1,
            calories = 280, protein = 18, fat = 18, carbs = 14, timeMinutes = 20,
            ingredients = listOf(RecipeIngredient("Кабачок","1 шт"), RecipeIngredient("Яйца","2 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Натри кабачок, отожми.","Смешай с яйцами.","Обжарь оладьи на масле."),
            tip = "Низкоуглеводная замена блинам.", lenten = false
        ),
        Recipe(
            id = "diab_chicken_cauliflower", name = "Курица с цветной капустой", emoji = "🍗",
            goals = listOf("fit","lose","dry"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 380, protein = 42, fat = 16, carbs = 16, timeMinutes = 30,
            ingredients = listOf(RecipeIngredient("Куриное филе","150 г"), RecipeIngredient("Цветная капуста","200 г"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Запеки филе и капусту 25 минут при 190°C.","Посоли."),
            tip = "Капуста вместо гарнира с крахмалом.", lenten = false
        ),
        Recipe(
            id = "diab_tuna_salad", name = "Салат с тунцом и яйцом", emoji = "🥗",
            goals = listOf("fit","lose","dry"), mealTypes = listOf("lunch","dinner","snack"), servings = 1,
            calories = 360, protein = 38, fat = 18, carbs = 8, timeMinutes = 10,
            ingredients = listOf(RecipeIngredient("Тунец консервированный","120 г"), RecipeIngredient("Яйцо","2 шт"), RecipeIngredient("Огурец","1 шт"), RecipeIngredient("Листья салата","80 г")),
            steps = listOf("Свари яйца, нарежь.","Смешай с тунцом и овощами."),
            tip = "Почти без углеводов.", lenten = false
        ),
        Recipe(
            id = "diab_veg_soup", name = "Овощной суп без картофеля", emoji = "🍲",
            goals = listOf("lose","light","dry"), mealTypes = listOf("lunch","dinner"), servings = 2,
            calories = 240, protein = 12, fat = 8, carbs = 28, timeMinutes = 30,
            ingredients = listOf(RecipeIngredient("Капуста","200 г"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Сельдерей","100 г"), RecipeIngredient("Куриное филе","100 г")),
            steps = listOf("Отвари курицу, нарежь.","Добавь овощи, вари 20 минут."),
            tip = "Лёгкий суп без крахмала.", lenten = false
        ),
        Recipe(
            id = "diab_greek_yogurt_nuts", name = "Греческий йогурт с орехами", emoji = "🥛",
            goals = listOf("fit","lose","dry"), mealTypes = listOf("breakfast","snack"), servings = 1,
            calories = 260, protein = 20, fat = 14, carbs = 12, timeMinutes = 3,
            ingredients = listOf(RecipeIngredient("Греческий йогурт","200 г"), RecipeIngredient("Грецкий орех","20 г")),
            steps = listOf("Выложи йогурт.","Посыпь орехами."),
            tip = "Белок без сахара.", lenten = false
        ),

        // ═══════════════ ДЛЯ АЛЛЕРГИКОВ (без топ-аллергенов) ═══════════════
        Recipe(
            id = "alg_turkey_rice", name = "Индейка с рисом и кабачком", emoji = "🍗",
            goals = listOf("fit","lose","light","mass"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 500, protein = 44, fat = 12, carbs = 56, timeMinutes = 30,
            ingredients = listOf(RecipeIngredient("Филе индейки","150 г"), RecipeIngredient("Рис","80 г"), RecipeIngredient("Кабачок","1 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Отвари рис.","Потуши индейку с кабачком.","Подавай вместе."),
            tip = "Без яиц, молока, орехов, морепродуктов.", lenten = false
        ),
        Recipe(
            id = "alg_beef_potato", name = "Говядина с картофелем", emoji = "🥩",
            goals = listOf("mass","fit"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 620, protein = 46, fat = 24, carbs = 52, timeMinutes = 50,
            ingredients = listOf(RecipeIngredient("Говядина","150 г"), RecipeIngredient("Картофель","2 шт"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Обжарь говядину.","Добавь морковь и картофель, влей воду.","Туши 40 минут."),
            tip = "Простой состав без аллергенов.", lenten = false
        ),
        Recipe(
            id = "alg_veg_stew", name = "Овощное рагу", emoji = "🥘",
            goals = listOf("lose","light","fit"), mealTypes = listOf("lunch","dinner","snack"), servings = 2,
            calories = 360, protein = 8, fat = 12, carbs = 56, timeMinutes = 35,
            ingredients = listOf(RecipeIngredient("Картофель","2 шт"), RecipeIngredient("Кабачок","1 шт"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Лук","1 шт"), RecipeIngredient("Болгарский перец","1 шт")),
            steps = listOf("Нарежь овощи.","Обжарь лук и морковь, добавь остальное.","Туши 25 минут."),
            tip = "Гипоаллергенное, подходит почти всем.", lenten = true
        ),
        Recipe(
            id = "alg_chicken_buckwheat", name = "Курица с гречкой и морковью", emoji = "🍗",
            goals = listOf("fit","mass","lose"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 500, protein = 44, fat = 12, carbs = 54, timeMinutes = 30,
            ingredients = listOf(RecipeIngredient("Куриное филе","150 г"), RecipeIngredient("Гречка","70 г"), RecipeIngredient("Морковь","1 шт")),
            steps = listOf("Отвари гречку.","Потуши курицу с морковью.","Подавай вместе."),
            tip = "Базовое безопасное блюдо.", lenten = false
        ),
        Recipe(
            id = "alg_rice_porridge", name = "Рисовая каша на воде", emoji = "🍚",
            goals = listOf("light","lose"), mealTypes = listOf("breakfast"), servings = 1,
            calories = 300, protein = 6, fat = 3, carbs = 66, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Рис","70 г"), RecipeIngredient("Вода","300 мл"), RecipeIngredient("Яблоко","1 шт")),
            steps = listOf("Свари рис на воде.","Добавь яблоко."),
            tip = "Без молока и аллергенов.", lenten = true
        ),
        Recipe(
            id = "alg_turkey_potato", name = "Индейка с картофельным пюре на воде", emoji = "🥔",
            goals = listOf("fit","mass","light"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 480, protein = 42, fat = 10, carbs = 56, timeMinutes = 35,
            ingredients = listOf(RecipeIngredient("Филе индейки","150 г"), RecipeIngredient("Картофель","2 шт")),
            steps = listOf("Отвари картофель, разомни на воде от варки.","Потуши индейку.","Подавай вместе."),
            tip = "Без молочного и яиц.", lenten = false
        ),
        Recipe(
            id = "alg_beef_buckwheat", name = "Говядина с гречкой", emoji = "🥩",
            goals = listOf("mass","fit"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 560, protein = 46, fat = 18, carbs = 52, timeMinutes = 45,
            ingredients = listOf(RecipeIngredient("Говядина","150 г"), RecipeIngredient("Гречка","70 г"), RecipeIngredient("Лук","1 шт")),
            steps = listOf("Отвари гречку.","Потуши говядину с луком 35 минут."),
            tip = "Простой безопасный состав.", lenten = false
        ),
        Recipe(
            id = "alg_veg_soup", name = "Овощной суп с курицей", emoji = "🍲",
            goals = listOf("light","lose","fit"), mealTypes = listOf("lunch","dinner"), servings = 2,
            calories = 400, protein = 32, fat = 10, carbs = 46, timeMinutes = 40,
            ingredients = listOf(RecipeIngredient("Куриное филе","150 г"), RecipeIngredient("Картофель","2 шт"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Кабачок","1 шт")),
            steps = listOf("Отвари курицу, нарежь.","Добавь овощи, вари 20 минут."),
            tip = "Без частых аллергенов.", lenten = false
        ),
        Recipe(
            id = "alg_apple_baked", name = "Печёные яблоки с корицей", emoji = "🍎",
            goals = listOf("light","lose"), mealTypes = listOf("snack","breakfast"), servings = 1,
            calories = 160, protein = 2, fat = 1, carbs = 40, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Яблоко","2 шт"), RecipeIngredient("Корица","щепотка")),
            steps = listOf("Удали сердцевину.","Посыпь корицей.","Запекай 20 минут."),
            tip = "Безопасный сладкий перекус.", lenten = true
        ),
        Recipe(
            id = "alg_rice_veg", name = "Рис с тушёными овощами", emoji = "🍚",
            goals = listOf("light","lose","fit"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 420, protein = 10, fat = 8, carbs = 80, timeMinutes = 25,
            ingredients = listOf(RecipeIngredient("Рис","90 г"), RecipeIngredient("Кабачок","1 шт"), RecipeIngredient("Морковь","1 шт"), RecipeIngredient("Лук","1 шт")),
            steps = listOf("Отвари рис.","Потуши овощи.","Смешай."),
            tip = "Полностью растительное и безопасное.", lenten = true
        ),
        Recipe(
            id = "alg_fish_potato", name = "Треска с картофелем", emoji = "🐟",
            goals = listOf("fit","lose","light"), mealTypes = listOf("lunch","dinner"), servings = 1,
            calories = 420, protein = 38, fat = 10, carbs = 48, timeMinutes = 35,
            ingredients = listOf(RecipeIngredient("Филе трески","180 г"), RecipeIngredient("Картофель","2 шт"), RecipeIngredient("Оливковое масло","1 ст.л.")),
            steps = listOf("Запеки картофель 25 минут.","Добавь рыбу на 15 минут.","Посоли."),
            tip = "Без молочного и яиц.", lenten = false
        )


    )

 /** Рецепты под цель и тип приёма пищи. */
    fun forGoalAndMeal(goal: String, mealType: String): List<Recipe> =
        all.filter { goal in it.goals && mealType in it.mealTypes }

 /** Все рецепты под цель. */
    fun forGoal(goal: String): List<Recipe> = all.filter { goal in it.goals }

    fun forMeal(mealType: String): List<Recipe> = all.filter { mealType in it.mealTypes }

 /** Только постные (растительные) рецепты. */
    fun lentenRecipes(): List<Recipe> = all.filter { it.lenten }

 /** Постные рецепты по типу приёма пищи. */
    fun lentenByMeal(mealType: String): List<Recipe> =
        all.filter { it.lenten && mealType in it.mealTypes }

    fun byId(id: String): Recipe? = all.firstOrNull { it.id == id }
}
