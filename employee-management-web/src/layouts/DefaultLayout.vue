<script setup lang="ts">
import { computed, ref } from 'vue'
import { RouterView, useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowDown, Expand, Fold, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const collapsed = ref(false)

/**
 * The sidebar is derived from the route table, so a page and its menu entry can
 * never disagree, and filtering by role happens in one place.
 */
const menuItems = computed(() => {
  const shell = router.options.routes.find((record) => record.path === '/')
  return (shell?.children ?? [])
    .filter((record) => record.meta?.title && auth.hasAnyRole(record.meta.roles))
    .map((record) => ({
      path: `/${record.path}`,
      title: record.meta?.title ?? '',
      icon: record.meta?.icon
    }))
})

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => (typeof route.meta.title === 'string' ? route.meta.title : ''))
const initials = computed(() => auth.username.slice(0, 1).toUpperCase() || 'U')

async function handleCommand(command: string | number | object): Promise<void> {
  if (command !== 'logout') {
    return
  }

  try {
    await ElMessageBox.confirm('You will need to sign in again to continue.', 'Sign out?', {
      confirmButtonText: 'Sign out',
      cancelButtonText: 'Cancel',
      type: 'warning'
    })
  } catch {
    return
  }

  auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <el-container class="layout">
    <el-aside :width="collapsed ? '78px' : '238px'" class="sidebar">
      <div class="brand" :class="{ 'brand-collapsed': collapsed }">
        <div class="brand-mark">EM</div>
        <transition name="fade">
          <div v-if="!collapsed" class="brand-text">
            <strong>Employee</strong>
            <span>Management</span>
          </div>
        </transition>
      </div>

      <el-menu
        :default-active="activeMenu"
        :collapse="collapsed"
        :collapse-transition="false"
        router
        class="nav"
        background-color="transparent"
        text-color="#aab2cf"
        active-text-color="#ffffff"
      >
        <el-menu-item v-for="item in menuItems" :key="item.path" :index="item.path">
          <el-icon><component :is="item.icon" /></el-icon>
          <template #title>{{ item.title }}</template>
        </el-menu-item>
      </el-menu>

      <div v-if="!collapsed" class="sidebar-footer">
        <span class="status-dot" />
        Connected to employee-api
      </div>
    </el-aside>

    <el-container class="main-column">
      <el-header class="topbar">
        <div class="topbar-left">
          <el-button text circle class="collapse-button" @click="collapsed = !collapsed">
            <el-icon :size="18"><component :is="collapsed ? Expand : Fold" /></el-icon>
          </el-button>

          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">Home</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <el-dropdown trigger="click" @command="handleCommand">
          <div class="user-chip">
            <el-avatar :size="30" class="user-avatar">{{ initials }}</el-avatar>
            <span class="user-name">{{ auth.username }}</span>
            <el-tag size="small" effect="plain" class="user-role">{{ auth.roleLabel }}</el-tag>
            <el-icon class="user-caret"><ArrowDown /></el-icon>
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout" :icon="SwitchButton">Sign out</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="content">
        <RouterView v-slot="{ Component }">
          <transition name="fade-slide" mode="out-in">
            <component :is="Component" />
          </transition>
        </RouterView>
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout {
  min-height: 100vh;
}

.sidebar {
  display: flex;
  flex-direction: column;
  background: var(--app-sidebar);
  transition: width 0.18s ease;
  overflow: hidden;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 68px;
  padding: 0 20px;
  flex-shrink: 0;
}

.brand-collapsed {
  justify-content: center;
  padding: 0;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  border-radius: 11px;
  background: linear-gradient(135deg, #6366f1, #818cf8);
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  box-shadow: 0 8px 20px rgba(79, 70, 229, 0.35);
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.15;
  color: #f4f6ff;
  white-space: nowrap;
}

.brand-text strong {
  font-size: 14px;
  font-weight: 650;
}

.brand-text span {
  font-size: 11px;
  color: var(--app-sidebar-muted);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.nav {
  flex: 1;
  border-right: none;
  padding: 6px 12px;
}

.nav :deep(.el-menu-item) {
  height: 44px;
  margin-bottom: 4px;
  border-radius: 10px;
  font-size: 14px;
}

.nav :deep(.el-menu-item:hover) {
  background: rgba(255, 255, 255, 0.07);
}

.nav :deep(.el-menu-item.is-active) {
  background: linear-gradient(135deg, #4f46e5, #6366f1);
  box-shadow: 0 10px 22px rgba(79, 70, 229, 0.35);
}

.sidebar-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 18px 22px 22px;
  color: #6d7699;
  font-size: 11px;
  white-space: nowrap;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.18);
}

.main-column {
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 68px;
  padding: 0 24px;
  background: rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(8px);
  border-bottom: 1px solid var(--app-border);
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.collapse-button {
  color: #7a819b;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 5px 10px 5px 6px;
  border: 1px solid transparent;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.16s ease, border-color 0.16s ease;
}

.user-chip:hover {
  background: #f5f6fc;
  border-color: var(--app-border);
}

.user-avatar {
  background: linear-gradient(135deg, #4f46e5, #818cf8);
  font-size: 13px;
  font-weight: 600;
}

.user-name {
  font-size: 13px;
  font-weight: 550;
  color: #3a4157;
}

.user-role {
  text-transform: capitalize;
  font-size: 10px;
  letter-spacing: 0.04em;
}

.user-caret {
  color: #9096ac;
  font-size: 12px;
}

.content {
  padding: 26px 28px 34px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 720px) {
  .content {
    padding: 18px 14px 26px;
  }

  .user-name {
    display: none;
  }

  .user-role {
    display: none;
  }
}
</style>
