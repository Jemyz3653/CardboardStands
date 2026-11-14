"""Build the stand model and its pixel-art materials."""

import json
import random
from pathlib import Path

from PIL import Image, ImageDraw

root = Path(__file__).resolve().parents[1] / "src/main/resources"
assets = root / "assets/cardboardstands"


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def cuboid(start, end, origin, angle, style, front=None, axis="x"):
    faces = {direction: {"texture": "#" + style} for direction in
             ("down", "up", "north", "south", "west", "east")}
    if front:
        faces["north"] = {"texture": "#" + front, "uv": [0, 0, 16, 16]}
    return {
        "from": start,
        "to": end,
        "rotation": {"origin": origin, "axis": axis, "angle": angle},
        "faces": faces,
    }


def textures():
    directory = assets / "textures/block"
    directory.mkdir(parents=True, exist_ok=True)
    rng = random.Random(2614)
    for name, base in (("cardboard", (164, 110, 63)), ("edge", (121, 77, 44)), ("paper", (201, 166, 115))):
        image = Image.new("RGB", (16, 16))
        for y in range(16):
            for x in range(16):
                tone = rng.choice((-7, -4, -1, 0, 0, 3, 8))
                if name == "edge" and x % 4 == 1:
                    tone += 18
                image.putpixel((x, y), tuple(channel + tone for channel in base))
        image.save(directory / f"{name}.png")

    icon = Image.new("RGB", (32, 32), "#3a2d26")
    draw = ImageDraw.Draw(icon)
    draw.rectangle((1, 1, 30, 30), fill="#584338")
    draw.polygon([(15, 13), (26, 24), (26, 27), (23, 27), (12, 15)], fill="#2d201a")
    draw.polygon([(16, 15), (24, 24), (24, 25), (14, 15)], fill="#a56c42")
    draw.polygon([(9, 5), (24, 6), (25, 8), (22, 25), (20, 27), (4, 25), (3, 23), (7, 7)], fill="#2d201a")
    draw.polygon([(9, 7), (23, 8), (20, 25), (5, 23)], fill="#c99361")
    draw.polygon([(10, 9), (21, 10), (19, 22), (7, 21)], fill="#dfccab")
    draw.polygon([(8, 19), (12, 15), (14, 17), (17, 14), (20, 18), (19, 22), (7, 21)], fill="#789564")
    draw.rectangle((16, 11, 17, 12), fill="#e5ab58")
    icon.resize((128, 128), Image.Resampling.NEAREST).save(assets / "icon.png")


def model():
    panel_pivot = [8, 0, 4.5]
    elements = [
        cuboid([2.2, 0.17, 3.875], [13.8, 14, 5.15], panel_pivot, 15, "edge", "cardboard"),
        cuboid([2.75, 2, 3.84], [13.25, 13.3, 3.875], panel_pivot, 15, "paper", "paper"),
        cuboid([6.75, 0, 11.35], [9.25, 8.6, 12.15], [8, 0, 12], -35, "edge", "cardboard"),
        cuboid([6.65, 0, 11.05], [9.35, 0.55, 12.65], [8, 0, 12], 0, "edge", "cardboard"),
    ]
    materials = {k: f"cardboardstands:block/{v}" for k, v in
                 (("particle", "cardboard"), ("cardboard", "cardboard"), ("edge", "edge"), ("paper", "paper"))}
    write(assets / "models/block/stand.json", {
        "ambientocclusion": False,
        "textures": materials,
        "elements": elements,
        "display": {
            "gui": {"rotation": [24, 142, 0], "translation": [0, 1, 0], "scale": [0.88] * 3},
            "ground": {"translation": [0, 2, 0], "scale": [0.4] * 3},
            "fixed": {"scale": [0.8] * 3},
            "firstperson_righthand": {"rotation": [5, 135, 0], "translation": [0, 2, 0], "scale": [0.65] * 3},
            "thirdperson_righthand": {"rotation": [70, 140, 0], "translation": [0, 2, 0], "scale": [0.42] * 3},
        },
    })
    for turn in range(4):
        angle = turn * 90
        pivot = [8, 8, 15.5]
        write(assets / f"models/block/wall_stand_{turn}.json", {
            "ambientocclusion": False,
            "textures": materials,
            "elements": [
                cuboid([2, 2, 14.45], [14, 14, 16], pivot, angle, "edge", "cardboard", axis="z"),
                cuboid([2.65, 2.65, 14.40], [13.35, 13.35, 14.45], pivot, angle, "paper", "paper", axis="z"),
            ],
        })
    variants = {}
    for facing, angle in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        variants[f"facing={facing},wall=false"] = {"model": "cardboardstands:block/stand", "y": angle}
        for turn in range(4):
            variants[f"facing={facing},wall=true,turn={turn}"] = {
                "model": f"cardboardstands:block/wall_stand_{turn}", "y": angle,
            }
    write(assets / "blockstates/stand.json", {"variants": variants})
    write(assets / "items/stand.json", {
        "model": {"type": "minecraft:model", "model": "cardboardstands:block/stand"},
    })


def data():
    mod = root / "data/cardboardstands"
    write(mod / "recipe/stand.json", {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": ["PP", "PS"],
        "key": {"P": "minecraft:paper", "S": "minecraft:stick"},
        "result": {"id": "cardboardstands:stand", "count": 1},
    })
    write(mod / "loot_table/blocks/stand.json", {
        "type": "minecraft:block", "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
            "entries": [{"type": "minecraft:item", "name": "cardboardstands:stand"}]}],
    })
    write(mod / "advancement/recipes/stand.json", {
        "parent": "minecraft:recipes/root",
        "criteria": {"paper": {"trigger": "minecraft:inventory_changed", "conditions": {
            "items": [{"items": "minecraft:paper"}]}}},
        "requirements": [["paper"]],
        "rewards": {"recipes": ["cardboardstands:stand"]},
    })


if __name__ == "__main__":
    textures()
    model()
    data()
