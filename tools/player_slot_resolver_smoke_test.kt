package com.example.honorofkingsassistant

fun main() {
    val resolver = PlayerSlotResolver(requiredHits = 3, maxMisses = 2)

    check(resolver.resolve(PlayerSlotDetection(3, TeamSide.ENEMY, 1.0), null) == null)
    check(resolver.resolve(PlayerSlotDetection(2, TeamSide.ALLY, 0.80), null) == null)
    check(resolver.resolve(PlayerSlotDetection(2, TeamSide.ALLY, 0.99), null) == null)
    check(resolver.resolve(PlayerSlotDetection(2, TeamSide.ALLY, 0.99), null) == null)
    check(resolver.resolve(PlayerSlotDetection(2, TeamSide.ALLY, 0.99), null)?.slotIndex == 2)

    val manual = resolver.resolve(PlayerSlotDetection(4, TeamSide.ALLY, 1.0), 1)
    check(manual == PlayerSlotDetection(1, TeamSide.ALLY, 1.0))

    resolver.resolve(null, null)
    check(resolver.resolve(null, null) == null)

    println("PLAYER_SLOT_RESOLVER_OK")
}
