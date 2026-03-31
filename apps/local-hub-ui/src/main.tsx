import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "uno.css";

import { App } from "./App.js";

const rootElement = document.getElementById("root");

if (rootElement == null) {
  throw new Error("missing root element");
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>
);
