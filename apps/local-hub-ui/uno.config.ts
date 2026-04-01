import { defineConfig, presetWind3 } from "unocss";
import transformerVariantGroup from "@unocss/transformer-variant-group";

export default defineConfig({
  presets: [presetWind3()],
  transformers: [transformerVariantGroup()],
  preflights: [
    {
      getCSS: () => {
        return `
*, *::before, *::after { box-sizing: border-box; }
body { margin: 0; min-width: 320px; min-height: 100vh; }
button, input, textarea, select { font: inherit; }
input[type="text"] { appearance: none; -webkit-appearance: none; }
`;
      }
    }
  ],
  theme: {
    fontFamily: {
      sans: "\"Segoe UI Variable\", \"Trebuchet MS\", \"Gill Sans\", sans-serif",
      display: "\"Iowan Old Style\", \"Palatino Linotype\", \"Book Antiqua\", serif",
      mono: "\"JetBrains Mono\", \"SFMono-Regular\", ui-monospace, monospace"
    },
    keyframes: {
      "fade-up": "{0%{opacity:0;transform:translateY(18px)}100%{opacity:1;transform:translateY(0)}}"
    }
  }
});
