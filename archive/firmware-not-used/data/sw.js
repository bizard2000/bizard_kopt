const CACHE_NAME = 'smoker-v1';
const OFFLINE_URL = '/offline.html';

// Файлы для кэширования при установке
const STATIC_CACHE = [
    '/',
    '/index.html',
    '/style.css',
    '/app.js',
    '/uPlot.iife.min.js',
    '/uPlot.min.css',
    '/manifest.json',
    '/icons/icon-192.png',
    '/icons/icon-512.png'
];

// Установка Service Worker
self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME)
            .then(cache => cache.addAll(STATIC_CACHE))
            .then(() => self.skipWaiting())
    );
});

// Активация и очистка старых кэшей
self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys().then(cacheNames => {
            return Promise.all(
                cacheNames.map(cacheName => {
                    if (cacheName !== CACHE_NAME) {
                        return caches.delete(cacheName);
                    }
                })
            );
        }).then(() => self.clients.claim())
    );
});

// Стратегия кэширования: Cache First, затем Network
self.addEventListener('fetch', event => {
    // Пропустить WebSocket и запросы к API
    if (event.request.url.includes('/ws') || 
        event.request.url.includes('/api/')) {
        return;
    }
    
    event.respondWith(
        caches.match(event.request)
            .then(response => {
                if (response) {
                    return response;
                }
                
                return fetch(event.request).then(response => {
                    // Кэшируем только успешные ответы
                    if (!response || response.status !== 200) {
                        return response;
                    }
                    
                    const responseToCache = response.clone();
                    caches.open(CACHE_NAME)
                        .then(cache => {
                            cache.put(event.request, responseToCache);
                        });
                    
                    return response;
                }).catch(() => {
                    // Офлайн режим
                    if (event.request.mode === 'navigate') {
                        return caches.match(OFFLINE_URL);
                    }
                    return new Response('Офлайн режим');
                });
            })
    );
});

// Фоновая синхронизация (для фоновых задач)
self.addEventListener('sync', event => {
    if (event.tag === 'send-commands') {
        event.waitUntil(sendQueuedCommands());
    }
});

async function sendQueuedCommands() {
    // Логика отправки команд из очереди
    const queue = await getCommandQueue();
    // ... отправка команд на сервер
}