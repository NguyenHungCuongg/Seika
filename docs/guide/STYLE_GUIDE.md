# Seika Frontend Style Guide

This document defines the current visual and component conventions used in the Seika web frontend. Use it as the single source of truth so new UI work stays consistent across both the public surface and the authenticated dashboards.

> Scope: covers the **guest / public surface** (homepage, auth, 404) **and the authenticated dashboards** (`/student/dashboard`, `/teacher/dashboard`, `/admin/dashboard`). Both halves share the same palette + typography but use different densities (see § Dashboard Patterns for the workspace rules).

---

## 1) Design Intent

- Brand mood: editorial-luxury learning platform — warm, considered, quietly premium.
- Brand colors: **deep aubergine + desaturated gold + cream**. The previous neon yellow + indigo→purple→violet gradient has been retired.
- Surface strategy:
  - **Public pages**: dark aubergine base with strategic cream sections (Contact) for visual rhythm.
  - **Auth surfaces**: same aubergine base, single centered form card — no decorative side panels.
- Core contrast pattern: warm dark base + restrained gold accent + occasional cream "lift-off" card.
- UI tone: editorial typography (Fraunces serif) + clean grotesk UI (Outfit) + warm hairlines + double-bezel hardware aesthetic.
- Design rule: keep the same brand colors across pages, but vary density, type, and motion by page purpose.

---

## 2) Surface Modes

### Guest / Landing Page (`/home`)

- Purpose: communicate product value with editorial weight.
- Style: aubergine base, Fraunces display serif headlines, bento-grid feature section, floating glass-pill navbar.
- Decorative elements: 3D objects rendered as **transparent PNGs** over the base — never wrapped in card chrome.
- Example sections: Navbar (floating pill), Hero (Editorial Split), Features (Asymmetric Bento), About (typography-driven), Contact (cream section), Footer (3-col).

### Auth Pages (`/auth/login`, `/auth/register`)

- Purpose: get out of the user's way.
- Style: single centered form card on aubergine background, no side panel, no decorative orbs.
- Register card is intentionally **wider than Login** (`maxWidth=720` vs `520`) to accommodate the 3-step flow and inline progress bar.

### 404 (`*`)

- Purpose: recover gracefully from dead ends.
- Style: same form-card double-bezel as auth, Fraunces `12rem` 404, gold accent bar.

### Dashboard Pages (`/student/dashboard`, `/teacher/dashboard`, `/admin/dashboard`)

- Purpose: dense, functional workspace. Not a marketing surface.
- Style: dark aubergine base, **hairline borders only** (no shadows, no `backdrop-blur`, no gradient tiles), Outfit-only typography (no Fraunces), one gold accent per screen.
- See **§ Dashboard Patterns** below for the full ruleset and component list.

---

## 3) Tech and UI Stack

- Framework: React 19 + TypeScript 5.9 + Vite 6.
- Styling: Tailwind CSS v4 (utility-first, `@theme inline` token block).
- Motion: GSAP + ScrollTrigger (via reactbit/AnimatedContent) for scroll-triggered reveals, plus CSS keyframes for entry animations (`animate-fade-up`).
- Icon system: `lucide-react` (default) + `react-icons/lu` for social glyphs.
- Component primitives: shadcn-style set in `src/components/ui` (Radix + CVA + `cn` utility) **plus** a small set of shared primitives authored for this redesign (`Button`, `Input`, `AuthShell`).

---

## 4) Typography

### Font Stack

- **Display (headings)**: `Fraunces Variable` — `font-display`.
- **UI / body**: `Outfit Variable` — `font-sans-ui` (alias of default `font-sans`).
- Loaded locally via `@fontsource-variable/outfit` and `@fontsource-variable/fraunces` (no Google CDN dependency).
- Inter is **no longer** the system default.

### Type Scale Patterns

| Use case           | Size pattern                                        | Weight  | Tracking                |
| ------------------ | --------------------------------------------------- | ------- | ----------------------- |
| Hero display       | `text-5xl sm:text-6xl lg:text-7xl xl:text-[5.5rem]` | medium  | `-0.035em`              |
| Auth page headline | `text-4xl lg:text-5xl`                              | medium  | `-0.025em`              |
| Section title      | `text-4xl md:text-5xl lg:text-6xl`                  | medium  | `-0.025em`              |
| Card title         | `text-2xl lg:text-3xl lg:text-4xl`                  | medium  | `-0.015em`              |
| Body               | `text-base lg:text-lg`                              | regular | default                 |
| Eyebrow            | `text-[10px]` / `text-[11px]`                       | medium  | `0.18–0.22em` uppercase |

### Weight Convention

