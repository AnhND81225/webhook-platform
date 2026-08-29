---
name: Technical Infrastructure Core
colors:
  surface: '#f8f9fa'
  surface-dim: '#d9dadb'
  surface-bright: '#f8f9fa'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f4f5'
  surface-container: '#edeeef'
  surface-container-high: '#e7e8e9'
  surface-container-highest: '#e1e3e4'
  on-surface: '#191c1d'
  on-surface-variant: '#464555'
  inverse-surface: '#2e3132'
  inverse-on-surface: '#f0f1f2'
  outline: '#777587'
  outline-variant: '#c7c4d8'
  surface-tint: '#4d44e3'
  primary: '#3525cd'
  on-primary: '#ffffff'
  primary-container: '#4f46e5'
  on-primary-container: '#dad7ff'
  inverse-primary: '#c3c0ff'
  secondary: '#575e70'
  on-secondary: '#ffffff'
  secondary-container: '#d9dff5'
  on-secondary-container: '#5c6274'
  tertiary: '#414855'
  on-tertiary: '#ffffff'
  tertiary-container: '#59606e'
  on-tertiary-container: '#d4dbeb'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  success: '#146c2e'
  warning: '#7a5901'
  danger: '#ba1a1a'
  primary-fixed: '#e2dfff'
  primary-fixed-dim: '#c3c0ff'
  on-primary-fixed: '#0f0069'
  on-primary-fixed-variant: '#3323cc'
  secondary-fixed: '#dce2f7'
  secondary-fixed-dim: '#c0c6db'
  on-secondary-fixed: '#141b2b'
  on-secondary-fixed-variant: '#404758'
  tertiary-fixed: '#dce2f3'
  tertiary-fixed-dim: '#c0c7d6'
  on-tertiary-fixed: '#151c27'
  on-tertiary-fixed-variant: '#404754'
  background: '#f8f9fa'
  on-background: '#191c1d'
  surface-variant: '#e1e3e4'
typography:
  headline-lg:
    fontFamily: Geist
    fontSize: 30px
    fontWeight: '600'
    lineHeight: 36px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Geist
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Geist
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 18px
  mono-label:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
  mono-data:
    fontFamily: JetBrains Mono
    fontSize: 13px
    fontWeight: '400'
    lineHeight: 20px
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  sidebar_width: 240px
  header_height: 56px
  container_max_width: 1280px
  gutter: 16px
  margin_page: 32px
  stack_compact: 4px
  stack_default: 12px
  stack_large: 24px
---

If prose conflicts with YAML design tokens, YAML tokens are authoritative.

## Brand & Style

This design system is engineered for high-utility developer tools and infrastructure monitoring. The brand personality is precise, reliable, and strictly professional, prioritizing information density over decorative elements.

The visual style is **Corporate / Modern** with a focus on **Minimalism** and **Technical Precision**. It draws inspiration from industry leaders in the dev-tool space, utilizing a "high-definition" aesthetic characterized by sharp borders, subtle value shifts, and a rigorous adherence to a systematic grid. The UI should evoke a sense of calm under pressure, providing developers with clear, actionable data without visual noise. There are no illustrations, gradients, or blurs; the design relies entirely on superior typography and rhythmic spacing to create hierarchy.

## Colors

The palette is optimized for clarity and long-term readability during debugging sessions. 

- **Foundation:** The main canvas uses a near-white background (`#F8F9FA`) to provide a soft base for pure white (`#FFFFFF`) primary surfaces. This subtle "paper-on-desk" layering creates depth without needing shadows.
- **Typography:** On-surface charcoal (`#191C1D`) provides maximum contrast for primary data, while on-surface variant (`#464555`) is reserved for metadata, labels, and secondary context.
- **Accents:** Primary indigo (`#3525CD`) is the interactive focus color. Primary-container indigo (`#4F46E5`) is available for emphasized filled actions and surfaces.
- **Semantics:** Color is used strictly for status. Success (`#146C2E`) maps to delivered or healthy states, warning (`#7A5901`) maps to retrying or pending states, and danger (`#BA1A1A`) maps to failed or error states.

## Typography

The system utilizes a dual-font strategy to distinguish between UI orchestration and technical data.

- **Geist (Headlines):** Used for structural navigation and page titles to provide a modern, technical edge.
- **Inter (Interface):** The workhorse for all standard UI elements, labels, and descriptions. It is chosen for its exceptional legibility at small sizes.
- **JetBrains Mono (Technical Data):** Mandatory for any data that originated from code or logs (IDs, URLs, JSON, Timestamps). This ensures that characters like `0` and `O` are never confused and that columnar data aligns perfectly.

Maintain a tight line height to support high information density. On mobile, headlines scale down by approximately 20% to avoid excessive wrapping in data-heavy views.

## Layout & Spacing

The layout follows a **Fixed Grid** philosophy for the sidebar and a **Fluid Grid** for the main content area.

- **Sidebar:** A persistent 240px left-hand navigation allows for rapid switching between resources. It uses a slightly darker fill than the main content area to provide grounding.
- **Top Header:** A 56px utility bar containing breadcrumbs and global actions.
- **Content Area:** Content is housed within a 1280px max-width container, centered on the screen. 
- **Density:** We utilize a 4px base unit. For technical tables and lists, use "Compact" spacing (8px internal padding) to maximize the amount of visible data on one screen. Use 16px gutters between cards and the 32px `margin_page` token for the primary page container.

## Elevation & Depth

This design system avoids traditional drop shadows in favor of **Tonal Layers** and **Low-Contrast Outlines**.

- **Level 0 (Background):** `#F8F9FA` – The base canvas.
- **Level 1 (Surface):** `#FFFFFF` – All primary cards, sidebars, and input areas. These are defined by a 1px solid border of `#E1E3E4`.
- **Level 2 (Popovers/Modals):** High-contrast white surfaces with a very subtle, tight shadow (0px 4px 12px rgba(0,0,0,0.05)) to separate them from the Level 1 surface.

Interactive states (hover) are indicated by a slight background shift to `#F3F4F5` rather than a change in elevation. Selection is indicated by a 2px vertical primary-indigo (`#3525CD`) stripe or the `primary-fixed` (`#E2DFFF`) background tint.

## Shapes

The shape language is **Soft (1)**. 

Infrastructure tools require a sense of structure. We use a base radius of 4px (`0.25rem`) for standard components like buttons, inputs, and small badges. Larger containers and cards use a 8px (`0.5rem`) radius. This maintains a sharp, engineered feel while avoiding the harshness of a 0px radius. Interactive elements should never be pill-shaped or fully rounded.

## Components

- **Buttons:** Primary buttons use Indigo backgrounds with white text. Secondary buttons use white backgrounds with a subtle gray border. Heights are compact (32px for small, 40px for default).
- **Status Badges:** Use a "Dot + Label" pattern. A 6px circular indicator of the semantic color next to JetBrains Mono text. Background of the badge should be a 10% opacity version of the semantic color.
- **Data Tables:** Compact rows (40px height). Monospace for technical columns. No vertical borders; use 1px horizontal dividers only. Header labels are all-caps, 11px Inter, with a 500 weight.
- **Metric Cards:** Large Geist-based numbers with a small trend indicator. No iconography; use color-coded labels for status.
- **Chronological Timeline:** A vertical 2px gray line connecting delivery attempts. Each attempt node is a 8px circle. Successes use filled green circles; failures use red outlines.
- **Input Fields:** 1px solid `#E1E3E4` borders that transition to a 1px primary-indigo (`#3525CD`) border on focus. Use a monospace font for any inputs expecting keys or IDs.
