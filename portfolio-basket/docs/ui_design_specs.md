# UI Design Specs: Smart Basket Preview

**Theme**: Premium FinTech, Dark Mode, Glassmorphism.
**Core Effect**: `BackdropFilter` (Blur), Semi-transparent layers, Neon gradients.

---

## 1. Visual Description

### **Overall Layout**
*   **Background**: Deep abstract gradient (Midnight Blue to Black).
*   **Surface**: Card-like elements with white/glass opacity (10-15%) and subtle white borders (1px, 20% opacity).

### **Component: Hero Gauge (Top)**
*   **Central Element**: A large Radial Gauge showing the "Match Percentage" (e.g., 85%).
*   **Color**: Gradient from Teal to Emerald Green.
*   **Glow**: The gauge should have a soft outer glow.
*   **Text**: "Nifty Alpha 50" in large, bold, sans-serif font (Inter/Roboto).

### **Component: Gap Analysis List (Bottom)**
*   **List Style**: Vertical list of stock items.
*   **Item Row**:
    *   **Left**: Stock Logo/Icon + Ticker (e.g., "TCS").
    *   **Right**: Status Badge.
    *   **Statuses**:
        *   ✅ **Match**: Green Text/Icon.
        *   🔄 **Substitute**: Blue "Swap" Icon (e.g., "Replacing Infosys").
        *   ⚠️ **Missing**: Orange Warning Icon.

---

## 2. Generative AI Prompts

Use these prompts in tools like Midjourney, DALL-E, or Code Generation AIs.

### **A. Text-to-Image Prompt (Visual Inspiration)**
> **Prompt:**
> "Mobile app UI design for a fintech investment app, dark mode, glassmorphism style. Main screen showing an 'ETF Replication' dashboard. Top section: A large growing radial progress bar glowing emerald green showing '85% Match'. Background is a deep blurred mostly black abstract gradient. Bottom section: A list of stock items on semi-transparent frosted glass cards. Some items have green checkmarks, some have orange warning icons. Sleek, modern, high fidelity, dribbble style, 4k."

### **B. Text-to-Code Prompt (for Web/Tailwind)**
> **Prompt:**
> "Create a React component using Tailwind CSS for a 'Basket Preview Card'. The design should use a dark theme with glassmorphism.
> 1.  **Container**: Dark gradient background (`bg-slate-900`).
> 2.  **Card**: A centered dive with `backdrop-blur-md`, `bg-white/10`, and a thin white border `border-white/20`. Rounded corners (`rounded-2xl`).
> 3.  **Content**:
>     *   Top: A circular progress indicator (SVG) showing 85% completion in green. Text 'Nifty 50 Replica' below it.
>     *   Bottom: A list of 3 items.
>         *   Item 1: 'HDFC Bank' with a green 'Held' badge.
>         *   Item 2: 'TCS' with a blue 'Substitute' badge.
>         *   Item 3: 'L&T' with an orange 'Missing' badge.
> Use distinct colors for badges (Green-500, Blue-500, Orange-500)."

### **C. Text-to-Code Prompt (for Flutter)**
> **Prompt:**
> "Write a Flutter widget named `BasketPreviewPage`.
> *   **Background**: Use a `Stack` with a `Container` having a `LinearGradient` (Colors.black to Colors.blueGrey.shade900).
> *   **Glass Effect**: Use `ClipRRect` containing a `BackdropFilter` (sigmaX/Y: 10) for the main content card.
> *   **Hero Section**: A `CustomPaint` or circular indicator widget showing '85%' progress.
> *   **List**: A `ListView.builder` returning `ListTile` widgets.
> *   **Styling**: Use white text for contrast. Valid items should have a `Icon(Icons.check_circle, color: Colors.greenAccent)`. Missing items should have `Icon(Icons.add_circle, color: Colors.orangeAccent)`."
