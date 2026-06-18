import type { Config } from "tailwindcss";

/**
 * Tailwind theme tokens for the "Fresh & friendly, soft rounded islands" design
 * language (Phase 7 polish pass).
 *
 * The colors here are SEMANTIC: they reference CSS variables defined in
 * `app/globals.css`. The variables hold DIFFERENT values under light vs dark
 * (via `prefers-color-scheme`), so the SAME Tailwind class (`bg-background`,
 * `text-foreground`, …) renders correctly in both themes without any `dark:`
 * variant at the call site. This is what keeps the refactor shallow: we mostly
 * swap literal colors for semantic tokens.
 *
 * - `background` = the tinted PAGE WASH cards float on.
 * - `card` / `surface` = the bright "island" a card sits on.
 * - `border` = a faint separator (soft shadows do most of the separation work).
 * - `muted` / `muted-foreground` = secondary text & quiet fills.
 * - `primary` / `primary-foreground` = the indigo→violet accent (CTAs, links).
 * - `accent` tints = soft fills for badges/hover (`accent-soft`, `accent-strong`).
 *
 * The extended `borderRadius` and `boxShadow` scales give the rounded-island
 * vocabulary (`rounded-island`, `shadow-island`) one place to live.
 */
const config: Config = {
  content: [
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./lib/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        // Page wash + content defaults.
        background: "rgb(var(--background) / <alpha-value>)",
        foreground: "rgb(var(--foreground) / <alpha-value>)",
        // The elevated "island" surface.
        card: "rgb(var(--card) / <alpha-value>)",
        surface: "rgb(var(--card) / <alpha-value>)",
        "card-foreground": "rgb(var(--card-foreground) / <alpha-value>)",
        // Faint separators + secondary text.
        border: "rgb(var(--border) / <alpha-value>)",
        muted: "rgb(var(--muted) / <alpha-value>)",
        "muted-foreground": "rgb(var(--muted-foreground) / <alpha-value>)",
        // Primary accent (indigo→violet).
        primary: {
          DEFAULT: "rgb(var(--primary) / <alpha-value>)",
          foreground: "rgb(var(--primary-foreground) / <alpha-value>)",
        },
        // Soft accent tints for badges/hover.
        accent: {
          soft: "rgb(var(--accent-soft) / <alpha-value>)",
          strong: "rgb(var(--accent-strong) / <alpha-value>)",
        },
        // Status colors kept as semantic hooks so badges stay consistent.
        success: {
          DEFAULT: "rgb(var(--success) / <alpha-value>)",
          foreground: "rgb(var(--success-foreground) / <alpha-value>)",
        },
        warning: {
          DEFAULT: "rgb(var(--warning) / <alpha-value>)",
          foreground: "rgb(var(--warning-foreground) / <alpha-value>)",
        },
        danger: {
          DEFAULT: "rgb(var(--danger) / <alpha-value>)",
          foreground: "rgb(var(--danger-foreground) / <alpha-value>)",
        },
      },
      borderRadius: {
        // Pill chips & buttons; the design language leans on rounded-full there,
        // but exposing `pill` documents intent at the call site.
        pill: "9999px",
        // Bigger islands than Tailwind's default `2xl`/`3xl`.
        island: "1.25rem",
        xl2: "1.5rem",
        xl3: "1.75rem",
      },
      boxShadow: {
        // Soft, diffuse elevation — the "island floating on a tint" look.
        // Larger blur + low opacity reads softer than Tailwind's default shadow.
        island: "0 1px 2px rgba(16, 24, 40, 0.04), 0 8px 24px rgba(16, 24, 40, 0.06)",
        "island-hover":
          "0 2px 4px rgba(16, 24, 40, 0.06), 0 12px 32px rgba(79, 70, 229, 0.12)",
        "island-lg":
          "0 2px 6px rgba(16, 24, 40, 0.05), 0 18px 48px rgba(16, 24, 40, 0.10)",
      },
      fontFamily: {
        // `var(--font-inter)` is set by next/font in app/layout.tsx. Falling back
        // to the native stack keeps things readable before the font loads.
        sans: ["var(--font-inter)", "system-ui", "sans-serif"],
      },
    },
  },
  plugins: [],
};

export default config;
