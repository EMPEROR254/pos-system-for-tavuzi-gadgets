// Leaving the app for another site (e.g. Facebook's sign-in page when linking
// an account). Kept here so tests can check it without really leaving.
export function goToExternal(url: string): void {
  window.location.assign(url)
}
