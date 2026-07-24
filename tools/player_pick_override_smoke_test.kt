package com.example.honorofkingsassistant

fun main() {
    val autoLocked = DraftFlowState(
        moment = DraftMoment.PLAYER_LOCKED,
        shouldRecommendPicks = false,
        shouldShowStrategy = true,
        message = "auto locked"
    )
    val pending = PlayerPickStatePolicy.apply(autoLocked, PlayerPickOverride.PENDING)
    check(pending.moment == DraftMoment.PLAYER_SELECTING)
    check(pending.shouldRecommendPicks)
    check(!PlayerPickStatePolicy.isLocked(true, PlayerPickOverride.PENDING))

    val searching = DraftFlowState(
        moment = DraftMoment.TRACKING,
        shouldRecommendPicks = true,
        shouldShowStrategy = false,
        message = "tracking"
    )
    val locked = PlayerPickStatePolicy.apply(searching, PlayerPickOverride.LOCKED)
    check(locked.moment == DraftMoment.PLAYER_LOCKED)
    check(!locked.shouldRecommendPicks)
    check(PlayerPickStatePolicy.isLocked(false, PlayerPickOverride.LOCKED))

    check(PlayerPickStatePolicy.apply(autoLocked, PlayerPickOverride.AUTO) == autoLocked)
    println("PLAYER_PICK_OVERRIDE_OK")
}
