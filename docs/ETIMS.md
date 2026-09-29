# KRA eTIMS

Every sale becomes an eTIMS invoice and every return a credit note. They are
queued with the sale (nothing is lost) and sent to KRA in the background every
30 seconds; while KRA or the internet is down they wait and are retried with
growing gaps (1, 2, 5, 15, then every 30 minutes). The receipt shows the KRA
signature and a QR code once KRA has signed the invoice. The owner and manager
see everything under **More → KRA eTIMS**, including invoices KRA refused (with
KRA's reason and a "Send again" button).

## Going live

1. The shop applies on the KRA eTIMS portal for an **OSCU** (online sales control
   unit) integration for its branch. KRA issues the device serial number and,
   when the device is initialised, a **cmcKey** (a secret).
2. Put them in `.env` (never in git):
   `ETIMS_TIN`, `ETIMS_BRANCH_ID` (usually `00`), `ETIMS_DEVICE_SERIAL`,
   `ETIMS_CMC_KEY`.
3. Set `ETIMS_MODE=SANDBOX`, restart, make a few test sales and returns, and
   check them under KRA eTIMS and on KRA's sandbox portal.
4. Set `ETIMS_DEFAULT_ITEM_CLASS` to the KRA item classification code that fits
   the shop's goods (the value in `.env.example` is only a placeholder).
5. When KRA approves, set `ETIMS_MODE=PRODUCTION`. Invoices queued while eTIMS
   was `OFF` are all sent then, oldest first.

## To confirm during KRA sandbox certification

The OSCU calls (`/items/saveItems`, `/trnsSales/saveSales`), their field names,
the tax type letters (A exempt, B 16%, C zero-rated), payment type codes
(01 cash, 05 card, 06 mobile money), the item code format and the receipt
check link were built from KRA's OSCU documentation as understood at build time
and tested against a stand-in KRA server - not against KRA itself. All KRA
field names live in `etims/EtimsMapper.java` and the URLs in
`etims/EtimsProperties.java`, so any correction KRA asks for is made there.
