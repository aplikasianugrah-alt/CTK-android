const ICON_CACHE = 'ctk-icons-v3';
const ICON_ASSETS = [
  '/manifest.webmanifest?v=3',
  '/icons/favicon-32-v3.png',
  '/icons/icon-180-v3.png',
  '/icons/icon-192-v3.png',
  '/icons/icon-512-v3.png',
  '/icons/icon-maskable-192-v3.png',
  '/icons/icon-maskable-512-v3.png'
];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(ICON_CACHE).then(cache => cache.addAll(ICON_ASSETS)));
  self.skipWaiting();
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys()
      .then(keys => Promise.all(
        keys.filter(key => key.startsWith('ctk-icons-') && key !== ICON_CACHE)
          .map(key => caches.delete(key))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (url.origin !== self.location.origin) return;
  if (!url.pathname.startsWith('/icons/') && url.pathname !== '/manifest.webmanifest') return;

  event.respondWith(
    caches.match(event.request).then(cached => cached || fetch(event.request).then(response => {
      const copy = response.clone();
      caches.open(ICON_CACHE).then(cache => cache.put(event.request, copy));
      return response;
    }))
  );
});
