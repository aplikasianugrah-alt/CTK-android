if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("/sw.js").catch(error => {
    console.warn("Registrasi service worker CTK gagal", error);
  });
}
