#!/usr/bin/env python3
"""
Build the Pokémon battle helper's data (S4) from PokeAPI's CSV dump.

Writes app/src/main/assets/pokemon/{gen3,gen4,gen5,latest}.json: the type chart, species
(types, base stats, abilities, catch rate, evolution line), moves (type, category, power) and
what each ability does, as they were in that generation. Nothing is fetched while playing; rerun this to update.

Usage: python3 tools/pokedata/build_pokedata.py [--ref <pokeapi git ref>]

PokeAPI "past" tables store the OLD value with the LAST generation it applied to, so the value in
generation G is the past row with the smallest generation_id >= G, else the current value.
move_changelog rows hold the value from before changed_in_version_group; for generation G, the
earliest change made after G that sets a field gives that field's value. Abilities work the same
way: ability_changelog_prose describes the behaviour before the change, else the current short effect.
"""
import argparse
import csv
import io
import json
import os
import sys
import urllib.request
from collections import defaultdict

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app", "src", "main", "assets", "pokemon")
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache")
EN = "9"
TABLES = [
    "types", "type_names", "type_efficacy", "type_efficacy_past",
    "pokemon", "pokemon_species", "pokemon_species_names",
    "pokemon_types", "pokemon_types_past", "pokemon_stats", "pokemon_stats_past",
    "pokemon_abilities", "pokemon_abilities_past", "abilities", "ability_names",
    "moves", "move_names", "move_changelog", "version_groups",
    "ability_prose", "ability_changelog", "ability_changelog_prose",
]
GENERATIONS = {"gen3": 3, "gen4": 4, "gen5": 5, "latest": None}
# Before Gen 4, a damaging move's category came from its type.
GEN3_PHYSICAL = {"normal", "fighting", "flying", "poison", "ground", "rock", "bug", "ghost", "steel"}
STAT_IDS = ["1", "2", "3", "4", "5", "6"]  # hp, atk, def, sp.atk, sp.def, speed


def load(ref):
    os.makedirs(os.path.join(CACHE, ref), exist_ok=True)
    data = {}
    for t in TABLES:
        path = os.path.join(CACHE, ref, t + ".csv")
        if not os.path.exists(path):
            url = f"https://raw.githubusercontent.com/PokeAPI/pokeapi/{ref}/data/v2/csv/{t}.csv"
            with urllib.request.urlopen(url) as r, open(path, "wb") as f:
                f.write(r.read())
        with open(path, encoding="utf-8") as f:
            data[t] = list(csv.DictReader(f))
    return data


def names(rows, key):
    return {r[key]: r["name"] for r in rows if r["local_language_id"] == EN}


def first_sentences(text, limit=160):
    """Changelog prose can run long; keep whole sentences up to about [limit] characters."""
    text = " ".join(text.split())
    out = ""
    for part in text.replace(". ", ".\n").split("\n"):
        if out and len(out) + len(part) + 1 > limit: break
        out = (out + " " + part).strip()
    return out


def ability_texts(d, g, used):
    """Ability name -> what it does in generation g, for the abilities species in g can have."""
    ab_name = names(d["ability_names"], "ability_id")
    vg_gen = {v["id"]: int(v["generation_id"]) for v in d["version_groups"]}
    vg_order = {v["id"]: int(v["order"]) for v in d["version_groups"]}
    short = {r["ability_id"]: r["short_effect"] for r in d["ability_prose"] if r["local_language_id"] == EN}
    prose = {r["ability_changelog_id"]: r["effect"] for r in d["ability_changelog_prose"] if r["local_language_id"] == EN}
    later = defaultdict(list)
    for c in d["ability_changelog"]:
        if vg_gen[c["changed_in_version_group_id"]] > g and c["id"] in prose: later[c["ability_id"]].append(c)
    out = {}
    for aid, name in ab_name.items():
        if name not in used: continue
        old = sorted(later[aid], key=lambda c: vg_order[c["changed_in_version_group_id"]])
        text = first_sentences(prose[old[0]["id"]]) if old else " ".join(short.get(aid, "").split())
        if text: out[name] = text
    return dict(sorted(out.items()))


