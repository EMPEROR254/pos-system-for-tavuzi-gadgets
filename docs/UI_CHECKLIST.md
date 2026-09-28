# UI and speed checklist

Every screen must look polished and modern - never a plain white page - and
feel instant. Check these before finishing a milestone.

## Style

- [ ] Tailwind CSS for all styling (NativeWind on React Native). shadcn/ui
      components only where a ready-made component clearly helps (dialogs,
      dropdowns, date pickers, data tables). No other CSS frameworks, no CSS-in-JS.
- [ ] One accent colour (the client's brand), neutral greys, generous spacing,
      rounded corners, soft shadows; a clear visual hierarchy on every screen.
- [ ] Colours come from theme tokens (CSS variables), never hard-coded per screen.
- [ ] Money is right-aligned with `tabular-nums` and shown as `KES 1,250`.
- [ ] Every list has a loading skeleton, an empty state and an error state.
- [ ] Forms validate inline, show what is wrong next to the field, and confirm
      success with a toast.

## Themes

- [ ] Light, dark and system themes, with a visible toggle (header or settings).
- [ ] The choice is saved; "system" follows the phone/computer setting live.
- [ ] No flash of the wrong theme on load (a tiny inline script sets it before CSS).
- [ ] Text contrast passes WCAG AA in both themes.

## Motion

- [ ] Subtle animations: page and dialog fade/slide-in, toast slide, hover and
      press feedback - 150-250 ms, ease-out.
- [ ] CSS transitions / Tailwind `transition` and `animate-` utilities first; a
      motion library only for complex cases.
- [ ] Everything respects "reduce motion" (`motion-reduce:` variants).
- [ ] Never animate big tables or anything that slows down daily work (e.g. a cashier's sale screen).

## Speed

- [ ] First screen JavaScript under 200 KB gzipped; every other route is
      code-split (`React.lazy` / Next.js dynamic imports).
- [ ] No render-blocking third-party scripts. Fonts are self-hosted (no Google
      Fonts request at run time) with `font-display: swap`.
- [ ] Images: WebP/AVIF, width and height set, `loading="lazy"` below the fold.
- [ ] Long lists (100+ rows) are paginated by the API or virtualised.
- [ ] Search boxes are debounced; buttons show progress and can't be double-submitted.
- [ ] Static files have hashed names and long cache headers; `index.html` is not cached.
- [ ] The API compresses responses (Spring Boot: `server.compression.enabled: true`),
      paginates lists, avoids N+1 queries and has indexes on filtered columns.
- [ ] Measured: Lighthouse Performance 90+ on mobile, and the main screen is
      usable within 2 seconds on a mid-range Android over 4G.

## Admin area

- [ ] Dashboard with key-number cards and Recharts charts (`ResponsiveContainer`,
      theme colours in light and dark); admin routes lazy-loaded.
- [ ] Management screens: search, filters, sorting, server-side pagination.
- [ ] Reports: date range with quick ranges, totals, CSV export.

## Works everywhere (mobile friendly)

- [ ] Checked at 360 px (phone), 768 px (tablet) and 1366 px (laptop) wide.
- [ ] Sidebar becomes a drawer or bottom navigation on phones; tables become
      stacked cards; dialogs become full-screen sheets; forms are one column.
- [ ] Touch targets at least 44 px; no sideways scrolling on any screen.
- [ ] Keyboard navigation with visible focus rings; labels on every input;
      `aria-label` on icon-only buttons.
- [ ] Clear messages when the internet is slow or down, with a retry.
