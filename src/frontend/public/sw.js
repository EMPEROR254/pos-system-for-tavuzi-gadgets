// Service worker: lets a till open the app even when it can't reach the server,
// so selling can carry on offline. It keeps a copy of the built app (the files
// listed in Vite's build manifest). API calls are never cached: offline sales
// are kept by the app itself and sent when the server is back.
const APP_FILES = ['/', '/index.html', '/manifest.webmanifest', '/icon.svg']

// A cheap hash of the build manifest names the cache, so each new build gets a
// fresh copy and old ones are removed.
function hash(text) {
  let h = 0
  for (let i = 0; i < text.length; i++) h = (Math.imul(31, h) + text.charCodeAt(i)) | 0
  return (h >>> 0).toString(36)
}

self.addEventListener('install', (event) => {
  event.waitUntil(
    (async () => {
      const response = await fetch('/.vite/manifest.json', { cache: 'no-store' })
      const text = await response.text()
      const files = new Set(APP_FILES)
      for (const entry of Object.values(JSON.parse(text))) {
        files.add('/' + entry.file)
        for (const css of entry.css ?? []) files.add('/' + css)
        for (const asset of entry.assets ?? []) files.add('/' + asset)
      }
      const cache = await caches.open('tavuzi-app-' + hash(text))
      await cache.addAll([...files])
      await self.skipWaiting()
    })(),
  )
})

self.addEventListener('activate', (event) => {
  event.waitUntil(
    (async () => {
      const keep = (await caches.keys()).filter((k) => k.startsWith('tavuzi-app-'))
      // The newest cache is the one just installed; remove the others.
      const newest = keep[keep.length - 1]
      await Promise.all(keep.filter((k) => k !== newest).map((k) => caches.delete(k)))
      await self.clients.claim()
    })(),
  )
})

self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url)
  if (event.request.method !== 'GET' || url.origin !== self.location.origin || url.pathname.startsWith('/api/')) {
    return
  }
  if (event.request.mode === 'navigate') {
    // Pages: the server's latest when it answers, otherwise the saved app.
    event.respondWith(fetch(event.request).catch(() => caches.match('/index.html')))
    return
  }
  // Built files have hashed names and never change: the saved copy is fine.
  event.respondWith(caches.match(event.request).then((hit) => hit ?? fetch(event.request)))
})
