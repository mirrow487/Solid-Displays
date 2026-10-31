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

## Tasarım Masası

Masaya sağ tıkla:

- **Sol panel:** blok/eşya arama ve seçme (tıkla → yeni parça), parça listesi, Kopyala / Sil / Geri / İleri.
- **Orta:** 3D önizleme. Sol tık parçayı seçer; okları (Taşı) veya yüz tutamaklarını (Boyut) sürükle.
  Boş yerde sürükle: döndür · Shift+sürükle veya orta tuş: kaydır · tekerlek: yakınlaştır.
  Üstte ızgara adımı (1, 1/2, 1/4, 1/8, 1/16), Taşı/Boyut modu ve Ortala.
- **Parça sekmesi:** konum/boyut/döndürme sayı alanları, hızlı 90° döndürme, kolizyon aç/kapat, toplam malzeme.
- **Kaydet sekmesi:** model adı, Boş Blueprint slotu ve envanter, "Blueprint olarak kaydet".

Kısayollar: Ctrl+Z / Ctrl+Y, Ctrl+D (kopyala), Delete, G (Taşı), S (Boyut).
Yarım kalan tasarım masada saklanır. Sunucu her kaydı doğrular (limitler, izinli bloklar, açık masa).

## Nasıl çalışır (kolizyon)

Her parça bir vanilla **Block/Item Display** ile görünür. Kolizyonu açık her parça için ayrıca
görünmez bir `solid_displays:model_collider` entity'si oluşturulur. Bu entity vanilla'nın tekne/shulker
mekanizmasını (`canBeCollidedWith`) kullanır, bu yüzden istemci ve sunucu aynı kutuyu hesaplar:
titreme yok, multiplayer'da ekstra paket yok. Kutunun X/Y/Z boyutu senkronize veri olarak taşınır.
Oyun entity aramalarını sınırlı bir payla yaptığı için 3 bloktan büyük parçalar birden çok kutuya bölünür.
90°'nin katı olmayan açılarla döndürülmüş parçalar, şekli takip eden küçük kutulardan oluşan bir
"merdiven" ile sarılır (ızgara adımı `rotatedCollisionStep`, varsayılan 1/8 blok; parça başına en fazla
`maxCollidersPerRotatedPart` kutu, gerekirse ızgara kabalaşır).

Survival'da yerleştirme malzeme harcar; söküldüğünde aynı malzemeler modelin yerine düşer.
Yaratıcı modda yerleştirme bedavadır ve söküm bir şey düşürmez.

Parçalar ortak bir model ID'si taşır; modeller dünyaya bağlı `data/solid_displays_models.dat`
dosyasında tutulur. Yüklenmemiş chunk'taki parçalar, chunk yüklenince otomatik temizlenir.

## Otomatik oyun içi testler

`src/gametest` altında, gerçek istemcide oyuncu fiziğiyle çalışan testler var (mod jar'ına girmez):

```bash
./gradlew runClientGameTest
```

Ekransız bir Linux sunucuda çalıştırmak için (yazılımsal OpenGL):

```bash
LIBGL_ALWAYS_SOFTWARE=1 SDL_VIDEO_FORCE_EGL=1 xvfb-run -a -s "-screen 0 1920x1080x24" ./gradlew runClientGameTest
```

## Tarifler

- **Tasarım Masası**: `cam panel, kâğıt, cam panel` / `tahta, çalışma masası, tahta` / `tahta, boş, tahta`
- **Boş Blueprint ×8**: 8 kâğıt + ortada mavi boya

## Config

`config/solid_displays.json` (sunucu tarafı): parça sayısı/boyut limitleri, yerleştirme mesafesi,
malzeme formülü (`volumeMultiplier`, `minPerBlockType`, `requireMaterials`).
