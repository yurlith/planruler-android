package com.planruler.pipecalculator

enum class CalculationJurisdiction { PRELIMINARY, SWITZERLAND, GERMANY, EUROPEAN_DRAFT }

data class CalculationStandardProfile(
    val id: String,
    val displayName: String,
    val jurisdiction: CalculationJurisdiction,
    val edition: String,
    val sourceIds: List<String>,
    val calculationEnabled: Boolean,
    val complianceClaimAllowed: Boolean = false,
    val blockingReason: String? = null,
)

object CalculationStandards {
    const val PRELIMINARY_ID = "planruler-preliminary-heat-loss-v2"
    const val SIA_384_2_CH_ID = "sia-384-2-2020-c1-2021-ch"
    const val DIN_EN_12831_DE_ID = "din-en-12831-1-2017-din-ts-2020-de"
    const val PREN_12831_DRAFT_ID = "pren-12831-1-2025-draft"

    val preliminary = CalculationStandardProfile(
        id = PRELIMINARY_ID,
        displayName = "Preliminary heat-loss estimate",
        jurisdiction = CalculationJurisdiction.PRELIMINARY,
        edition = "PlanRuler 0.3",
        sourceIds = listOf("planruler-preliminary-heat-loss-0.3"),
        calculationEnabled = true,
    )

    val swissSia = CalculationStandardProfile(
        id = SIA_384_2_CH_ID,
        displayName = "SIA 384/2 Switzerland",
        jurisdiction = CalculationJurisdiction.SWITZERLAND,
        edition = "2020 + C1:2021",
        sourceIds = listOf("sia-384-2-2020-c1-2021"),
        calculationEnabled = false,
        blockingReason = "Licensed clauses, climate inputs, and official reference cases require engineering validation.",
    )

    val germanDin = CalculationStandardProfile(
        id = DIN_EN_12831_DE_ID,
        displayName = "DIN EN 12831-1 Germany",
        jurisdiction = CalculationJurisdiction.GERMANY,
        edition = "2017 with national supplement profile pending",
        sourceIds = listOf("din-en-12831-1-2017"),
        calculationEnabled = false,
        blockingReason = "The licensed national method and exact reference cases have not been validated.",
    )

    val europeanDraft = CalculationStandardProfile(
        id = PREN_12831_DRAFT_ID,
        displayName = "prEN 12831-1 draft",
        jurisdiction = CalculationJurisdiction.EUROPEAN_DRAFT,
        edition = "2025 draft",
        sourceIds = listOf("pren-12831-1-2025-draft"),
        calculationEnabled = false,
        blockingReason = "Draft standards can be researched but never presented as final compliance methods.",
    )

    val all = listOf(preliminary, swissSia, germanDin, europeanDraft)

    fun requireRunnable(id: String): CalculationStandardProfile {
        val profile = all.singleOrNull { it.id == id }
            ?: throw CalculationException("Unknown calculation standard profile: $id")
        if (!profile.calculationEnabled) {
            throw CalculationException(profile.blockingReason ?: "Calculation profile is locked")
        }
        return profile
    }
}
