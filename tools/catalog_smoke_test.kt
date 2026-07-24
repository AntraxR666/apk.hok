package com.example.honorofkingsassistant

fun main() {
    val catalog = CounterCatalog(
        listOf(
            Hero(
                id = "annette",
                name = "Annette",
                role = "Roamer/Support",
                counters = listOf(HeroCounter("Donghuang", "Supresión dirigida."))
            ),
            Hero("donghuang", "Donghuang", "Roamer/Support", emptyList())
        )
    )
    check(catalog.findHero("  ANNETTE ")?.name == "Annette")
    check(catalog.countersFor("annette").single().heroName == "Donghuang")
    println("catalog smoke tests passed")
}
