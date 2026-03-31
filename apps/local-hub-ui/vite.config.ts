import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";
import UnoCSS from "@unocss/vite";

export default defineConfig({
  plugins: [UnoCSS(), react()],
  server: {
    host: "127.0.0.1",
    port: 5174
  },
  preview: {
    host: "127.0.0.1",
    port: 4174
  }
});
