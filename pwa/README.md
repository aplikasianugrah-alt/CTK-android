# PWA icon deployment

Folder ini berisi aset PWA siap salin ke document root situs produksi.

1. Salin `icons/`, `manifest.webmanifest`, dan `sw.js` ke root situs sehingga URL-nya menjadi `/icons/...`, `/manifest.webmanifest`, dan `/sw.js`.
2. Tambahkan ke `<head>`:

```html
<link rel="manifest" href="/manifest.webmanifest?v=3">
<link rel="icon" type="image/png" sizes="32x32" href="/icons/favicon-32-v3.png">
<link rel="apple-touch-icon" sizes="180x180" href="/icons/icon-180-v3.png">
```

3. Daftarkan service worker satu kali dari JavaScript utama:

```js
if ('serviceWorker' in navigator) {
  window.addEventListener('load', () => navigator.serviceWorker.register('/sw.js'));
}
```

Nama berkas `-v3` dan query manifest `?v=3` sengaja dibuat baru untuk memutus cache ikon lama. Pertahankan nilai `id` manifest (`/`) pada pembaruan berikutnya agar browser mengenali PWA sebagai aplikasi yang sama.
