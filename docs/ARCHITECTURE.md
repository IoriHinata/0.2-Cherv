# Архитектура

- MarkovChain — локальная цепь второго порядка, сохраняет знаки препинания.
- SettingsStore — SharedPreferences для персонажей, мест, имён и фраз.
- MarkovViewModel — MVVM и StateFlow; обучение выполняется на Dispatchers.Default.
- MarkovApp — Jetpack Compose, GetContent для импорта и CreateDocument для экспорта.
- Вставка сущностей/реплик эвристическая: статистическая генерация не гарантирует связный сюжет.
