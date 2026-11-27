const PWA_HEAD = [
  '<link rel="manifest" href="/manifest.webmanifest?v=3">',
  '<link rel="icon" type="image/png" sizes="32x32" href="/icons/favicon-32-v3.png">',
  '<link rel="apple-touch-icon" sizes="180x180" href="/icons/icon-180-v3.png">',
  '<script defer src="/ctk-pwa-register.js"></script>'
].join("\n");

function isPwaAsset(path) {
  return path === "/manifest.webmanifest" ||
    path === "/sw.js" ||
    path === "/ctk-pwa-register.js" ||
    /^\/icons\/(?:favicon-32|icon-(?:maskable-)?\d+)-v3\.png$/.test(path);
}

export default {
  async fetch(request, env, ctx) {
    ctx.passThroughOnException();
    const { pathname } = new URL(request.url);
    if (request.method !== "GET" && request.method !== "HEAD") return fetch(request);

    if (isPwaAsset(pathname)) {
      const asset = await env.PWA_ASSETS.fetch(request);
      if (asset.status !== 404) return asset;
    }
    if (pathname !== "/" || request.method !== "GET") return fetch(request);

    const headers = new Headers(request.headers);
    headers.delete("If-None-Match");
    headers.delete("If-Modified-Since");
    const origin = await fetch(new Request(request, { headers }));
    if (!origin.ok || !origin.headers.get("Content-Type")?.includes("text/html")) return origin;

    const response = new Response(origin.body, origin);
    response.headers.delete("ETag");
    response.headers.delete("Last-Modified");
    return new HTMLRewriter()
      .on("head", { element: head => head.append(PWA_HEAD, { html: true }) })
      .transform(response);
  }
};