- Display headings: `font-medium` (Fraunces at 500) — never `font-black`.
- Italic Fraunces accent for emphasis: `italic font-light text-[#d4a843]` ("Master", "Seika.", "on your mind.").
- UI body / labels: regular (Outfit 400) or medium (Outfit 500).

### Numeric Display

- Statistics use `font-tabular` (`font-variant-numeric: tabular-nums`) for aligned numerals.

---

## 5) Color System

All color tokens are defined in `src/index.css` via `@theme inline` **and** as CSS custom properties on `:root`. Use the CSS variables in `style={}` for ad-hoc usage; use the Tailwind arbitrary value notation (`text-[#d4a843]`) for components.

### Surface Tokens

| Token                 | Value     | Use                                                  |
| --------------------- | --------- | ---------------------------------------------------- |
| `--color-bg`          | `#15091e` | Default page background (deep aubergine, near-black) |
| `--color-bg-2`        | `#1c0f2e` | Slightly lighter, used for nested shells             |
| `--color-aubergine`   | `#2a1247` | Mid-tone surface                                     |
| `--color-aubergine-2` | `#3a1c5c` | Mid-tone surface (lighter)                           |
| `--color-aubergine-3` | `#4f2670` | Footer base, slightly lighter than page              |

### Brand Accents

| Token               | Value     | Use                                                                 |
| ------------------- | --------- | ------------------------------------------------------------------- |
| `--color-gold`      | `#d4a843` | Primary accent (desaturated). CTA fill, link, italic display accent |
| `--color-gold-soft` | `#f1e4c0` | Soft gold for muted states                                          |
| `--color-cream`     | `#faf6ee` | Light surface — Contact section, "earn coins" card, brand panel     |
| `--color-cream-2`   | `#f1ead8` | Cream mid-tone                                                      |

### Text & Hairlines

- Default text on dark: `#faf6ee` (cream) at 100% for headings, 65–70% for body, 40–55% for muted.
- Hairlines on dark: `rgba(255, 255, 255, 0.08)` — single warm-tinted family. Use the `.hairline` utility class for full-width dividers.
- Hairlines on cream: `rgba(28, 15, 46, 0.1)` — use `.hairline-warm`.

### Background Atmospheres

- `.bg-glow-aubergine` — radial gradient combining `aubergine-3` and `aubergine` at opposite corners. Apply to `<div className="absolute inset-0 ...">` for hero / auth atmosphere.
- `.bg-glow-cream` — softer radial for cream sections.

### Anti-Patterns (retired)

The following patterns from the previous design are **no longer allowed**:

- `bg-gradient-to-br from-indigo-950 via-purple-900 to-violet-950` — replaced by solid `#15091e` + radial glow.
- Floating blur orbs (`bg-violet-400 rounded-full blur-3xl animate-pulse`) — removed from all surfaces.
- `bg-clip-text text-transparent` gold gradient on every heading — replaced by solid italic Fraunces in `#d4a843`.
- SVG wave dividers between sections — replaced by `.hairline` utility or generous whitespace.

---

## 6) Component Primitives

All new components live under `src/components/ui`. Prefer them over hand-rolled markup. The dashboards introduced a second wave of primitives on top of the public-surface set — see § 19 for the full dashboard primitive list.

### `<Button>` (`src/components/ui/Button.tsx`)

- Variants: `primary` (default), `ghost`, `dark`, `link`.
- Sizes: `md` (h-11 px-5 text-sm), `lg` (h-14 px-7 text-base).
- `trailing` prop renders the **Button-in-Button** pattern: a nested circular icon container (`w-7 h-7 rounded-full bg-[#1c0f2e]/15`) holding an `ArrowUpRight` icon. On hover the inner circle translates `2px x, -1px y`.
- All variants use `magnetic-press` utility: `active:scale-[0.98]` + spring easing.
- Primary fill: `bg-gradient-to-b from-[#e6c264] to-[#c89a36]` with inset highlight `shadow-[inset_0_1px_1px_rgba(255,255,255,0.4),...]`.

### `<TextInput>` (`src/components/ui/Input.tsx`)

- **Double-bezel**: outer shell (`bg-white/[0.06]` hairline border) + inner core (`bg-[#1c0f2e]`, `shadow-[inset_0_1px_1px_rgba(255,255,255,0.04)]`).
- Focus: outer shell transitions to a `from-[#d4a843] to-[#a37f2a]` gradient.
- Error: outer shell becomes red gradient + inner helper text in `#fca5a5`.
- Props: `label`, `hint`, `error`, `leadingIcon`, `trailing`, `onClearError`.
- Labels are 11px uppercase tracked (`text-[#d4a843]/80`).

### `<AuthShell>` (`src/components/auth/AuthShell.tsx`)

