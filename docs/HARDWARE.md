# Hardware for Tavuzi Gadgets

The spec: tills are a desktop computer, a laptop and an Android phone; no receipt
printer yet ("please advise"). The system runs on one shop computer and every till
opens it in a browser over the shop Wi-Fi/LAN.

## Recommended receipt printer

An **80 mm thermal printer with an Ethernet (LAN) port and ESC/POS**, for example
Epson TM-T20III (Ethernet), Xprinter XP-Q200 or XP-Q80 LAN (cheaper; check local availability). Why network and not USB/Bluetooth: the server prints, so *every* till,
including the Android phone, uses the same printer with no drivers.

Setup:

1. Plug the printer into the shop router and give it a fixed IP address (hold the
   feed button while switching on to print its current IP; set a fixed one on the
   router or in the printer's web page).
2. In the system: **Hardware** → enter the IP, port `9100`, paper width
   (80 mm = 48 characters) → **Save** → **Print test page**.
3. Optional: tick "Print automatically after every sale".

Without a printer, receipts print from the browser (Receipt → Print) on any
printer, and customers can get an SMS receipt.

## Cash drawer

Any 12/24 V cash drawer with an **RJ11** cable plugs into the printer's DK port.
Tick "Open the cash drawer" on the Hardware screen: it opens when a cash sale's
receipt prints.

## Barcode scanner

Any **USB barcode scanner** (keyboard mode) works on the desktop and laptop: click
the Sell search box and scan. Unit barcodes (e.g. a carton) add that unit. On the
Android phone, the camera button on the Sell screen scans barcodes (Chrome on
Android).

## Android phone

Open the system address in Chrome on the shop Wi-Fi, then menu → **Add to Home
screen**: it opens full-screen like an app. Keep the phone plugged in at the counter
if it is used all day.
