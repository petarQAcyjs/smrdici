enum class ExpenseCategory {
    GROCERIES,
    UTILITIES,
    RENT,
    TRANSPORTATION,
    ENTERTAINMENT,
    HEALTH,
    EDUCATION,
    CLOTHING,
    TRAVEL,
    FOOD,
    COFFEE,
    ALCOHOL,
    CIGARETTES,
    GIFTS,
    SUBSCRIPTIONS,
    ELECTRONICS,
    HOME,
    BEAUTY,
    PETS,
    SPORTS,
    INVESTMENTS,
    DEBT,
    INSURANCE,
    TAXES,
    CHARITY,
    BUSINESS,
    CHILDREN,
    PERSONAL_CARE,
    SHOPPING,
    MAINTENANCE,
    SERVICES,
    SAVINGS,
    LOAN,
    RAMPA,
    PARKING,
    OTHER;
    
    fun getDisplayName(): String {
        return when (this) {
            GROCERIES -> "Намирнице"
            UTILITIES -> "Рачуни"
            RENT -> "Станарина"
            TRANSPORTATION -> "Превоз"
            ENTERTAINMENT -> "Забава"
            HEALTH -> "Здравље"
            EDUCATION -> "Образовање"
            CLOTHING -> "Одећа"
            TRAVEL -> "Путовање"
            FOOD -> "Храна"
            COFFEE -> "Кафа"
            ALCOHOL -> "Алкохол"
            CIGARETTES -> "Цигарете"
            GIFTS -> "Поклони"
            SUBSCRIPTIONS -> "Претплате"
            ELECTRONICS -> "Електроника"
            HOME -> "Кућа"
            BEAUTY -> "Лепота"
            PETS -> "Кућни љубимци"
            SPORTS -> "Спорт"
            INVESTMENTS -> "Инвестиције"
            DEBT -> "Дуг"
            INSURANCE -> "Осигурање"
            TAXES -> "Порези"
            CHARITY -> "Донације"
            BUSINESS -> "Пословни трошкови"
            CHILDREN -> "Деца"
            PERSONAL_CARE -> "Лична нега"
            SHOPPING -> "Куповина"
            MAINTENANCE -> "Одржавање"
            SERVICES -> "Услуге"
            SAVINGS -> "Штедња"
            LOAN -> "Кредит"
            RAMPA -> "Рампа"
            PARKING -> "Паркинг"
            OTHER -> "Остало"
        }
    }
} 