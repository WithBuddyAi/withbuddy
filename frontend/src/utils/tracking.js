import ReactGA from "react-ga4";

export function trackEvent(eventName, params) {
  ReactGA.event(eventName, params);
}