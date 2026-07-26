package com.example.honorofkingsassistant

import com.google.mlkit.vision.text.Text

/**
 * Reads only the calibrated five-by-two scoreboard regions. Hero titles are
 * resolved through the offline canonical/Spanish-alias catalog; unknown lines
 * remain unknown rather than being fuzzy-guessed as a hero.
 */
class ScoreboardBitmapAnalyzer(private val catalog: CounterCatalog) {
    fun analyze(text: Text, frameWidth: Int, frameHeight: Int): ScoreboardSnapshot {
        val geometry = ScoreboardGeometry.forFrame(frameWidth, frameHeight)
        val lines = text.textBlocks.flatMap { block -> block.lines }

        fun rows(side: TeamSide, rowGeometry: List<ScoreboardRowGeometry>): List<ScoreboardRow> =
            rowGeometry.mapIndexed { index, row ->
                val titleLines = lines.filter { line -> line.boundingBox?.let { bounds ->
                    val x = (bounds.left + bounds.right) / 2
                    val y = (bounds.top + bounds.bottom) / 2
                    x in row.heroTitle.left until row.heroTitle.right &&
                        y in row.heroTitle.top until row.heroTitle.bottom
                } == true }
                val titleHero = titleLines.firstNotNullOfOrNull { line ->
                    catalog.findHero(line.text)?.name
                }
                val player = lines.firstOrNull { line -> line.boundingBox?.let { bounds ->
                    val x = (bounds.left + bounds.right) / 2
                    val y = (bounds.top + bounds.bottom) / 2
                    x in row.playerName.left until row.playerName.right &&
                        y in row.playerName.top until row.playerName.bottom
                } == true }?.text
                val level = lines.firstOrNull { line -> line.boundingBox?.let { bounds ->
                    val x = (bounds.left + bounds.right) / 2
                    val y = (bounds.top + bounds.bottom) / 2
                    x in row.level.left until row.level.right &&
                        y in row.level.top until row.level.bottom
                } == true }?.text?.filter(Char::isDigit)?.toIntOrNull()
                ScoreboardRow(side, index + 1, titleHeroName = titleHero, playerName = player, level = level)
            }
        return ScoreboardSnapshot(rows(TeamSide.ALLY, geometry.allies), rows(TeamSide.ENEMY, geometry.enemies))
    }
}
