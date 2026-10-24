package io.github.magisk317.sesame.task.antForest

/** Builds the prop actions required before an energy collection run. */
internal class ForestPropUsagePlanner(
    private val renewalPolicy: ForestPropRenewalPolicy = ForestPropRenewalPolicy(),
) {
    internal data class State(
        val now: Long,
        val doubleEnabled: Boolean,
        val doubleEndTime: Long,
        val robExpandEnabled: Boolean,
        val robExpandEndTime: Long,
        val stealthEnabled: Boolean,
        val stealthEndTime: Long,
        val shieldEnabled: Boolean,
        val shieldEndTime: Long,
        val bombEnabled: Boolean,
        val bombEndTime: Long,
        val bubbleBoostEnabled: Boolean,
    )

    internal data class Plan(
        val useDouble: Boolean,
        val useRobExpand: Boolean,
        val useStealth: Boolean,
        val useShield: Boolean,
        val useBomb: Boolean,
        val useBubbleBoost: Boolean,
    ) {
        val hasActions: Boolean
            get() = useDouble || useRobExpand || useStealth || useShield || useBomb || useBubbleBoost
    }

    fun plan(state: State): Plan = Plan(
        useDouble = state.doubleEnabled && renewalPolicy.shouldRenewDoubleCard(
            state.doubleEndTime,
            state.now,
        ),
        useRobExpand = state.robExpandEnabled && state.robExpandEndTime < state.now,
        useStealth = state.stealthEnabled && state.stealthEndTime < state.now,
        useShield = state.shieldEnabled && !state.bombEnabled && renewalPolicy.shouldRenewShield(
            state.shieldEndTime,
            state.now,
        ),
        useBomb = state.bombEnabled && !state.shieldEnabled && renewalPolicy.shouldRenewEnergyBomb(
            state.bombEndTime,
            state.now,
        ),
        useBubbleBoost = state.bubbleBoostEnabled,
    )
}
