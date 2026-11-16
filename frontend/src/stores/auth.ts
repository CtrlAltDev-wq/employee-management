import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { login as loginRequest } from '@/api/auth'
import { clearSession, getStoredRoles, getStoredUsername, getToken, storeSession } from '@/utils/session'

/**
 * Session state. The token is mirrored into localStorage so a refresh keeps
 * the user signed in; the API layer reads the token from the same place.
 *
 * <p>Roles drive the UI only: the menu filters on them and {@code v-permission}
 * hides write actions. Every rule is enforced again by the backend, so a
 * tampered localStorage value gains nothing but a failing request.
 */
export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getToken())
  const username = ref<string>(getStoredUsername() ?? '')
  const roles = ref<string[]>(getStoredRoles())

  const isAuthenticated = computed(() => token.value !== null)
  const isAdmin = computed(() => roles.value.includes('ROLE_ADMIN'))
  const roleLabel = computed(() => {
    if (roles.value.length === 0) {
      return 'Guest'
    }
    return roles.value.map((role) => role.replace('ROLE_', '').toLowerCase()).join(', ')
  })

  async function login(user: string, password: string): Promise<void> {
    const response = await loginRequest({ username: user, password })
    token.value = response.accessToken
    username.value = response.username
    roles.value = response.roles ?? []
    storeSession(response.accessToken, response.username, roles.value)
  }

  function hasRole(role: string): boolean {
    return roles.value.includes(role)
  }

  /**
   * An empty requirement means "any signed-in user", which is what most routes
   * want; otherwise at least one of the listed roles has to be granted.
   */
  function hasAnyRole(required?: string[]): boolean {
    if (!required || required.length === 0) {
      return true
    }
    return required.some((role) => roles.value.includes(role))
  }

  function logout(): void {
    token.value = null
    username.value = ''
    roles.value = []
    clearSession()
  }

  return { token, username, roles, isAuthenticated, isAdmin, roleLabel, login, logout, hasRole, hasAnyRole }
})
