<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Edit, Plus, Refresh, Search } from '@element-plus/icons-vue'
import EmployeeFormDialog from '@/components/EmployeeFormDialog.vue'
import { deleteEmployee, searchEmployees } from '@/api/employees'
import { listDepartments } from '@/api/departments'
import type { Department, Employee } from '@/api/types'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()

const loading = ref(false)
const employees = ref<Employee[]>([])
const total = ref(0)
const departments = ref<Department[]>([])

const dialogVisible = ref(false)
const editing = ref<Employee | null>(null)

const filters = reactive({
  firstName: '',
  lastName: '',
  email: '',
  departmentId: undefined as number | undefined
})

const pagination = reactive({ page: 0, size: 10 })
const sorting = reactive({ sort: 'id,asc' })

const departmentNames = computed(
  () => new Map(departments.value.map((department) => [department.id, department.name] as const))
)

async function loadEmployees(): Promise<void> {
  loading.value = true
  try {
    const result = await searchEmployees({
      firstName: filters.firstName.trim() || undefined,
      lastName: filters.lastName.trim() || undefined,
      email: filters.email.trim() || undefined,
      departmentId: filters.departmentId,
      page: pagination.page,
      size: pagination.size,
      sort: sorting.sort
    })
    employees.value = result?.content ?? []
    total.value = result?.totalElements ?? 0
  } catch {
    // The interceptor already explained the failure.
  } finally {
    loading.value = false
  }
}

async function loadDepartments(): Promise<void> {
  try {
    const result = await listDepartments()
    departments.value = Array.isArray(result) ? result : []
  } catch {
    departments.value = []
  }
}

function search(): void {
  pagination.page = 0
  void loadEmployees()
}

function reset(): void {
  filters.firstName = ''
  filters.lastName = ''
  filters.email = ''
  filters.departmentId = undefined
  pagination.page = 0
  sorting.sort = 'id,asc'
  void loadEmployees()
}

function openCreate(): void {
  editing.value = null
  dialogVisible.value = true
}

function openEdit(employee: Employee): void {
  editing.value = employee
  dialogVisible.value = true
}

async function handleSaved(): Promise<void> {
  dialogVisible.value = false
  await Promise.all([loadEmployees(), loadDepartments()])
}

async function remove(employee: Employee): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `Delete ${employee.firstName} ${employee.lastName}? This action cannot be undone.`,
      'Delete employee',
      { confirmButtonText: 'Delete', cancelButtonText: 'Cancel', type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await deleteEmployee(employee.id)
    ElMessage.success('Employee deleted.')
    // Step back a page when the last row of the current page was removed.
    if (employees.value.length === 1 && pagination.page > 0) {
      pagination.page -= 1
    }
    await Promise.all([loadEmployees(), loadDepartments()])
  } catch {
    // The interceptor already explained the failure.
  }
}

function handleSortChange(payload: { prop?: string | null; order?: string | null }): void {
  if (payload.prop && payload.order) {
    sorting.sort = `${payload.prop},${payload.order === 'ascending' ? 'asc' : 'desc'}`
  } else {
    sorting.sort = 'id,asc'
  }
  pagination.page = 0
  void loadEmployees()
}

function handlePageChange(page: number): void {
  pagination.page = page - 1
  void loadEmployees()
}

function handleSizeChange(size: number): void {
  pagination.size = size
  pagination.page = 0
  void loadEmployees()
}

function initials(employee: Employee): string {
  return `${employee.firstName.charAt(0)}${employee.lastName.charAt(0)}`.toUpperCase()
}

function formatSalary(value: number): string {
  return value.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 2 })
}

function formatDate(value: string): string {
  return new Date(value).toLocaleDateString('en-GB', {
    year: 'numeric',
    month: 'short',
    day: '2-digit'
  })
}

onMounted(async () => {
  await Promise.all([loadDepartments(), loadEmployees()])
})
</script>

