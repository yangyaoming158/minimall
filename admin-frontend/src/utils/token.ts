// Distinct from the customer storefront's key so an admin session and a
// customer session never collide if both apps are opened on the same host.
const TOKEN_KEY = 'minimall_admin_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}
