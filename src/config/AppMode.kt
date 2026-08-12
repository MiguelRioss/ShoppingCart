package config

enum class AppMode {
    Online,
    Offline;

    companion object {
        fun fromEnvironment(): AppMode =
            when (Environment.get("APP_MODE")?.trim()?.lowercase()) {
                "online" -> Online
                else -> Offline
            }
    }
}
