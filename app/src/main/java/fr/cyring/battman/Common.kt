package fr.cyring.battman

object C {
    const val MILLIS_PER_DAY = 24L * 60L * 60L * 1000L

    const val FONT_SIZE_SYSTEM: Int = 0
    const val FONT_SIZE_SMALL: Int = FONT_SIZE_SYSTEM + 1
    const val FONT_SIZE_MEDIUM: Int = FONT_SIZE_SMALL + 1
    const val FONT_SIZE_LARGE: Int = FONT_SIZE_MEDIUM + 1

    const val THEME_SYSTEM: Int = 0
    const val THEME_LIGHT: Int = THEME_SYSTEM + 1
    const val THEME_DARK: Int = THEME_LIGHT + 1

    const val BATTERY_ALERT_THRESHOLD = 10
    const val MIN_ALERT_THRESHOLD = 0
    const val MAX_ALERT_THRESHOLD = 100

    const val DEFAULT_MAX_ALERTS = 3
    const val MIN_MAX_ALERTS = 0
    const val MAX_MAX_ALERTS = 10

    const val CASE_FOCUS = 0
    const val CASE_AWAY = 1

    const val PREF_FOCUS = "focusRefresh"
    const val PREF_AWAY = "awayRefresh"

    const val PREF_TIME_FOCUS = "focusRefreshTime"
    const val PREF_TIME_AWAY = "awayRefreshTime"

    const val MIN_TIME_FOCUS = 0L
    const val MAX_TIME_FOCUS = 25000L
    const val DEFAULT_TIME_FOCUS = 3000L

    const val MIN_TIME_AWAY = 0L
    const val MAX_TIME_AWAY = 360L
    const val DEFAULT_TIME_AWAY = 15L

    const val DEFAULT_AUTO_FOCUS = true
    const val DEFAULT_AUTO_AWAY = false

    const val CYCLE: Int = 0
    const val STATUS: Int = CYCLE + 1
    const val HEALTH: Int = STATUS + 1
    const val TECH: Int = HEALTH + 1
    const val VOLTAGE: Int = TECH + 1
    const val TEMP: Int = VOLTAGE + 1
    const val ENERGY: Int = TEMP + 1
    const val CHARGE: Int = ENERGY + 1
    const val CURRENT: Int = CHARGE + 1
    const val AVG: Int = CURRENT + 1
    const val PLUG: Int = AVG + 1
    const val REMAIN: Int = PLUG + 1
    const val FULL: Int = REMAIN + 1
    const val BAT_SAV: Int = FULL + 1
    const val THERMAL: Int = BAT_SAV + 1
    const val PRESENT: Int = THERMAL + 1
    const val BAT_LOW: Int = PRESENT + 1
    const val PREDICT: Int = BAT_LOW + 1
    const val EMPTY: Int = PREDICT + 1
    const val BRIGHT: Int = EMPTY + 1
    const val ADAPTIVE: Int = BRIGHT + 1
    const val DARK_MOD: Int = ADAPTIVE + 1
    const val NIGHT_LT: Int = DARK_MOD + 1
    const val TOUCH: Int = NIGHT_LT + 1
    const val SMOOTH: Int = TOUCH + 1
    const val TIMEOUT: Int = SMOOTH + 1
    const val SCR_SAV: Int = TIMEOUT + 1
    const val SCR_OFF: Int = SCR_SAV + 1
    const val TORCH: Int = SCR_OFF + 1
}