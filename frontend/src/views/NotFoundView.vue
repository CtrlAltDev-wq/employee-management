<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()
</script>

<template>
  <div class="not-found">
    <section class="card">
      <span class="code">404</span>
      <h1>Page not found</h1>
      <p>The page you are looking for does not exist or has moved.</p>
      <div class="actions">
        <el-button type="primary" @click="router.push('/dashboard')">Go to dashboard</el-button>
        <el-button @click="router.back()">Go back</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.not-found {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
  background: var(--app-bg);
}

.card {
  width: 100%;
  max-width: 420px;
  padding: 40px 36px;
  text-align: center;
  background: #ffffff;
  border: 1px solid var(--app-border);
  border-radius: 18px;
  box-shadow: 0 24px 60px rgba(23, 30, 58, 0.08);
}

.code {
  display: inline-block;
  font-size: 46px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--el-color-primary);
}

h1 {
  margin: 8px 0 8px;
  font-size: 21px;
}

p {
  margin: 0 0 24px;
  color: var(--app-muted);
  font-size: 13.5px;
  line-height: 1.6;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}
</style>
