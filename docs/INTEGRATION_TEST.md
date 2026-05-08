# CCI World — Integration Test Guide

Test manuale del comportamento di `cci_world` (sostituzione vene) in combinazione
con `cci_radar` (o `cci_scanner`) e JourneyMap.

`cci_world` compila e si avvia **standalone**: questa configurazione serve solo
per test di integrazione manuali e non introduce alcuna dipendenza a compile time.

---

## Regole

- `cci_world` **non** dipende da `cci_radar` / `cci_scanner` a compile time.
- Nessun import di classi `cci_radar` o JourneyMap nel codice sorgente.
- I jar di integrazione sono **runtime only** e vivono solo dentro
  `libs/integration/` di **questa** repo.
- Gradle **non** legge repo sorelle: nessun riferimento a `../cci-radar` o
  `../cci-scanner`.
- `libs/integration/*.jar` è gitignored: i jar non vengono mai committati.
- Senza jar di integrazione, `./gradlew clean build` e `./gradlew runClient`
  funzionano normalmente.

---

## Setup — copia manuale dei jar

L'utente copia manualmente in `libs/integration/`:

```
libs/integration/
├── .gitkeep                          (committato, mantiene la cartella)
├── cci_radar-<versione>.jar          (oppure cci_scanner-<versione>.jar)
├── journeymap-neoforge-<versione>.jar
└── jmi-neoforge-<versione>.jar       (opzionale, JourneyMap Integration)
```

Tutti i `*.jar` presenti nella cartella vengono caricati come runtime only
quando si attiva la modalità integrazione. I jar `*-sources.jar` e
`*-javadoc.jar` sono esclusi automaticamente.

---

## Avvio

### Modalità standalone (default)

```
./gradlew clean build
./gradlew runClient
```

Carica solo `cci_world` + `create` + `createoreexcavation`. Nessun jar in
`libs/integration/` viene letto.

### Modalità integrazione

```
./gradlew runClient -PcciIntegration=true
```

Gradle elenca a console i jar caricati:

```
[CCI World] integration: loading 3 jar(s) from libs/integration/
  - cci_radar-1.0.0.jar
  - journeymap-neoforge-1.21.1-6.0.0-beta.66.jar
  - jmi-neoforge-1.21.1-1.9.jar
```

Se `libs/integration/` è vuota o mancante, Gradle stampa un **warning** (non un
errore) e il client parte comunque con il solo `cci_world`.

---

## Mod attesi a runtime

| Modalità                          | Mod caricati                                                    |
|-----------------------------------|-----------------------------------------------------------------|
| `runClient`                       | `cci_world`, `create`, `createoreexcavation`                    |
| `runClient -PcciIntegration=true` | tutti i precedenti + ogni jar in `libs/integration/` (es. `cci_radar`, `journeymap`, `jmi`) |

---

## Checklist test in-game (OP player)

### Fase 1 — baseline

```
/cci_world set_test_vein zinc
/cci_radar unlock_tier 1
/cci_radar debug_scan_real 8
/cci_radar debug_resource_distribution
```

**Atteso:** il radar riporta una vena di **zinc** sul chunk corrente.

### Fase 2 — sostituzione

```
/cci_world replace_test_vein zinc copper
/cci_radar debug_scan_real 8
/cci_radar resync
/cci_radar debug_resource_distribution
```

**Atteso:**
- il radar **non** riporta più zinc sul chunk
- il radar riporta **copper** sul chunk
- nessun marker stale di zinc su JourneyMap
- nessun crash, nessun pebble/marker duplicato

---

## Troubleshooting

**Warning `no jars found in libs/integration/`:**
copiare manualmente almeno il jar `cci_radar` (o `cci_scanner`) e il jar di
JourneyMap nella cartella, poi rilanciare con `-PcciIntegration=true`.

**Configuration cache stale dopo aver aggiornato un jar:**
```
./gradlew runClient -PcciIntegration=true --no-configuration-cache
```

**Conflitti di versione tra mod:**
rimuovere dalla cartella `libs/integration/` i jar non necessari; vengono
caricati tutti i `*.jar` presenti.
