import type { Directive, DirectiveBinding } from 'vue'
import { useAuthStore } from '@/stores/auth'

type Requirement = string | string[]

/**
 * {@code v-permission="'ROLE_ADMIN'"} removes the element when the signed-in
 * user lacks the role; an array means "any of these".
 *
 * <p>This is a convenience for the user, not a security boundary: the API
 * rejects a forbidden write with {@code 403} regardless of what the DOM shows.
 */
export const permission: Directive<HTMLElement, Requirement> = {
  mounted(el: HTMLElement, binding: DirectiveBinding<Requirement>) {
    if (!isAllowed(binding.value)) {
      el.remove()
    }
  }
}

function isAllowed(requirement: Requirement | undefined): boolean {
  if (!requirement || (Array.isArray(requirement) && requirement.length === 0)) {
    return true
  }

  const required = Array.isArray(requirement) ? requirement : [requirement]
  return useAuthStore().hasAnyRole(required)
}
