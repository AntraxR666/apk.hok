import json
from pathlib import Path

data = json.loads((Path(__file__).resolve().parents[1] / "app/src/main/assets/hok_items.json").read_text(encoding="utf-8"))
items = data["items"]
assert len(items) >= 10
assert len({item["id"] for item in items}) == len(items)
assert all(item["name"] and item["tags"] and item["tier"] in (1, 2, 3) for item in items)
assert any("anti_heal" in item["tags"] for item in items)
assert any("magic_defense" in item["tags"] for item in items)
print("ITEM_CATALOG_OK")
