# Solid Displays

Minecraft **26.3** (Java Edition) için Fabric modu. Survival'da, komut kullanmadan,
**Tasarım Masası**'nda block/item display'lerden modeller tasarla, **Blueprint**'e kaydet
ve malzemeleriyle dünyaya **gerçek kolizyonlu** olarak yerleştir.

## Sürümler

| | |
|---|---|
| Minecraft | 26.3 |
| Fabric Loader | 0.20.1 |
| Fabric API | 0.165.0+26.3 |
| Loom | 1.19.3 (`net.fabricmc.fabric-loom`, obfuscation'sız / Mojang isimleri) |
| Gradle | 9.8.2 |
| Java | 25 |

## Derleme

```bash
./gradlew build
```

Çıktı: `build/libs/solid_displays-<sürüm>.jar`. Bu dosyayı ve Fabric API'yi `.minecraft/mods` klasörüne koy.

## Tarifler

- **Tasarım Masası**: `cam panel, kâğıt, cam panel` / `tahta, çalışma masası, tahta` / `tahta, boş, tahta`
- **Boş Blueprint ×8**: 8 kâğıt + ortada mavi boya

## Config

`config/solid_displays.json` (sunucu tarafı): parça sayısı/boyut limitleri, yerleştirme mesafesi,
malzeme formülü (`volumeMultiplier`, `minPerBlockType`, `requireMaterials`).
