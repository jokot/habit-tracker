# habitto.jokot.dev

The website of Habitto: the landing page, the privacy policy and the account deletion page.
Play needs the last 2 pages for the store listing.

The site is static HTML. It needs no build step.

| Path | File |
|---|---|
| `/` | `index.html` |
| `/privacy-policy` | `privacy-policy/index.html` |
| `/delete-account` | `delete-account/index.html` |

## Deploy on Cloudflare Pages

1. In the Cloudflare dashboard, open **Workers & Pages → Create → Pages → Connect to Git**.
2. Select the repository `jokot/habit-tracker`, and the branch `main`.
3. Use these build settings:
   - Framework preset: **None**
   - Build command: empty
   - Build output directory: `web`
4. Click **Save and Deploy**. Cloudflare deploys again at each push to `main`.
5. Open the project → **Custom domains → Set up a custom domain**, and enter
   `habitto.jokot.dev`. If the DNS of `jokot.dev` is on Cloudflare, Cloudflare adds the
   record. If not, add a `CNAME` record from `habitto` to `<project>.pages.dev` at your
   DNS provider.

A push that changes only the app also starts a deploy. To skip these deploys, open
**Settings → Build → Build watch paths**, and include only `web/*`.

## Preview on your computer

```sh
python3 -m http.server 8000 --directory web
```

Then open <http://localhost:8000>.
