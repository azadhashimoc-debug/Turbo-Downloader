package com.example.engine

/**
 * Smart engine mode for optimizing downloads under different network conditions.
 */
enum class SmartEngineMode(
    val title: String,
    val description: String
) {
    BALANCED(
        title = "Balanslaşdırılmış (Standart)",
        description = "Orta və yaxşı internet üçün stabil 2-hissəli paralel axın"
    ),
    TURBO_MULTI_STREAM(
        title = "Ağıllı Çoxaxınlı Turbo (Aşağı Sürət üçün)",
        description = "Zəif internetdə server buraxılış gücünü 4 paralel HTTP Range axınına bölərək maksimal sürət əldə edir"
    ),
    LOW_LATENCY_ECO(
        title = "Sabit Zəif Şəbəkə (Qırılmaya Qarşı)",
        description = "Qeyri-sabit və tez kəsilən internetlərdə kiçik buferlər və təkrar avtomatik cəhdlərlə fasiləsiz yükləmə"
    )
}
