# Social media posts

Under **More → Social media** the owner links the shop's accounts, and the
owner or a manager writes a post (headline, description, one picture) and
posts it to the chosen accounts with one button. Posts are sent in the
background; the list shows for each account whether it was posted, is still
being sent, or failed (with the network's reason and a "Try again" button).

| Account | How it works |
|---|---|
| Facebook Page | Posted as a photo post on the Page. |
| Instagram (business) | Linked together with the Facebook Page it is connected to. Instagram fetches the picture from the shop's public address. Pictures must be between 4:5 (portrait) and 1.91:1 (landscape); the screen warns before posting. |
| TikTok | The photo goes to the account's TikTok **inbox**; open TikTok, add a sound and publish. TikTok only allows direct publishing for apps that passed its audit. |
| WhatsApp | WhatsApp has **no API for posting** to Status or Channels. "WhatsApp" opens the phone's share sheet with the picture and text (choose WhatsApp → My status or a chat). The saved business number adds an "Order on WhatsApp" link to posts. On a laptop WhatsApp opens with the text only. |

Pictures are turned into JPEGs of at most 1080 px (what the networks expect).
Access tokens are stored encrypted with `SOCIAL_TOKEN_KEY`. If that key is
ever changed, link the accounts again.

## Setting it up (once)

1. The site must be reachable on a public HTTPS address (the same one M-Pesa
   and SMS callbacks need). Set `APP_PUBLIC_URL` to it, e.g. `https://pos.tavuzi.co.ke`.
2. Set `SOCIAL_TOKEN_KEY` to a long random string.
3. **Facebook and Instagram:** on developers.facebook.com create a *Business*
   app, add *Facebook Login for Business*, and set the redirect URI to
   `APP_PUBLIC_URL/api/social/callback/meta`. Put the App ID and secret in
   `META_APP_ID` / `META_APP_SECRET`. The app needs the permissions
   `pages_show_list, pages_read_engagement, pages_manage_posts,
   business_management, instagram_basic, instagram_content_publish`. While the
   app is in development mode, only people with a role on the app can link;
   for others Meta's App Review is needed.
4. **TikTok:** on developers.tiktok.com create an app with *Login Kit* and the
   *Content Posting API* (scopes `user.info.basic, video.upload`), set the
   redirect URI to `APP_PUBLIC_URL/api/social/callback/tiktok`, and verify the
   shop's domain (TikTok only fetches pictures from verified domains). Put the
   client key and secret in `TIKTOK_CLIENT_KEY` / `TIKTOK_CLIENT_SECRET`.
5. Restart, open **Social media** as the owner and press **Link** on each account.

## To confirm with the real services

The Graph API and TikTok calls follow their documentation and were tested
against a stand-in server, not against Facebook, Instagram or TikTok
themselves. The URLs live in `application.yml` (`app.social.*`) and the calls
in `social/MetaClient.java` and `social/TikTokClient.java`.
