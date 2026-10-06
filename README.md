# Yakın — numarayla mesajlaşma (aynı ağda internetsiz, uzakta internetle)

## GitHub'a yükleme
1. Zip'i aç, içindeki **dosyaları** deponun ana dizinine yükle (`settings.gradle.kts` en üstte görünmeli).
2. `.github/workflows/build.yml` gizli klasördür; yüklenmediyse GitHub'da "Create new file" ile aynı yola elle oluştur.
3. Actions > Build APK > Artifacts > Yakin-debug-apk (ZIP iner, içinden .apk'yı kur).

## Nasıl çalışır
- **Aynı Wi-Fi ya da hotspot:** numaralar ağda otomatik duyurulur, internet gerekmez. Bluetooth yok.
- **Uzakta:** `server/` klasörünü bir yerde çalıştır, adresini uygulamada Profil > Sunucu adresi'ne yaz.
  Sonra sağ üstteki kişi simgesiyle numarayı yazıp bul.
- Mesajlar telefonda saklanır; çevrimdışıyken de okunur.
