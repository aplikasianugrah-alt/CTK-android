# CTK Kasir Android

Aplikasi Android Kotlin khusus tampilan **Katalog/Kasir** yang terhubung langsung ke:

`https://aksesoris.anugrahsatu.com/`

## Sinkronisasi

- Aplikasi tidak membuat database produk atau transaksi terpisah.
- WebView memakai aplikasi web dan backend produksi yang sama.
- Perubahan produk, harga, stok, transaksi, dan tampilan web otomatis digunakan oleh Android saat halaman memuat data terbaru.
- Cookie/sesi disimpan agar login tetap aktif.
- Cetak struk dari web diarahkan ke dialog cetak Android.

## Mode kasir

- Setelah halaman siap, aplikasi otomatis membuka menu **Kasir**.
- Tombol hamburger dan sidebar web tetap tersedia untuk navigasi sesuai hak akses akun.
- Katalog kasir memiliki filter kategori otomatis seperti Cincin, Bando, Anting, Gelang, dan kategori produk lainnya.
- Produk ditampilkan 12 item per halaman dengan navigasi pagination.
- Keranjang dibersihkan dan katalog dirender ulang otomatis setelah transaksi berhasil.
- Tombol tambah setoran selalu memuat ulang cash penjualan terbaru milik kasir sebelum membuka formulir.
- Tautan dari domain lain dibuka oleh aplikasi eksternal, bukan di dalam WebView.
- Jika koneksi gagal, aplikasi menampilkan halaman coba lagi.

> Pembatasan APK ini adalah pembatasan antarmuka. Hak akses dan keamanan data tetap harus diterapkan pada role pengguna di backend web.

## Build APK

Gunakan JDK 17 dari Android Studio, lalu jalankan:

```powershell
.\gradlew.bat assembleDebug
```

Hasil APK debug: `app/build/outputs/apk/debug/app-debug.apk`.

