<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import { Refresh } from '@element-plus/icons-vue'
import { searchEmployees } from '@/api/employees'
import { listDepartments } from '@/api/departments'
import type { Department } from '@/api/types'

const loading = ref(true)
const departments = ref<Department[]>([])
const totalEmployees = ref(0)

const chartRef = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

const distribution = computed(() =>
  departments.value
    .map((department) => ({ name: department.name, value: department.employees?.length ?? 0 }))
    .sort((left, right) => right.value - left.value)
)

const assigned = computed(() => distribution.value.reduce((sum, item) => sum + item.value, 0))
const unassigned = computed(() => Math.max(totalEmployees.value - assigned.value, 0))
const largestDepartment = computed(() => distribution.value[0] ?? { name: 'No departments yet', value: 0 })

const cards = computed(() => [
  {
    label: 'Total employees',
    value: totalEmployees.value,
    hint: 'Across every department',
    tone: 'indigo'
  },
  {
    label: 'Departments',
    value: departments.value.length,
    hint: 'Active business units',
    tone: 'teal'
  },
  {
    label: 'Largest team',
    value: largestDepartment.value.value,
    hint: largestDepartment.value.name,
    tone: 'amber'
  },
  {
    label: 'Unassigned',
    value: unassigned.value,
    hint: 'Employees without a department',
    tone: 'rose'
  }
])

const chartData = computed(() => {
  const data = distribution.value.filter((item) => item.value > 0)
  if (unassigned.value > 0) {
    data.push({ name: 'Unassigned', value: unassigned.value })
  }
  return data
})

function renderChart(): void {
  if (!chartRef.value) {
    return
  }

  if (!chart) {
    chart = echarts.init(chartRef.value)
  }

  const data = chartData.value.length > 0 ? chartData.value : [{ name: 'No employees yet', value: 1 }]

  const option: EChartsOption = {
    color: ['#4f46e5', '#0ea5e9', '#14b8a6', '#f59e0b', '#8b5cf6', '#ef4444', '#22c55e', '#f97316'],
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      bottom: 0,
      icon: 'circle',
      itemWidth: 8,
      itemHeight: 8,
      textStyle: { color: '#6b7280', fontSize: 12 }
    },
    series: [
      {
        type: 'pie',
        radius: ['52%', '74%'],
        center: ['50%', '45%'],
        avoidLabelOverlap: true,
        itemStyle: { borderColor: '#ffffff', borderWidth: 3, borderRadius: 6 },
        label: { show: false },
        emphasis: {
          scale: true,
          scaleSize: 6,
          label: { show: true, fontSize: 15, fontWeight: 600, formatter: '{b}\n{c}' }
        },
        data
      }
    ]
  }

  chart.setOption(option, true)
}

async function load(): Promise<void> {
  loading.value = true
  try {
    const [page, departmentList] = await Promise.all([
      searchEmployees({ page: 0, size: 1, sort: 'id,asc' }),
      listDepartments()
    ])
    totalEmployees.value = page?.totalElements ?? 0
    // Guard the shape: a backend that answers with a bare array (or an empty
    // body) must render an empty dashboard instead of crashing the view.
    departments.value = Array.isArray(departmentList) ? departmentList : []
    await nextTick()
    renderChart()
  } catch {
    // The interceptor already displayed the reason.
  } finally {
    loading.value = false
  }
}

function handleResize(): void {
  chart?.resize()
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await load()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<template>
  <div class="dashboard">
    <div class="page-header">
      <div>
        <h1 class="page-title">Dashboard</h1>
        <p class="page-subtitle">Headcount and department distribution, computed from live API data.</p>
      </div>

      <el-button :icon="Refresh" :loading="loading" @click="load">Refresh</el-button>
    </div>

    <div class="card-grid">
      <article v-for="card in cards" :key="card.label" class="panel stat-card" :class="`tone-${card.tone}`">
        <span class="stat-label">{{ card.label }}</span>
        <strong class="stat-value">{{ card.value }}</strong>
        <span class="stat-hint">{{ card.hint }}</span>
      </article>
    </div>

    <div class="chart-grid">
      <section v-loading="loading" class="panel chart-panel">
        <header class="panel-header">
          <div>
            <h2>Department distribution</h2>
            <p>Share of employees per department</p>
          </div>
        </header>
        <div ref="chartRef" class="chart" />
      </section>

      <section class="panel ranking-panel">
        <header class="panel-header">
          <div>
            <h2>Team size ranking</h2>
            <p>Headcount per department</p>
          </div>
        </header>

        <div v-if="distribution.length === 0" class="empty-state">
          <el-empty description="No departments yet" :image-size="80" />
        </div>

        <ul v-else class="ranking">
          <li v-for="department in distribution" :key="department.name">
            <div class="ranking-head">
              <span class="ranking-name">{{ department.name }}</span>
              <span class="ranking-value">{{ department.value }}</span>
            </div>
            <el-progress
              :percentage="totalEmployees === 0 ? 0 : Math.round((department.value / totalEmployees) * 100)"
              :stroke-width="7"
              :show-text="false"
              color="#4f46e5"
            />
          </li>
        </ul>
      </section>
    </div>
  </div>
</template>

<style scoped>
.card-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.stat-card {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 18px 20px 20px;
  overflow: hidden;
}

.stat-card::after {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  border-radius: 3px;
}

.tone-indigo::after {
  background: #4f46e5;
}

.tone-teal::after {
  background: #14b8a6;
}

.tone-amber::after {
  background: #f59e0b;
}

.tone-rose::after {
  background: #f43f5e;
}

.stat-label {
  color: var(--app-muted);
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.07em;
}

.stat-value {
  font-size: 30px;
  font-weight: 650;
  line-height: 1.15;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
}

.stat-hint {
  color: #9096ac;
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chart-grid {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 16px;
}

.chart-panel,
.ranking-panel {
  padding: 20px 22px 22px;
}

.panel-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: 10px;
}

.panel-header h2 {
  margin: 0;
  font-size: 15px;
  font-weight: 620;
}

.panel-header p {
  margin: 4px 0 0;
  color: var(--app-muted);
  font-size: 12px;
}

.chart {
  width: 100%;
  height: 340px;
}

.ranking {
  margin: 16px 0 0;
  padding: 0;
  list-style: none;
  display: grid;
  gap: 16px;
}

.ranking-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 7px;
}

.ranking-name {
  font-size: 13px;
  font-weight: 550;
  color: #3a4157;
}

.ranking-value {
  font-size: 12px;
  color: var(--app-muted);
  font-variant-numeric: tabular-nums;
}

.empty-state {
  padding: 30px 0;
}

@media (max-width: 1160px) {
  .card-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-grid {
    grid-template-columns: 1fr;
  }
}
</style>
