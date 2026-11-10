# Kâtip

Kişisel, yerel bütçe asistanı Android uygulaması.

## Özellikler
- Ana ekran: toplam bakiye, aylık gelir, gider, kalan, aylık bütçe çubuğu, harcama dağılımı
- Gelir/gider ekleme, düzenleme, silme (manuel giriş)
- Banka bildirimlerinden ve SMS'lerden otomatik harcama/gelir yakalama
- Otomatik kategori: market, yemek, ulaşım, yakıt, fatura, alışveriş, sağlık, eğlence
- Raporlar: günlük, haftalık, aylık özet ve kategori dağılımı
- Veriler yalnızca cihazda saklanır (internet yok)

## APK
Her `main` güncellemesinde GitHub Actions debug APK üretir ve **Releases** sayfasına `Katip.apk` olarak yükler.

## Derleme
JDK 17 ve Gradle 8.7 ile `gradle assembleDebug`.
