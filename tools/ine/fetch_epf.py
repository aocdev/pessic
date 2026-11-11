#!/usr/bin/env python3
"""Download INE EPF average household spending by COICOP 2018 group and write Pessic's data file.

Source: INE, Encuesta de Presupuestos Familiares (EPF), Tempus API.
  73807  Gasto según tipo de hogar
  73806  Gasto según tamaño del hogar
  73809  Gasto según nivel de ingresos mensuales netos regulares del hogar
Only "Gasto medio por hogar" (euros per household per year) is kept.

Usage: python3 tools/ine/fetch_epf.py [output.tsv]
Default output: pessic-core/src/main/resources/ine/epf.tsv
Run it once a year after INE publishes a new EPF (usually June) and commit the result.
"""
import datetime
import json
import re
import sys
import urllib.request

API = "https://servicios.ine.es/wstempus/js/ES/DATOS_TABLA/{}?nult=1&tip=AM"
TABLES = {"type": 73807, "size": 73806, "income": 73809}
MEASURE = "Gasto medio por hogar"

# Order must match Catalog.HOUSEHOLDS in the app (index = key).
HOUSEHOLD_TYPES = [
    "Persona sola con menos de 65 años", "Persona sola de 65 o más años", "Pareja sin hijos",
    "Pareja con 1 hijo", "Pareja con 2 hijos", "Pareja con 3 o más hijos",
    "Un adulto con hijos", "Otro tipo de hogar", "Total",
]


def fetch(table):
    with urllib.request.urlopen(API.format(table), timeout=120) as r:
        return json.load(r)


def income_bounds(label):
    """'Hasta 499 euros' -> (0, 499); 'De 1.000 a 1.499 euros' -> (1000, 1499); '5.000 euros o más' -> (5000, '')."""
    nums = [int(n.replace(".", "")) for n in re.findall(r"\d[\d.]*", label)]
    if label.startswith("Hasta"):
        return 0, nums[0]
    if "o más" in label:
        return nums[0], ""
    return nums[0], nums[1]


def main():
    out_path = sys.argv[1] if len(sys.argv) > 1 else "pessic-core/src/main/resources/ine/epf.tsv"
    rows, year = [], None
    for breakdown, table in TABLES.items():
        for s in fetch(table):
            meta = {m["T3_Variable"]: m for m in s["MetaData"]}
            measure = next(m["Nombre"] for v, m in meta.items() if v.startswith("Gastos totales"))
            group = meta.get("Grupos ECOICOP")
            if measure != MEASURE or group is None or group["Codigo"] in ("", "0", "00"):
                continue  # skip percentages, per-person figures and the "Índice general" total
            dim = next(m["Nombre"] for v, m in meta.items()
                       if v not in ("Grupos ECOICOP", "Tipo de dato") and not v.startswith("Gastos totales"))
            point = s["Data"][0]
            year = point["Anyo"]
            value = point["Valor"]
            if value is None:
                continue
            if breakdown == "type":
                key, lo, hi = HOUSEHOLD_TYPES.index(dim), "", ""
            elif breakdown == "size":
                key, lo, hi = (0 if dim == "Total" else int(dim[0])), "", ""
            else:
                if dim == "Total":
                    key, lo, hi = "total", "", ""
                else:
                    lo, hi = income_bounds(dim)
                    key = lo
            rows.append((breakdown, str(key), dim, str(lo), str(hi), group["Codigo"].zfill(2), f"{value:.2f}"))

    rows.sort(key=lambda r: (r[0], r[1].zfill(6), r[5]))
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("# INE, Encuesta de Presupuestos Familiares (EPF). Gasto medio por hogar, euros/any, grups COICOP 2018.\n")
        f.write(f"# year\t{year}\n")
        f.write(f"# retrieved\t{datetime.date.today().isoformat()}\n")
        f.write("# tables\t" + " ".join(f"{k}={v}" for k, v in TABLES.items()) + "\n")
        f.write("# source\thttps://www.ine.es/dyngs/INEbase/operacion.htm?c=Estadistica_C&cid=1254736176806\n")
        f.write("breakdown\tkey\tlabel\tincome_min\tincome_max\tgroup\tannual_eur\n")
        for r in rows:
            f.write("\t".join(r) + "\n")
    print(f"wrote {len(rows)} rows for {year} to {out_path}")


if __name__ == "__main__":
    main()
