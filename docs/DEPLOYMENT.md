# Deployment - Spring Boot service

## Option A: VPS with Docker (recommended)

1. Create a VPS (Ubuntu 24.04, 2 GB RAM minimum for Java + PostgreSQL).
2. Install Docker: `curl -fsSL https://get.docker.com | sh`.
3. Copy the project (or `git clone`) to `/opt/app`, then `cp .env.example .env`
   and fill in real values (database password, JWT secret, M-Pesa keys).
   On the first start the app creates the owner account from `OWNER_USERNAME` /
   `OWNER_PASSWORD`; sign in, change that password under "My account", then add
   staff under "Staff". Set `COOKIE_SECURE=true` only when the site runs on HTTPS.
   Receipts print the `SHOP_*` values (till and paybill numbers, KRA PIN, return
   policy); set `PRINTER_HOST` once a network thermal printer is installed,
   otherwise staff print receipts from the browser. The printer can also be set
   on the Hardware screen (see docs/HARDWARE.md for what to buy).
   SMS receipts and promotions need an Africa's Talking account: set `AT_USERNAME`,
   `AT_API_KEY` and (once approved) `AT_SENDER_ID`. Without a key, SMS are only logged.
   Set `SMS_CALLBACK_TOKEN` to a long random string, then in the Africa's Talking
   dashboard (SMS → Callback URLs) enter, with the site's public HTTPS address:
   delivery reports `https://<site>/api/sms/callbacks/<token>/delivery`,
   incoming messages `.../<token>/inbound` (needs a shortcode or two-way number,
   so "Reply STOP" works) and bulk opt-out `.../<token>/opt-out`.
   Promotions are held between `SMS_QUIET_START` and `SMS_QUIET_END` (Nairobi time).
   Social media posting (Facebook, Instagram, TikTok) needs `APP_PUBLIC_URL`,
   `SOCIAL_TOKEN_KEY` and the app keys - see docs/SOCIAL_MEDIA.md.
   KRA eTIMS stays `OFF` (invoices are kept, not sent) until the shop's device is
   registered with KRA - see docs/ETIMS.md for the steps.
4. `docker compose up -d --build`.
5. Put Nginx or Caddy in front for HTTPS (Caddy gets certificates automatically):
   `app.example.co.ke { reverse_proxy localhost:8080 }`.
6. M-Pesa callbacks need a public HTTPS URL - set it in `.env`.

## Backups

- Daily database dump: `docker compose exec -T db pg_dump -U app app | gzip > /backup/app-$(date +%F).sql.gz`
  from cron, and copy dumps off the server (e.g. to object storage).
- Test a restore at least once before go-live.

## Updates

`git pull && docker compose up -d --build`. Flyway applies new migrations on start.

## Option B: on-premise computer

For shops without reliable internet, run the same `docker compose` on a
computer in the shop; provide a UPS and an automatic daily backup to USB/cloud.