- Wrapper for login / register. Renders: background atmosphere + fixed "Back home" link + centered form card.
- Form card itself is **double-bezel**: outer `p-1 bg-gradient-to-b from-white/[0.08] to-white/[0.02]`, inner `bg-[#15091e]/85 backdrop-blur-2xl` with `shadow-[inset_0_1px_1px_rgba(255,255,255,0.05),0_32px_80px_-24px_rgba(0,0,0,0.6)]`.
- Prop `maxWidth` defaults to `520`, Register uses `720` for its wider 3-step layout.

### Shared Icon Chip pattern

- 48px rounded-2xl with `rotate-[-4deg]` and `group-hover:rotate-0` transition (500ms spring).
- Tone variants: `gold` (gradient `#e6c264 → #c89a36`), `purple` (`#3a1c5c`), `blue` (`#1e3a5f`), `green` (`#1d3a2e`).

---

## 7) Layout Primitives

### Eyebrow tag

`.eyebrow` utility — 10–11px uppercase tracked text in `text-[#d4a843]/90`, with a 1px border `rgba(212, 168, 67, 0.25)`, pill shape, and a leading dot.

### Hairline divider

`.hairline` (on dark) and `.hairline-warm` (on cream) — fade-in / fade-out horizontal lines.

### Double-Bezel container

```
<outer className="p-1.5 rounded-[2rem] bg-white/[0.04] border border-white/[0.06]">
  <inner className="rounded-[calc(2rem-0.375rem)] bg-gradient-to-br from-[#2a1247] to-[#15091e] shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)]">
    {content}
  </inner>
</outer>
```

Use this for any elevated card that needs to look like physical hardware. Inner radius = outer radius − padding.

### Grain overlay

`<div className="grain-overlay" />` — applied once at the App root (`src/App.tsx`). Fixed positioning, `pointer-events-none`, SVG noise texture at `opacity: 0.04`, `mix-blend-mode: overlay`. Breaks digital flatness without affecting readability.

---

## 8) Spacing and Layout

- Main content container width: `max-w-[1200px]` on the public homepage.
- Horizontal padding: `px-6 lg:px-10`.
- Auth card width: `max-w-[520px]` (Login) / `max-w-[720px]` (Register) via `AuthShell`.
- Section spacing: `py-32 lg:py-40` for major sections — generous whitespace.
- Grid patterns:
  - Bento (Features): `grid grid-cols-12 gap-4 lg:gap-5 auto-rows-[minmax(180px,auto)]`.
  - Two-column hero: `lg:grid-cols-[1.15fr_0.85fr] gap-16 lg:gap-20`.
  - Team grid: `grid md:grid-cols-2 gap-16 md:gap-20 max-w-4xl mx-auto`.
- Use `100dvh` instead of `100vh` for full-screen sections (iOS Safari viewport fix).

---

## 9) Shape Language

