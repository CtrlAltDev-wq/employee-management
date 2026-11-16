<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Lock, User } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: 'Username is required', trigger: 'blur' }],
  password: [{ required: true, message: 'Password is required', trigger: 'blur' }]
}

function fillDemo(username: string, password: string): void {
  form.username = username
  form.password = password
}

async function submit(): Promise<void> {
  if (!formRef.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  loading.value = true
  try {
    await auth.login(form.username.trim(), form.password)
    ElMessage.success(`Welcome back, ${auth.username}`)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/dashboard'
    await router.replace(redirect)
  } catch {
    // The response interceptor already displayed the reason; keep the form usable.
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <section class="brand-panel">
      <div class="brand-glow brand-glow-one" />
      <div class="brand-glow brand-glow-two" />

      <div class="brand-content">
        <div class="brand-mark">EM</div>
        <h1>Employee Management</h1>
        <p class="brand-tagline">
          One workspace for your people, departments and headcount — backed by a
          stateless JWT API.
        </p>

        <ul class="brand-points">
          <li>
            <span class="point-dot" />
            Search, sort and page through the whole directory
          </li>
          <li>
            <span class="point-dot" />
            Department distribution at a glance
          </li>
          <li>
            <span class="point-dot" />
            Role-based write access enforced by the backend
          </li>
        </ul>
      </div>

      <p class="brand-footnote">Spring Boot 3 · Vue 3 · Element Plus</p>
    </section>

    <section class="form-panel">
      <div class="form-card">
        <header>
          <h2>Sign in</h2>
          <p>Use your work account to continue.</p>
        </header>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
          <el-form-item label="Username" prop="username">
            <el-input v-model="form.username" size="large" placeholder="admin" :prefix-icon="User" autocomplete="username" />
          </el-form-item>

          <el-form-item label="Password" prop="password">
            <el-input
              v-model="form.password"
              size="large"
              type="password"
              placeholder="••••••••"
              :prefix-icon="Lock"
              show-password
              autocomplete="current-password"
              @keyup.enter="submit"
            />
          </el-form-item>

          <el-button type="primary" size="large" class="submit" :loading="loading" @click="submit">
            Sign in
          </el-button>
        </el-form>

        <div class="demo">
          <span class="demo-label">Demo accounts</span>
          <div class="demo-chips">
            <button type="button" class="demo-chip" @click="fillDemo('admin', 'admin123')">
              admin <small>full access</small>
            </button>
            <button type="button" class="demo-chip" @click="fillDemo('viewer', 'viewer123')">
              viewer <small>read only</small>
            </button>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  grid-template-columns: 1.05fr 1fr;
  min-height: 100%;
  background: #f6f7fc;
}

.brand-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 56px 60px;
  overflow: hidden;
  color: #f8f9ff;
  background: radial-gradient(circle at 18% 12%, #3b3f8f 0%, transparent 55%),
    linear-gradient(160deg, #1a1f3d 0%, #141a30 55%, #0f1426 100%);
}

.brand-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(4px);
  opacity: 0.55;
}

.brand-glow-one {
  width: 380px;
  height: 380px;
  right: -140px;
  top: -120px;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.55), transparent 68%);
}

.brand-glow-two {
  width: 300px;
  height: 300px;
  left: -120px;
  bottom: -100px;
  background: radial-gradient(circle, rgba(56, 189, 248, 0.35), transparent 70%);
}

.brand-content {
  position: relative;
  max-width: 430px;
  margin-top: 40px;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 13px;
  background: linear-gradient(135deg, #6366f1, #818cf8);
  font-weight: 700;
  letter-spacing: 0.02em;
  box-shadow: 0 12px 26px rgba(79, 70, 229, 0.35);
}

.brand-content h1 {
  margin: 26px 0 14px;
  font-size: 34px;
  line-height: 1.2;
  letter-spacing: -0.02em;
}

.brand-tagline {
  margin: 0;
  color: #b9c0dd;
  font-size: 15px;
  line-height: 1.7;
}

.brand-points {
  margin: 34px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 14px;
  color: #d6dbf0;
  font-size: 14px;
}

.brand-points li {
  display: flex;
  align-items: center;
  gap: 12px;
}

.point-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #818cf8;
  box-shadow: 0 0 0 4px rgba(129, 140, 248, 0.18);
}

.brand-footnote {
  position: relative;
  margin: 0;
  color: #7e87a8;
  font-size: 12px;
  letter-spacing: 0.04em;
}

.form-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
}

.form-card {
  width: 100%;
  max-width: 396px;
  padding: 36px 34px 30px;
  background: #ffffff;
  border: 1px solid var(--app-border);
  border-radius: 18px;
  box-shadow: 0 24px 60px rgba(23, 30, 58, 0.1);
}

.form-card header h2 {
  margin: 0 0 6px;
  font-size: 24px;
  letter-spacing: -0.01em;
}

.form-card header p {
  margin: 0 0 26px;
  color: var(--app-muted);
  font-size: 13px;
}

.submit {
  width: 100%;
  margin-top: 6px;
  letter-spacing: 0.01em;
}

.demo {
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px dashed var(--app-border);
}

.demo-label {
  display: block;
  margin-bottom: 10px;
  color: #8b91a7;
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.demo-chips {
  display: flex;
  gap: 10px;
}

.demo-chip {
  flex: 1;
  padding: 9px 12px;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  background: #fbfcff;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 600;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
}

.demo-chip small {
  display: block;
  margin-top: 2px;
  color: var(--app-muted);
  font-size: 11px;
  font-weight: 400;
}

.demo-chip:hover {
  border-color: var(--el-color-primary-light-5);
  box-shadow: 0 4px 14px rgba(79, 70, 229, 0.1);
}

@media (max-width: 960px) {
  .login-page {
    grid-template-columns: 1fr;
  }

  .brand-panel {
    display: none;
  }
}
</style>
