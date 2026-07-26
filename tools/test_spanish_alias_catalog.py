#!/usr/bin/env python3
from __future__ import annotations

import json
import re
import unicodedata
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CATALOG_PATH = ROOT / "app/src/main/assets/hok_counters.json"
EXPECTED_TITLES = {
    "Angela": "La Maga de Fuego",
    "Bai Qi": "El Arma Suprema",
    "Flowborn (Tank)": "Puño de la Paz",
    "Flowborn (Mage)": "Corazón Arcano",
    "Shouyue": "El Francotirador",
    "Xuance": "La Hoz Justiciera",
    "Augran": "El Sumo Sacerdote",
    "Dr Bian": "El Boticario",
    "Mai Shiranui": "La Ninja de Fuego",
    "Cai Yan": "La Alegre Canción",
    "Fatih": "El Conquistador",
    "Chano": "El Último Lobo",
}


def normalize(value: str) -> str:
    without_diacritics = "".join(
        character
        for character in unicodedata.normalize("NFD", value.strip())
        if unicodedata.category(character) != "Mn"
    )
    return re.sub(r"\s+", " ", without_diacritics.lower())


def recognition_aliases(hero: dict[str, object]) -> list[str]:
    identity_aliases = hero.get("identity_aliases", {})
    if not isinstance(identity_aliases, dict):
        raise AssertionError(f"identity_aliases invalido para {hero.get('name')}")

    aliases = list(hero.get("aliases", []))
    aliases.extend(identity_aliases.get("display_titles", []))
    aliases.extend(identity_aliases.get("historical_names", []))
    for localized_aliases in identity_aliases.get("localized_aliases", {}).values():
        aliases.extend(localized_aliases)

    if not all(isinstance(alias, str) and alias.strip() for alias in aliases):
        raise AssertionError(f"Alias vacio o invalido para {hero.get('name')}")
    return aliases


def main() -> None:
    catalog = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))
    heroes = catalog["heroes"]
    assert len(heroes) == 116, f"Se esperaban 116 heroes, hay {len(heroes)}"

    heroes_by_name = {hero["name"]: hero for hero in heroes}
    for name, title in EXPECTED_TITLES.items():
        assert name in heroes_by_name, f"Heroe requerido ausente: {name}"
        display_titles = heroes_by_name[name].get("identity_aliases", {}).get("display_titles", [])
        assert title in display_titles, f"Titulo espanol ausente para {name}: {title}"

    aliases_by_normalized_value: dict[str, str] = {}
    for hero in heroes:
        for alias in recognition_aliases(hero):
            normalized_alias = normalize(alias)
            existing_hero = aliases_by_normalized_value.get(normalized_alias)
            assert existing_hero is None, (
                f"Alias normalizado duplicado: {alias!r} para {hero['name']} y {existing_hero}"
            )
            aliases_by_normalized_value[normalized_alias] = hero["name"]

    print("Spanish alias catalog contract passed: 12 titles, 116 heroes, unique normalized aliases")


if __name__ == "__main__":
    main()
