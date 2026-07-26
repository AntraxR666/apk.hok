package com.example.honorofkingsassistant

import android.content.Context
import org.json.JSONObject

class ItemCatalog(val items: List<HoKItem>) {
    private val byId = items.associateBy { it.id }
    init {
        require(items.isNotEmpty())
        require(byId.size == items.size) { "IDs de ítem duplicados" }
        require(items.all { it.name.isNotBlank() && it.cost >= 0 && it.tier in 1..3 && it.tags.isNotEmpty() })
    }
    fun withTag(tag: String): List<HoKItem> = items.filter { tag in it.tags }
    fun find(id: String): HoKItem? = byId[id]

    companion object {
        fun load(context: Context): ItemCatalog = parse(
            context.assets.open("hok_items.json").bufferedReader().use { it.readText() }
        )
        fun parse(json: String): ItemCatalog {
            val root = JSONObject(json)
            val array = root.getJSONArray("items")
            return ItemCatalog(buildList {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val tags = item.getJSONArray("tags")
                    add(HoKItem(
                        id = item.getString("id"), name = item.getString("name"),
                        cost = item.getInt("cost"), tier = item.getInt("tier"),
                        tags = buildSet { for (tag in 0 until tags.length()) add(tags.getString(tag)) }
                    ))
                }
            })
        }
    }
}
