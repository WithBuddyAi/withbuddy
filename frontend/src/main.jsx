import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "react-calendar/dist/Calendar.css";
import "react-datepicker/dist/react-datepicker.css";
import "./index.css";
import App from "./App.jsx";
import { BrowserRouter } from "react-router-dom";
import { UserProvider } from "./contexts/UserContext.jsx";

createRoot(document.getElementById("root")).render(
  <StrictMode>
    <BrowserRouter>
      <UserProvider>
        <App />
      </UserProvider>
    </BrowserRouter>
  </StrictMode>,
);
