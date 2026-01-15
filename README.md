# AutoSell (Fabric Client Mod)

**AutoSell** ist ein **Fabric Client-Mod**, der automatisch über ein **Sell-GUI** Items verkauft.  
Start mit `/startsell <item_id> <amount>` und Stop mit `/stopsell`.

> **Voraussetzung:** Dein Server muss einen Command `sell` haben, der ein Inventar-/Sell-GUI öffnet.

---

## Features

- Automatisches Verkaufen, sobald genug Items im Inventar vorhanden sind
- Exakte Menge pro Durchlauf (**1–64**)
- Item-Auswahl über **Minecraft Identifier** (z.B. `minecraft:stone`)
- Timeout/Fail-Safe, falls das Sell-GUI nicht öffnet
- Jederzeit stoppbar

---

## Commands

### Start

```txt
/startsell <item_id> <amount>
````

### Stop

```txt
/stopsell
```

---

## Beispiele

```txt
/startsell minecraft:stone 64
/startsell minecraft:iron_ingot 16
/stopsell
```

---

## So funktioniert’s (kurz)

AutoSell prüft jeden Tick, ob du mindestens `<amount>` vom Item im Inventar hast.

1. Wenn **kein GUI offen** ist, wird `sell` als Chat-Command gesendet.
2. Sobald das **Sell-GUI** offen ist, verschiebt AutoSell Items aus dem Inventar in **freie GUI-Slots**:

   * Wenn `amount == 64` und ein voller Stack gehalten wird → **schneller Transfer**
   * Sonst → **itemweise per Rechtsklick** für exakte Menge
3. Danach wird das GUI geschlossen und AutoSell wartet auf den nächsten Durchlauf.

> Pro Runde werden maximal **64 Items** übertragen.

---

## Installation

1. **Fabric Loader** installieren
2. **Fabric API** installieren
3. `autosell-*.jar` in den Ordner `mods/` legen
4. Spiel starten

---

## Hinweise / Limitierungen

* **Client-only:** macht nur GUI-Klicks + sendet `sell`.
* Wenn dein Server **kein Sell-GUI** öffnet oder es **anders aufgebaut** ist, kann es zu einem **Timeout** kommen.
* Pro Runde werden maximal **64 Items** übertragen.

---

## Wichtiger Hinweis (AFK / Botting)

AutoSell ist im Prinzip eine **AFK-/Automations-Mod**.
Auf manchen Servern kann so etwas als **Botting/Makro/Automatisierung** gewertet werden und gegen die Regeln verstoßen.

✅ Nutze den Mod nur, wenn es auf deinem Server erlaubt ist.
Im Zweifel: vorher im Regelwerk nachschauen oder einen Admin/Teammitglied fragen.