def past_value(past_rows, gen):
    """Rows of one entity's past table -> rows for the smallest generation_id >= gen, or None."""
    cands = sorted({int(r["generation_id"]) for r in past_rows if int(r["generation_id"]) >= gen})
    return [r for r in past_rows if int(r["generation_id"]) == cands[0]] if cands else None


def build(d, gen_key, gen):
    max_gen = max(int(s["generation_id"]) for s in d["pokemon_species"])
    g = gen or max_gen
    type_name = names(d["type_names"], "type_id")
    types = [t for t in d["types"] if int(t["id"]) < 10000 and t["generation_id"] and int(t["generation_id"]) <= g
             and t["identifier"] not in ("stellar", "unknown", "shadow")]
    type_ids = [t["id"] for t in types]
    ident = {t["id"]: t["identifier"] for t in d["types"]}

    # type chart
    eff = {(r["damage_type_id"], r["target_type_id"]): int(r["damage_factor"]) for r in d["type_efficacy"]}
    past = defaultdict(list)
    for r in d["type_efficacy_past"]:
        past[(r["damage_type_id"], r["target_type_id"])].append(r)
    chart = []
    for a in type_ids:
        row = []
        for t in type_ids:
            p = past_value(past[(a, t)], g)
            row.append((int(p[0]["damage_factor"]) if p else eff.get((a, t), 100)) / 100)
        chart.append(row)

    # species (default form of each)
    default_mon = {p["species_id"]: p["id"] for p in d["pokemon"] if p["is_default"] == "1"}
    sp_name = {r["pokemon_species_id"]: r["name"] for r in d["pokemon_species_names"] if r["local_language_id"] == EN}
    mon_types, mon_types_past = defaultdict(list), defaultdict(list)
    for r in d["pokemon_types"]: mon_types[r["pokemon_id"]].append(r)
    for r in d["pokemon_types_past"]: mon_types_past[r["pokemon_id"]].append(r)
    mon_stats, mon_stats_past = defaultdict(dict), defaultdict(list)
    for r in d["pokemon_stats"]: mon_stats[r["pokemon_id"]][r["stat_id"]] = int(r["base_stat"])
    for r in d["pokemon_stats_past"]: mon_stats_past[r["pokemon_id"]].append(r)
    mon_ab, mon_ab_past = defaultdict(list), defaultdict(list)
    for r in d["pokemon_abilities"]: mon_ab[r["pokemon_id"]].append(r)
    for r in d["pokemon_abilities_past"]: mon_ab_past[r["pokemon_id"]].append(r)
    ab_name = names(d["ability_names"], "ability_id")
    ab_gen = {a["id"]: int(a["generation_id"]) for a in d["abilities"]}

    species_rows = [s for s in d["pokemon_species"] if int(s["generation_id"]) <= g]
    species_rows.sort(key=lambda s: int(s["id"]))
    chains = defaultdict(list)
    for s in species_rows: chains[s["evolution_chain_id"]].append(s)
    depth = {}
    parent = {s["id"]: s["evolves_from_species_id"] for s in species_rows}
    def dep(i):
        if i not in depth:
            p = parent.get(i)
            depth[i] = 0 if not p or p not in parent else dep(p) + 1
        return depth[i]

    species = []
    for s in species_rows:
        mon = default_mon[s["id"]]
        tp = past_value(mon_types_past[mon], g) or mon_types[mon]
        tnames = [type_name[r["type_id"]] for r in sorted(tp, key=lambda r: int(r["slot"]))]
        stats = dict(mon_stats[mon])
        for sid in STAT_IDS:
            rows = [r for r in mon_stats_past[mon] if r["stat_id"] == sid]
            p = past_value(rows, g)
            if p: stats[sid] = int(p[0]["base_stat"])
        # abilities: past rows override by slot; blank ability = slot absent
        slots = {r["slot"]: r for r in mon_ab[mon]}
        p = past_value(mon_ab_past[mon], g)
        for r in (p or []):
            if r["ability_id"]: slots[r["slot"]] = r
            else: slots.pop(r["slot"], None)
        normal, hidden = [], None
        for slot in sorted(slots, key=int):
            r = slots[slot]
            if ab_gen.get(r["ability_id"], 99) > g: continue
            if r["is_hidden"] == "1":
                if g >= 5: hidden = ab_name[r["ability_id"]]
            else:
                normal.append(ab_name[r["ability_id"]])
        line = sorted(chains[s["evolution_chain_id"]], key=lambda x: (dep(x["id"]), int(x["id"])))
        # every root-to-last-stage path, so split evolutions (Eevee, Wurmple, Kirlia) keep their branches
        members = {x["id"] for x in line}
        kids = defaultdict(list)
        for x in line:
            if parent.get(x["id"]) in members: kids[parent[x["id"]]].append(x["id"])
        def paths(i):
            return [[i]] if not kids[i] else [[i] + p for k in kids[i] for p in paths(k)]
        roots = [x["id"] for x in line if parent.get(x["id"]) not in members]
        entry = {
            "id": int(s["id"]),
            "name": sp_name[s["id"]],
            "types": tnames,
            "stats": [stats[sid] for sid in STAT_IDS],
            "abilities": normal,
            "catchRate": int(s["capture_rate"]),
            "evo": [[sp_name[i] for i in p] for r in roots for p in paths(r)],
        }
        if hidden: entry["hidden"] = hidden
        species.append(entry)

    # moves
    vg_gen = {v["id"]: int(v["generation_id"]) for v in d["version_groups"]}
    vg_order = {v["id"]: int(v["order"]) for v in d["version_groups"]}
    changes = defaultdict(list)
    for r in d["move_changelog"]: changes[r["move_id"]].append(r)
    mv_name = names(d["move_names"], "move_id")
    moves = []
    for m in d["moves"]:
        if int(m["id"]) >= 10000 or int(m["generation_id"]) > g or m["id"] not in mv_name: continue
        cur = {"type_id": m["type_id"], "power": m["power"]}
        later = sorted((c for c in changes[m["id"]] if vg_gen[c["changed_in_version_group_id"]] > g),
                       key=lambda c: vg_order[c["changed_in_version_group_id"]])
        for field in cur:
            for c in later:
                if c[field]:
                    cur[field] = c[field]; break
        tname = ident[cur["type_id"]]
        dc = {"1": "status", "2": "physical", "3": "special"}[m["damage_class_id"]]
        if g <= 3 and dc != "status":
            dc = "physical" if tname in GEN3_PHYSICAL else "special"
        moves.append({"name": mv_name[m["id"]], "type": type_name[cur["type_id"]], "category": dc,
                      "power": int(cur["power"]) if cur["power"] else None})
    moves.sort(key=lambda x: x["name"])

    return {
        "generation": g,
        "label": gen_key,
        "types": [type_name[t] for t in type_ids],
        "chart": chart,
        "species": species,
        "moves": moves,
        "abilities": ability_texts(d, g, {a for sp in species for a in sp["abilities"] + ([sp["hidden"]] if "hidden" in sp else [])}),
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ref", default="master", help="PokeAPI git ref (branch or commit)")
    args = ap.parse_args()
    d = load(args.ref)
    os.makedirs(OUT, exist_ok=True)
    for key, gen in GENERATIONS.items():
        data = build(d, key, gen)
        data["source"] = f"PokeAPI {args.ref}"
        path = os.path.join(OUT, key + ".json")
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, separators=(",", ":"))
        print(f"{key}: gen {data['generation']}, {len(data['types'])} types, "
              f"{len(data['species'])} species, {len(data['moves'])} moves, {len(data['abilities'])} abilities, "
              f"{os.path.getsize(path)//1024} KB")


if __name__ == "__main__":
    sys.exit(main())