<template>
  <div class="employees">
    <div class="page-header">
      <div>
        <h1 class="page-title">Employees</h1>
        <p class="page-subtitle">Search the directory, then create, edit or remove a person.</p>
      </div>

      <el-button v-permission="'ROLE_ADMIN'" type="primary" :icon="Plus" @click="openCreate">
        Add employee
      </el-button>
    </div>

    <section class="panel filters">
      <div class="filter-grid">
        <el-input
          v-model="filters.firstName"
          placeholder="First name"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-input
          v-model="filters.lastName"
          placeholder="Last name"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-input
          v-model="filters.email"
          placeholder="Email contains"
          clearable
          @keyup.enter="search"
          @clear="search"
        />
        <el-select v-model="filters.departmentId" placeholder="All departments" clearable @change="search">
          <el-option
            v-for="department in departments"
            :key="department.id"
            :label="department.name"
            :value="department.id"
          />
        </el-select>

        <div class="filter-actions">
          <el-button type="primary" :icon="Search" @click="search">Search</el-button>
          <el-button :icon="Refresh" @click="reset">Reset</el-button>
        </div>
      </div>
    </section>

    <section class="panel table-panel">
      <el-table
        v-loading="loading"
        :data="employees"
        row-key="id"
        class="employee-table"
        @sort-change="handleSortChange"
      >
        <el-table-column label="Employee" min-width="240">
          <template #default="{ row }">
            <div class="identity">
              <span class="avatar">{{ initials(row) }}</span>
              <div class="identity-text">
                <strong>{{ row.firstName }} {{ row.lastName }}</strong>
                <span>{{ row.email }}</span>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="Job title" min-width="180">
          <template #default="{ row }">
            <span v-if="row.jobTitle">{{ row.jobTitle }}</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>

        <el-table-column label="Department" min-width="150">
          <template #default="{ row }">
            <el-tag v-if="row.departmentId" effect="light" round size="small" class="department-tag">
              {{ departmentNames.get(row.departmentId) ?? `#${row.departmentId}` }}
            </el-tag>
            <span v-else class="muted">Unassigned</span>
          </template>
        </el-table-column>

        <el-table-column label="Salary" prop="salary" width="140" align="right" sortable="custom">
          <template #default="{ row }">
            <span class="salary">{{ formatSalary(row.salary) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="Joined" prop="createdAt" width="140" sortable="custom">
          <template #default="{ row }">
            <span class="muted">{{ formatDate(row.createdAt) }}</span>
          </template>
        </el-table-column>

        <!-- A viewer gets a read-only table: the API rejects writes with 403 anyway. -->
        <el-table-column v-if="auth.isAdmin" label="Actions" width="132" align="right">
          <template #default="{ row }">
            <el-button text type="primary" :icon="Edit" @click="openEdit(row)">Edit</el-button>
            <el-button text type="danger" :icon="Delete" @click="remove(row)">Delete</el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="No employees match these filters" :image-size="90" />
        </template>
      </el-table>

      <footer class="table-footer">
        <span class="result-count">{{ total }} employee{{ total === 1 ? '' : 's' }}</span>
        <el-pagination
          :current-page="pagination.page + 1"
          :page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          :total="total"
          layout="sizes, prev, pager, next"
          background
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </footer>
    </section>

    <EmployeeFormDialog
      v-model="dialogVisible"
      :employee="editing"
      :departments="departments"
      @saved="handleSaved"
    />
  </div>
</template>

<style scoped>
.filters {
  padding: 16px;
  margin-bottom: 16px;
}

.filter-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr)) auto;
  gap: 12px;
  align-items: center;
}

.filter-actions {
  display: flex;
  gap: 8px;
}

.table-panel {
  overflow: hidden;
}

.employee-table {
  width: 100%;
}

.identity {
  display: flex;
  align-items: center;
  gap: 11px;
}

.avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  border-radius: 50%;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary-dark-2);
  font-size: 12px;
  font-weight: 650;
  letter-spacing: 0.02em;
}

.identity-text {
  display: flex;
  flex-direction: column;
  line-height: 1.35;
  min-width: 0;
}

.identity-text strong {
  font-size: 13.5px;
  font-weight: 600;
  color: #262b3d;
}

.identity-text span {
  font-size: 12px;
  color: var(--app-muted);
  overflow: hidden;
  text-overflow: ellipsis;
}

.department-tag {
  border-color: transparent;
  background: #eef0fb;
  color: #4f46e5;
  font-weight: 550;
}

.salary {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: #2d3348;
}

.muted {
  color: #8b91a7;
  font-size: 12.5px;
}

.table-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border-top: 1px solid var(--app-border);
  background: #fcfdff;
}

.result-count {
  color: var(--app-muted);
  font-size: 12.5px;
}

@media (max-width: 1100px) {
  .filter-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .filter-actions {
    grid-column: 1 / -1;
  }
}
</style>