- Generous radii: `rounded-full` for buttons and pills, `rounded-2xl` for input shells and icon chips, `rounded-[2rem]` for elevated cards, `rounded-[calc(2rem-0.375rem)]` for nested inner cores.
- Subtle rotation only — `rotate-[-4deg]` for icon chips, `rotate-[5deg]` for one-time decorative tilts. Avoid `rotate-6 / rotate-12` everywhere (previous design's anti-pattern).
- No `border` + `shadow` generic card pattern. Use double-bezel or nothing.

---

## 10) Motion and Interaction

### Easing

- `--ease-spring`: `cubic-bezier(0.32, 0.72, 0, 1)` — primary motion easing.
- `--ease-soft`: `cubic-bezier(0.22, 1, 0.36, 1)` — for color / opacity transitions.
- Available as utilities: `.ease-spring`, `.ease-soft`, `.magnetic-press`.

### Custom keyframes (`src/index.css`)

- `float-slow` / `float-slow-reverse` — used by 3D objects in the Hero.
- `fade-up` — opacity + translateY + blur, 900ms.
- `fade-in` — opacity only, 700ms.
- Utility classes: `.animate-fade-up`, `.animate-fade-in`, plus `.delay-100/150/200/300/400/500` for staggered entry.

### Standard hover/press pattern

- Buttons: `magnetic-press` (active scale 0.98, 320ms spring) + color shift.
- Cards: subtle `hover:-translate-y-1` (no scale-105).
- Links: `transition-colors duration-300 ease-soft`.
- Form focus: outer shell gradient border + inner core stays solid.

### Reduced motion

`@media (prefers-reduced-motion: reduce)` is honored globally — all animations collapse to 0.01ms.

### Animations to retire

- `animate-pulse` on decorative blur orbs — removed.
- `animate-bounce` on logos / cards — replaced with static tilt or fade-up.
- `hover:scale-105` everywhere — replaced with `active:scale-[0.98]` or single-card `hover:-translate-y-1`.

---

## 11) Buttons, Pills, and Chips

### Primary CTA

Use `<Button variant="primary" trailing>`. Spring active scale, gold gradient, dark ink text, inset highlight + soft outer glow on hover.

### Secondary CTA

Use `<Button variant="ghost">`. Translucent dark glass (`bg-white/[0.04]`), hairline border, hover fills with `bg-white/[0.08]`.

### Link / tertiary

Use `<Button variant="link">` for inline text links that still need a trailing arrow.

### Chip / badge

`.eyebrow` utility, or hand-rolled `inline-flex items-center px-3 py-1 rounded-full text-xs font-medium bg-white/[0.06] border border-white/[0.08]`. On dashboards prefer `<StatusPill>` (always renders text — no color-only meaning) with the appropriate `variant`.

---

## 12) Card Patterns

### Dark feature card

- Outer shell: `bg-white/[0.04] border border-white/[0.06]`.
- Inner core: `bg-gradient-to-br from-[#1c0f2e] to-[#15091e]` with `shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)]`.
- Padding: `p-8 lg:p-10`.
- Hover: subtle `hover:-translate-y-1` (no shadow change).

### Gold-bordered feature card

- Outer: `bg-gradient-to-b from-[#d4a843]/30 to-[#d4a843]/[0.04] border border-[#d4a843]/[0.18]`.
- Inner: `bg-gradient-to-br from-[#1c0f2e] via-[#15091e] to-[#15091e] shadow-[inset_0_1px_1px_rgba(212,168,67,0.18)]`.

### Cream contrast card

- Inner: `bg-[#faf6ee] text-[#1c0f2e] shadow-[inset_0_1px_1px_rgba(255,255,255,0.6)]`.
- Used for "Earn coins" bento cell and About mission block.

### Buttons in cards

Pin the CTA to the bottom of the card (`mt-auto`) when card content varies in length.

---

## 13) Section Pattern Library

### Navbar (`src/components/home/Navbar.tsx`)

- **Floating glass pill**, not edge-to-edge. `mt-5 mx-auto max-w-[1100px] h-14 rounded-full`.
- Background: `bg-[#15091e]/70 backdrop-blur-2xl` + `border border-white/[0.08]` + inner highlight `shadow-[inset_0_1px_1px_rgba(255,255,255,0.08),0_8px_32px_-12px_rgba(0,0,0,0.5)]`.
- Hamburger morphs to X via `rotate-45` / `-rotate-45` on two stacked hairline spans (no icon toggle).
- Mobile menu: full-screen overlay `bg-[#15091e]/92 backdrop-blur-3xl`, links fade-up staggered with `delay-[120ms + i*80ms]`.

### Hero (`src/components/home/Hero.tsx`)

- **Editorial Split**: `grid lg:grid-cols-[1.15fr_0.85fr] gap-16 lg:gap-20 items-center`.
- Background: solid `--color-bg` + `.bg-glow-aubergine` overlay.
- Headline: Fraunces `text-5xl sm:text-6xl lg:text-7xl xl:text-[5.5rem]` with `italic font-light text-[#d4a843]` for one accent word.
- Stats: 3-col grid with `font-tabular` numerals (`2,847` / `134` / `47.2%`), `text-[11px] uppercase tracking-[0.16em]` labels.
- 3D objects: **transparent PNGs, no card chrome.** `GameJoystick` (back) + `YellowBlueSchoolBag` (front), float-animated, slight offset + tilt.
- Floating "+50 XP" chip: glass-pill style, top-left, `rotate-[-6deg]`.

### Features (`src/components/home/Features.tsx`)

- **Asymmetrical Bento** on `grid-cols-12` with `auto-rows-[minmax(180px,auto)]` and `gap-4 lg:gap-5`.
- 8 cells, varied col-spans (4/5/7/8/12) and row-spans, three tones (dark / gold / cream) for rhythm.
- Top hairline separator replaces previous SVG wave divider.
- Social proof band sits above the bento grid.

### About (`src/components/home/About.tsx`)

- Section header: Fraunces headline + subhead.
- Team grid: 2-col, max-width 4xl, member cards with social buttons.
- Mission block: cream double-bezel card with `border-l-2 border-[#d4a843]` accent (handled via gold outer shell).

### Contact (`src/components/home/Contact.tsx`)

- **Cream section background** — intentional dark/light contrast break.
- Form: double-bezel inputs (`bg-white border border-[#1c0f2e]/10`), focus ring `ring-2 ring-[#d4a843]/25`.
- Right column: pull-quote + contact info rows + "Write us directly" link with trailing arrow.

### Footer (`src/components/home/Footer.tsx`)

- Background: `#0e0617` (one shade darker than page) + hairline top border.
- 3-col grid: `md:grid-cols-[1.5fr_1fr_1fr]` (Brand + Contact + Legal).
- Column headings: `text-[10px] uppercase tracking-[0.25em] text-[#d4a843]/60` eyebrow style.
- Bottom strip: `font-tabular` build version tag.

### Auth pages (`src/pages/auth/Login.tsx`, `src/pages/auth/Register.tsx`)

- Shared `AuthShell` wrapper.
- Login: `maxWidth=520`, single form, "Welcome back" headline.
- Register: `maxWidth=720`, 3-step flow with hairline progress bar + gold gradient fill.
- Role cards in `RoleStep`: 1px gradient border (`from-[#d4a843] to-[#a37f2a]` when selected), `p-px` outer / inner shell trick.
- Gender toggle and role cards share the same selected-state pattern.

### 404 (`src/pages/not-found/NotFound.tsx`)

- Fraunces `text-[10rem] sm:text-[12rem]` for the "404" number, `font-variant-numeric: tabular-nums`.
- Gold accent bar (`h-1 w-20 rounded-full bg-gradient-to-r from-[#e6c264] to-[#c89a36]`) at top of card.
- Double-bezel shell matches auth form cards.

---

## 14) 3D Object Components (`src/components/3d-objects/`)

- `GameJoystick` (intrinsic 544×483 PNG) and `YellowBlueSchoolBag` (intrinsic 3000×3000 PNG).
- Both accept a `width` prop (in px). `style={{ width: "${width}px", height: "auto", maxWidth: "none", display: "block" }}` is mandatory — Tailwind v4 preflight sets `img { max-width: 100% }` which silently clamps the requested width otherwise.
- Render directly on the page background — do **not** wrap them in cards. The previous double-bezel card wrappers have been removed; transparency is intentional.

---

## 15) Accessibility and UX Baselines

- Visible focus ring: 2px gold outline + 3px offset, applied globally via `:focus-visible`.
- All transitions ≤ 320ms; respect `prefers-reduced-motion`.
- Form labels are real `<label htmlFor>` with eyebrow style (11px uppercase tracked gold).
- Error states use inline text under the field, not `window.alert()`.
- Mobile menu uses full-screen overlay with backdrop blur for clear context-switch.
- Touch targets ≥ 44px on interactive elements (buttons `h-11`/`h-14`, icon chips `w-12 h-12`).
- Text contrast on dark: cream at 100% (≥ 13:1) and 65% (≥ 7:1) both pass WCAG AA for body text.

---

## 16) Collaboration Rules (Do / Do Not)

### Do

- Use Fraunces for display, Outfit for UI body. Use `font-display`, `font-sans-ui`, or rely on the global default.
- Reach for `Button` / `TextInput` / `AuthShell` from `src/components/ui` and `src/components/auth` before writing new primitives.
- Use the `@theme inline` tokens or hardcoded hex values via Tailwind arbitrary notation (`text-[#d4a843]`) consistently — do not introduce a parallel color system.
- Apply double-bezel treatment to any elevated card, never flat `bg-white rounded-2xl`.
- Pin CTAs to the bottom of cards when content varies in length.
- Use sentence case for all headings and CTAs. Italic Fraunces for emphasis.
- Use real-feel numbers in stats (e.g. `2,847`, `47.2%`) — avoid fake round figures (`10K+`, `98%`).
- Add a `:focus-visible` ring on any new interactive element.
- Use `100dvh` for full-height sections.

### Do Not

- Use Inter, Roboto, or any other default font — Fraunces + Outfit only.
- Use the retired `indigo-950 / purple-900 / violet-950` gradient or any floating blur orbs.
- Add `bg-clip-text text-transparent` gradient text on headings — use solid Fraunces + italic gold accent instead.
- Apply `hover:scale-105` / `animate-bounce` / `animate-pulse` to UI chrome — these are retired.
- Wrap 3D objects in card chrome — they render directly on background.
- Mix multiple button visual systems in the same screen — use `<Button>` variants only.
- Use generic `box-shadow` / `shadow-md` on cards — use double-bezel with inset highlights.
- Use `width` prop without `style={{ maxWidth: "none" }}` on `<img>` (Tailwind preflight will clamp it).

**Dashboard-specific (in addition to the above)**:

- Use Fraunces (`font-display`) in dashboard chrome — apply `font-sans-ui` explicitly on layout-level `h1`/`h2`.
- Use `backdrop-blur-xl` / `shadow-[0_2Npx_*]` / Tailwind palette pills (`bg-emerald-500/20 text-emerald-300` etc.) on dashboards — use `<SectionCard>`, hairlines, and `<StatusPill>`.
- Use emoji-only product hero at `text-5xl` — keep emoji but frame it in `aspect-[4/3] bg-white/[0.03] border border-white/[0.06]` at `text-3xl`.
- Use `animate-bounce` / `animate-pulse` for celebration / hint states — flat `<SectionCard>` + `<IconChip>` + `<StatusPill>` is sufficient.
- Mix more than one gold accent per dashboard screen — see the Gold Accent Rule in § Dashboard Patterns.

---

## 17) Quick Copy Recipes

### Main page background

```jsx
<section className="relative min-h-[100dvh] overflow-hidden">
  <div className="absolute inset-0 bg-glow-aubergine pointer-events-none" />
  {/* content */}
</section>
```

### Eyebrow tag

```jsx
<span className="eyebrow">
  <span className="inline-block w-1 h-1 rounded-full bg-[#d4a843]" />
  Section label
</span>
```

### Fraunces display headline with italic accent

```jsx
<h1 className="font-display font-medium text-[#faf6ee] text-4xl lg:text-5xl leading-[1.02] tracking-[-0.025em]">
  Headline <span className="italic font-light text-[#d4a843]">emphasis</span>
</h1>
```

### Primary CTA

```jsx
<Button variant="primary" size="lg" trailing>
  Get started
</Button>
```

### Form card (auth)

```jsx
<AuthShell>
  <div className="p-8 sm:p-10 lg:p-12 space-y-10">
    <header className="space-y-3">
      <span className="eyebrow">...</span>
      <h1 className="font-display font-medium text-[#faf6ee] text-4xl lg:text-5xl">
        Welcome back
      </h1>
    </header>
    <form className="space-y-5">...</form>
  </div>
</AuthShell>
```

### Form field

```jsx
<TextInput
  label="Username"
  placeholder="your.username"
  leadingIcon={<User className="w-4 h-4" strokeWidth={1.5} />}
  error={errors.username}
  onClearError={() => clearError("username")}
/>
```

### Double-bezel card

```jsx
<div className="p-1.5 rounded-[2rem] bg-white/[0.04] border border-white/[0.06]">
  <div className="rounded-[calc(2rem-0.375rem)] bg-gradient-to-br from-[#2a1247] to-[#15091e] shadow-[inset_0_1px_1px_rgba(255,255,255,0.05)]">
    {content}
  </div>
</div>
```

### Cream section (Contact / About mission)

```jsx
<section className="bg-[#faf6ee] text-[#1c0f2e]">{/* ... */}</section>
```

### Transparent 3D object

```jsx
<YellowBlueSchoolBag width={420} />
<GameJoystick width={320} />
```

---

## 18) Changelog

- **Public surface redesign** — replaced Inter + indigo→purple→violet gradient + floating blur orbs with Fraunces/Outfit typography + deep aubergine + desaturated gold + cream contrast. New shared primitives (`Button`, `TextInput`, `AuthShell`). Navbar now a floating glass pill. Features converted to asymmetrical bento. Auth pages use a single centered card with double-bezel hardware aesthetic.

- **Dashboard redesign (Phase A–D)** — replaced the pre-redesign chrome (`backdrop-blur-xl`, `shadow-[0_2Npx_*]`, gradient tiles, `bg-clip-text` gold headings, emoji-only product hero at `text-5xl`, `animate-bounce` trophies, `font-black`) on Student/Teacher/Admin dashboards with a restrained hairline-bordered system. Added six new shared primitives in `src/components/ui/`: `PageHeader`, `SectionCard`, `StatCard`, `IconChip`, `StatusPill`, `EmptyState`. Extended `<Button>` with a `tone?: "neutral" | "danger"` prop for destructive ghost actions. Rewrote `<ConfirmModal>` visuals (focus trap preserved) and `<Pagination>` (hairline + gold-pill active state). Local `RoleBadge` / `UserStatusPill` / `UsersCardSection` / `StudentActionButton` / dashboard-local `StatCard` / `QuickStatItem` deleted; callers migrated to `<StatusPill>` + `<SectionCard>` + shared `<StatCard>`.

---

## 19) Dashboard Patterns

The authenticated dashboards (`/student/dashboard`, `/teacher/dashboard`, `/admin/dashboard`) share the public-surface palette and typography, but apply them under a stricter workspace discipline. Tone reference: Linear / Notion / Vercel dashboard — one gold accent per screen, the rest is hairline-bordered `bg-white/[0.03]` surfaces.

### Architectural principles

1. **One accent per screen.** Only one element per page gets gold: the active nav row in the sidebar, the primary CTA, or the one KPI the page is about. Everything else is `text-cream` + `text-white/45`–`text-white/65` + hairline borders.
2. **No gradients in dashboards.** `bg-gradient-*`, `bg-clip-text`, `from-*/to-*` belong only on the public surface. Forbidden inside `src/web-app/src/pages/{admin,student,teacher}` and `src/web-app/src/layouts`.
3. **Hairlines > shadows.** Replace `shadow-2xl`, `shadow-[0_2Npx_*]` with `border border-white/[0.06]`. Shadows are reserved for the modal scrim and modal panel only.
4. **Tabular figures for all numbers.** Apply `tabular-nums` on any rendered number.
5. **Outfit only in dashboards.** Apply explicit `font-sans-ui` on every layout-level `h1`/`h2` so the global `font-display` rule does not leak into the workspace.

### New shared primitives (`src/components/ui/`)

| Component       | Purpose                                      | Notes                                                                                                                                                                                                            |
| --------------- | -------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `<PageHeader>`  | Title row at the top of every dashboard page | Token: `font-sans-ui text-2xl font-semibold tracking-tight text-cream`. Props: `title`, `subtitle`, `actions`, `breadcrumbs`.                                                                                    |
| `<SectionCard>` | Minimalist container for dashboard sections  | `bg-white/[0.025] border border-white/[0.06] rounded-2xl p-6`. Optional `header` and `footer`. No backdrop-blur, no shadow.                                                                                      |
| `<StatCard>`    | Single KPI card                              | Label: `text-xs uppercase tracking-[0.12em] text-white/45`. Value: `text-3xl font-semibold text-cream tabular-nums`. Optional `icon` + `iconVariant`, `delta`, `unit`, `hint`. Replaces 4-up gradient KPI tiles. |
| `<IconChip>`    | 9×9 hairline square for icons                | Variants: `gold \| muted \| info \| success \| danger \| warning`. Decorative by default (`aria-hidden`); consumer passes `ariaLabel` + `decorative={false}` when meaning is conveyed.                           |
| `<StatusPill>`  | Text-carrying status / role / type pill      | Variants: `success \| danger \| warning \| info \| neutral \| gold`. Always renders text (no color-only meaning). Replaces inline Tailwind palette pills.                                                        |
| `<EmptyState>`  | Composed empty-state block                   | IconChip + title + description + optional action. Use wherever a section has nothing to show.                                                                                                                    |

### Extended / rewritten primitives

- `<Button>` (`src/components/ui/Button.tsx`) — extended with `tone?: "neutral" | "danger"`. When `tone === "danger"` and `variant === "ghost"`, the button renders `text-red-300 hover:bg-red-500/10`. Other variants ignore `tone` to keep the API surface small.
- `<ConfirmModal>` (`src/components/ui/ConfirmModal.tsx`) — visuals rewritten to match the dashboard palette: `bg-[#1c0f2e] border border-white/[0.08] rounded-2xl p-6`, scrim `bg-black/60`, opacity-only fade-in via custom `confirm-fadeIn` keyframe (200 ms). Focus trap + Escape handler preserved verbatim. Confirm button uses `<Button variant="ghost" tone={variant === "danger" ? "danger" : "neutral"}>`.
- `<Pagination>` (`src/components/ui/Pagination.tsx`) — hairline borders, `tabular-nums`, gold-pill active state (`bg-[#d4a843]/10 border-[#d4a843]/30 text-[#d4a843]`). Active page uses `aria-current="page"`.

### Layout chrome (Student/Teacher/Admin DashboardLayout)

| Surface             | Replace                                                           | With                                                                                                 |
| ------------------- | ----------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| Sidebar wrapper     | `backdrop-blur-xl bg-[rgba(20,15,38,0.88)]`                       | `bg-[#1c0f2e] border-r border-white/[0.06]`                                                          |
| Sidebar width       | `w-64`                                                            | `w-60`                                                                                               |
| Header wrapper      | `backdrop-blur-xl bg-[rgba(24,18,45,0.9)] shadow-[0_24px_80px_*]` | `bg-[#15091e]/80 backdrop-blur-md border-b border-white/[0.06]`                                      |
| Nav row (inactive)  | `text-[var(--text-muted)] hover:bg-[var(--muted)]/50`             | `text-white/60 hover:text-cream hover:bg-white/[0.03]`                                               |
| Nav row (active)    | `bg-[var(--primary)] text-[var(--primary-foreground)]`            | `bg-white/[0.05] text-cream border-l-2 border-[#d4a843]` (left-rail accent)                          |
| Role chip (Teacher) | `bg-amber-500/20 text-amber-300`                                  | `<StatusPill variant="gold">Teacher</StatusPill>`                                                    |
| Role chip (Admin)   | `bg-red-500/20 text-red-300`                                      | `<StatusPill variant="neutral">Admin</StatusPill>` (red is reserved for destructive actions at rest) |
| Avatar fallback     | `bg-[var(--primary)]/20`                                          | `bg-white/[0.06] text-cream`                                                                         |

### Forbidden patterns (dashboard grep audit)

These queries must return zero hits inside `src/web-app/src/pages/{student,teacher,admin}/**` and `src/web-app/src/layouts/**`:

- `bg-gradient-to-`
- `bg-clip-text`
- `from-amber-`, `to-amber-`, `from-yellow-`, `to-yellow-`
- `from-purple-`, `to-purple-`, `from-violet-`, `to-violet-`
- `from-emerald-`, `to-emerald-`, `from-rose-`, `to-rose-`
- `from-blue-`, `to-blue-`, `from-cyan-`, `to-cyan-`
- `from-pink-`, `to-pink-`, `from-fuchsia-`, `to-fuchsia-`
- `backdrop-blur-xl`
- `shadow-[0_2[04]px_`
- `animate-bounce`, `animate-pulse` inside dashboard chrome
- `animate-in fade-in zoom-in`
- `font-display` (Fraunces leaks — see § architectural principle 5)

### Gold Accent Rule

The one element per screen that gets gold must be the element that communicates the page's primary action or state:

- **Sidebar nav**: the active row gets `border-l-2 border-[#d4a843]`.
- **Revenue / wallet**: the hero balance or the most-important KPI gets `<IconChip variant="gold">` or a gold left-rail bar (`absolute left-0 top-0 bottom-0 w-px bg-[#d4a843]/60`).
- **Forms / modals**: the primary CTA is `<Button variant="primary">` (gold gradient); destructive actions are `<Button variant="ghost" tone="danger">` (red ghost).
- **Charts**: one series per chart uses gold `#d4a843` stroke; other series use muted white `rgba(255,255,255,0.45)`.

### Accessibility baselines (dashboard-specific)

- `<IconChip>` defaults to `aria-hidden="true"`; pass `ariaLabel` + `decorative={false}` when meaning is conveyed (e.g. a status badge that is the only indication of state).
- `<StatusPill>` always renders text content (no color-only information).
- `<ConfirmModal>` retains the existing focus trap and Escape-to-close handler — visuals-only changes were made in Phase A.
- `<Pagination>` uses `aria-current="page"` on the active page button.
- `<PageHeader>` breadcrumbs (when present) are wrapped in `<nav aria-label="Breadcrumb">`.

### Quick copy recipes (dashboard)

```jsx
// Page header with reload action
<PageHeader
  title="Quản lý người dùng"
  subtitle={`Tổng ${users.totalElements} user`}
  actions={
    <Button variant="ghost" size="md" onClick={refetch}>Tải lại</Button>
  }
/>

// KPI grid (single gold accent)
<div className="grid gap-4 md:grid-cols-2 lg:grid-cols-4">
  <StatCard label="Total XP" value={exp} icon={<Zap className="h-4 w-4" />} iconVariant="gold" delta={{ value: "+12%", trend: "up" }} />
  <StatCard label="Quizzes" value={quizzes} icon={<Target className="h-4 w-4" />} iconVariant="info" />
  <StatCard label="Spent" value={spent} icon={<Coins className="h-4 w-4" />} iconVariant="muted" />
  <StatCard label="Locked" value={locked} icon={<ShieldAlert className="h-4 w-4" />} iconVariant="danger" />
</div>

// Hairline-bordered container
<SectionCard className="overflow-hidden p-0">
  <div className="overflow-x-auto p-6">
    <table>...</table>
  </div>
  <Pagination ... />
</SectionCard>

// Empty state
<EmptyState
  icon={<Inbox className="w-5 h-5" aria-hidden="true" />}
  title="Chưa có sản phẩm nào"
  description="Marketplace hiện chưa có sản phẩm. Quay lại sau nhé."
/>

// Filter chip group (gold when active)
<div role="group" aria-label="Lọc theo role" className="flex items-center gap-1 rounded-full border border-white/[0.08] bg-white/[0.02] p-1">
  {OPTIONS.map(opt => (
    <button
      aria-pressed={active}
      className={active
        ? "rounded-full bg-[#d4a843]/10 border border-[#d4a843]/30 px-3 py-1 text-xs font-medium text-[#d4a843]"
        : "rounded-full border border-transparent px-3 py-1 text-xs font-medium text-white/60 hover:bg-white/[0.04] hover:text-cream"}
    >
      {opt.label}
    </button>
  ))}
</div>

// Input (hairline + gold/50 focus)
<input className="w-full rounded-lg border border-white/[0.08] bg-white/[0.04] px-3 py-2 font-sans-ui text-sm text-cream focus:border-[#d4a843]/50 focus:outline-none transition-colors" />
```
