# Kâtip

Kişisel, yerel harcama defteri Android uygulaması.

## Özellikler
- Harcamayı elle ekleme: ad, tutar, kategori ve isteğe bağlı not
- Aylık toplam ve tüm kayıtların toplamı
- Harcama geçmişini görüntüleme ve tek tek silme
- Veriler Android SharedPreferences içinde yalnızca cihazda saklanır
- SMS, banka bildirimi, internet veya hesap bağlantısı yoktur

## APK
GitHub Actions içindeki Katip Android APK iş akışı her ana dal güncellemesinde debug APK üretir. Başarılı çalışmanın Katip-debug-apk artifact dosyası indirilebilir.

## Derleme
JDK 17 ve Gradle 8.7 ile gradle assembleDebug komutu kullanılır.
