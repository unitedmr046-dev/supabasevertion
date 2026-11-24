# HANDOVER PROYEK: iyonzkasir v2.0 (Rombak Total Siap Launch)

## 1. Visi & Alur Produk
- Target: UMKM, warung, gerobakan, hingga **Owner multi-cabang/multi-gerobak**.
- Arsitektur: **Offline-First (Room SQLite UUID) + Auto Cloud Sync (Firebase Firestore & Auth)**.
- Auth 2 Lapis:
  1. Owner daftar/login pakai **Email + Password** di HP (otomatis terbuat 1 Usaha + Cabang Utama). Di HP cabang, Owner login sekali lalu pilih HP itu untuk cabang mana.
  2. Jika Owner kerja sendiri (belum ada karyawan): langsung masuk tanpa layar PIN. Jika sudah ada karyawan: muncul layar pilih nama + **PIN 4–6 digit** (anti-bruteforce lockout 30 detik).
- **Karyawan 100% Custom Akses (`AppAccess`):** Nama & jabatan bebas ketik (Kasir, Dapur 1, Dapur 2, Admin, dll.), izin ditentukan murni lewat checklist halaman & tindakan khusus (termasuk memisah izin "Lihat Modal HPP & Untung Bersih").
- **Web Menu Digital (`web/index.html`):** Support QR meja (`?store=...&meja=...`), Paket Bundling, cek stok, order masuk real-time ke tab Pesanan Web di kasir + WhatsApp.

## 2. Lingkungan Build & Aturan Penting
- Edit lewat **GitHub Codespaces terminal di HP**; build via **GitHub Actions** (file `.github/workflows/android-build.yml`).
- **ATURAN PASTE:** Terminal HP macet jika paste >120 baris sekaligus. Selalu pecah per ~80–100 baris (`>` lalu `>>`) dan kirim 1 sub-fase per giliran chat.
- Stack terpasang & hijau di CI: `compileSdk = 36`, `targetSdk = 36`, `minSdk = 24`, AGP `8.9.1`, Gradle `8.14.3`, JDK `17`, Kotlin `2.0.20`, KSP `2.0.20-1.0.25`, CameraX `1.4.2` + ML Kit `17.3.0`.
- Foto web dikompres kecil ~20KB Data URI via `ImageHelper` agar gratis di Firebase Spark (tanpa ImgBB API key).

## 3. Progress Saat Ini
- [x] Langkah 1: CI Workflow + SDK 36 + Manifest + ProGuard
- [x] Langkah 2: `core/` (`Theme.kt`, `Formatters.kt`, `Access.kt`, `Security.kt`, `SessionManager.kt`)
- [x] Langkah 3: `data/local/` (`Entities.kt`, `CatalogDaos.kt`, `OrderDaos.kt`, `FinanceDaos.kt`, `IyonzDatabase.kt`)
- [x] Langkah 4A: `data/remote/` (`ImageHelper.kt`, `CloudGateway.kt`, `FirebaseAuthCatalog.kt`, `FirebaseSalesSync.kt`)
- [x] Langkah 4B: `data/repo/` + `SyncCoordinator.kt`
- [x] Langkah 5: `di/AppContainer.kt`, `ui/auth/*`, `ui/home/HomeV2Screen.kt`
- [x] Langkah 6A: `hardware/*` + `ui/pos/*` (POS, Bundling, Checkout, Sukses, Struk)
- [x] Langkah 6B: `ui/activity/*` (Dapur, Open Bill & Riwayat, Kasbon & Member, Web Orders)
- [x] Langkah 6C-1: `ui/manage/` bagian 1 (Produk/Kategori/Stok, Paket, Pengeluaran, Shift, Laporan+CSV)
- [x] Langkah 6C-2: `ui/manage/` bagian 2 (Cabang & Karyawan Custom, Web QR, Printer/Tema/Backup, ManageHub)
- [ ] Langkah 6D: Sambungkan `MainActivity.kt` & `IyonzApp` ke UI v2 + hapus file lama -> APK v2 siap instal!
- [ ] Langkah 7: Update `web/index.html` + `firestore.rules` + setup Firebase Console + tes APK
