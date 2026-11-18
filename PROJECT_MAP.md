# KÂTİP — Proje Haritası

> İlk harita: 18 Kasım 2026. README ve depo kökü esas alınmıştır; alt paketlerin tam taraması sonraki incelemede yapılmalıdır.

## Projenin amacı
Android üzerinde çalışan, kişisel ve yerel bütçe asistanı. README'ye göre veriler cihazda tutulur ve internet bağlantısı gerektirmez.

## Ana mimari
Android uygulaması → banka bildirimi/SMS alıcıları → işlem ayrıştırma → kategori belirleme → yerel veri saklama → raporlar ve bütçe ekranı.

## Kök dosyalar
- `MainActivity.kt`: Android uygulamasının ana Activity/UI giriş noktası.
- `Data.kt`: veri modeli ve/veya veri işlemleri (dosya adı ve proje bağlamı; ayrıntılı sembol taraması gerekli).
- `Parser.kt`: bildirim/SMS işlem metni ayrıştırma.
- `Receivers.kt`: Android bildirim/SMS olaylarını alan receiver bileşenleri.
- `README.md`: özellikler, yerel veri yaklaşımı ve derleme talimatı.
- `GEMINI.md`: Gemini için proje yönergeleri.
- `.gradle.properties`: Gradle yapılandırma ayarları.
- `Katip-Kod.zip`: kaynak arşivi; aktif derleme kaynağı olduğu varsayılmamalıdır.
- `.github/`: GitHub Actions otomasyonu.

## İşlevsel akış
1. Kullanıcı gelir/gideri manuel ekler veya banka bildirimi/SMS yakalanır.
2. Parser işlem metnini çözümler.
3. İşlem kategorisi belirlenir.
4. Yerel kayıtlar bakiye, bütçe ve günlük/haftalık/aylık raporlarda kullanılır.

## Derleme ve dağıtım
- README: JDK 17 + Gradle 8.7.
- Komut: `gradle assembleDebug`.
- `main` dalı güncellemelerinde GitHub Actions debug APK üretip Release'e `Katip.apk` olarak yükler.

## Güvenlik ve gizlilik
- README'ye göre veriler cihazda saklanır; bulut senkronizasyonu varsayılmamalıdır.
- SMS ve bildirim izinleri hassas olduğundan yalnızca gerekli izinler istenmeli; ham mesajlar gereksiz yere loglanmamalıdır.

## Harita güncelleme kuralı
Yeni receiver, parser, veri alanı, ekran veya izin eklendiğinde bu dosyayı aynı commit/PR içinde güncelle.
