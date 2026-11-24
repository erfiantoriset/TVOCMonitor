# TVOC Monitor (Android)

Aplikasi Android (Kotlin + Jetpack Compose) untuk menampilkan data M5Stack Fire
(`TVOC_MQTT.m5f`) dari broker MQTT publik **test.mosquitto.org:1883** (TCP, tanpa TLS, tanpa login).

## Cara menjalankan
1. Buka Android Studio → **File › Open** → pilih folder `TVOCMonitor`.
2. Tunggu Gradle sync (butuh internet; Android Studio akan mengunduh Gradle 8.11.1, AGP 8.7.3, Kotlin 2.0.21).
   Jika Android Studio menawarkan upgrade AGP/Kotlin, boleh diterima.
3. Jalankan ke HP/emulator (minSdk 24 / Android 7.0).

## Topic yang di-subscribe
Base topic default: `m5fire/tvoc/b7de4536` → aplikasi subscribe `m5fire/tvoc/b7de4536/#`

| Sub-topic | Isi |
|---|---|
| `temperature` | Suhu ENV III (°C) |
| `tvoc` / `tvoc_baseline` | TVOC (ppb) dan baseline |
| `eco2` / `eco2_baseline` | eCO2 (ppm) dan baseline |
| `h2` / `ethanol` | Sinyal mentah H2 dan Ethanol |
| `json` | Semua nilai sekaligus (dipakai untuk History & Export) |

Host, port, dan base topic bisa diubah di menu **Settings**.

## Fitur
- **Dashboard**: sesuai mockup (Temperature, TVOC + badge, gauge eCO2, baseline, H2, Ethanol, status koneksi).
- **History**: grafik tren TVOC/eCO2 + tabel 1000 data terakhir (disimpan di memori selama aplikasi terbuka).
- **Settings**: ubah broker/port/topic, reconnect.
- **Export Data**: ekspor riwayat ke CSV lalu bagikan (WhatsApp, Drive, Email, dll).

## Kategori warna
- TVOC (ppb): ≤220 Good, ≤660 Moderate, ≤2200 Poor, >2200 Unhealthy
- eCO2 (ppm): <1000 Safe, <2000 Elevated, ≥2000 High

## File penting
- `DashboardViewModel.kt` – koneksi MQTT (Eclipse Paho), parsing pesan, riwayat
- `MainActivity.kt` – tampilan dashboard, bottom bar, export CSV
- `Components.kt` – kartu kaca, badge, skala garis, gauge eCO2
- `OtherScreens.kt` – History & Settings

## Build APK tanpa Android Studio (GitHub Actions)
1. Buat repository baru di GitHub, upload seluruh isi folder `TVOCMonitor` (termasuk folder `.github`).
2. Buka tab **Actions** → workflow **Build APK** akan berjalan otomatis (atau klik **Run workflow**).
3. Setelah selesai (±5–8 menit), buka run tersebut → bagian **Artifacts** → unduh `TVOCMonitor-apk` (zip berisi `app-debug.apk`).
4. Salin APK ke HP, izinkan "Install dari sumber tidak dikenal", lalu install.
