const TOKEN_KEY = 'employee-management.token'
const USERNAME_KEY = 'employee-management.username'
const ROLES_KEY = 'employee-management.roles'

/**
 * The token lives in localStorage so a page refresh keeps the session.
 * Every API request reads it from here, which keeps the Axios layer free of
 * an import cycle with the Pinia store.
 */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getStoredUsername(): string | null {
  return localStorage.getItem(USERNAME_KEY)
}

/**
 * Roles are persisted together with the token because the backend only reveals
 * them at login. They decide which menu entries and buttons render; the server
 * still enforces every rule on its own.
 */
export function getStoredRoles(): string[] {
  const raw = localStorage.getItem(ROLES_KEY)
  if (!raw) {
    return []
  }

  try {
    const parsed: unknown = JSON.parse(raw)
    return Array.isArray(parsed) ? parsed.filter((role): role is string => typeof role === 'string') : []
  } catch {
    return []
  }
}

export function storeSession(token: string, username: string, roles: string[]): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USERNAME_KEY, username)
  localStorage.setItem(ROLES_KEY, JSON.stringify(roles))
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USERNAME_KEY)
  localStorage.removeItem(ROLES_KEY)
}
