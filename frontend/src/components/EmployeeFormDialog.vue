<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { createEmployee, updateEmployee } from '@/api/employees'
import type { Department, Employee } from '@/api/types'

const props = defineProps<{
  modelValue: boolean
  employee: Employee | null
  departments: Department[]
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved'): void
}>()

const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  firstName: '',
  lastName: '',
  email: '',
  jobTitle: '',
  salary: 0 as number | undefined,
  departmentId: undefined as number | undefined
})

const isEdit = computed(() => props.employee !== null)
const title = computed(() => (isEdit.value ? 'Edit employee' : 'New employee'))

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})

const rules: FormRules = {
  firstName: [
    { required: true, message: 'First name is required', trigger: 'blur' },
    { max: 50, message: 'First name must not exceed 50 characters', trigger: 'blur' }
  ],
  lastName: [
    { required: true, message: 'Last name is required', trigger: 'blur' },
    { max: 50, message: 'Last name must not exceed 50 characters', trigger: 'blur' }
  ],
  email: [
    { required: true, message: 'Email is required', trigger: 'blur' },
    { type: 'email', message: 'Enter a valid email address', trigger: 'blur' },
    { max: 120, message: 'Email must not exceed 120 characters', trigger: 'blur' }
  ],
  jobTitle: [{ max: 100, message: 'Job title must not exceed 100 characters', trigger: 'blur' }],
  salary: [{ required: true, message: 'Salary is required', trigger: 'change' }]
}

/**
 * Copies the selected row into the form. The dialog is a controlled component,
 * so the caller decides when to open it and which employee to edit.
 */
function hydrate(): void {
  const employee = props.employee
  form.firstName = employee?.firstName ?? ''
  form.lastName = employee?.lastName ?? ''
  form.email = employee?.email ?? ''
  form.jobTitle = employee?.jobTitle ?? ''
  form.salary = employee?.salary ?? 0
  form.departmentId = employee?.departmentId ?? undefined
}

watch(
  () => props.modelValue,
  async (open) => {
    if (!open) {
      return
    }

    hydrate()
    await nextTick()
    formRef.value?.clearValidate()
  }
)

async function submit(): Promise<void> {
  if (!formRef.value) {
    return
  }

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) {
    return
  }

  const payload = {
    firstName: form.firstName.trim(),
    lastName: form.lastName.trim(),
    email: form.email.trim(),
    jobTitle: form.jobTitle.trim() || null,
    salary: form.salary ?? 0,
    departmentId: form.departmentId ?? null
  }

  submitting.value = true
  try {
    if (props.employee) {
      await updateEmployee(props.employee.id, payload)
      ElMessage.success('Employee updated.')
    } else {
      await createEmployee(payload)
      ElMessage.success('Employee created.')
    }

    visible.value = false
    emit('saved')
  } catch {
    // The interceptor displays the reason (field errors included); keep the
    // dialog open so the user can correct the input.
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="560px"
    :close-on-click-modal="false"
    destroy-on-close
    align-center
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="First name" prop="firstName">
            <el-input v-model="form.firstName" placeholder="Qi" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Last name" prop="lastName">
            <el-input v-model="form.lastName" placeholder="Wang" maxlength="50" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="Email" prop="email">
        <el-input v-model="form.email" placeholder="qi.wang@example.com" maxlength="120" />
      </el-form-item>

      <el-form-item label="Job title" prop="jobTitle">
        <el-input v-model="form.jobTitle" placeholder="Backend Engineer" maxlength="100" />
      </el-form-item>

      <el-row :gutter="16">
        <el-col :span="12">
          <el-form-item label="Annual salary" prop="salary">
            <el-input-number
              v-model="form.salary"
              class="salary-input"
              :min="0"
              :step="500"
              :precision="2"
              controls-position="right"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="Department" prop="departmentId">
            <el-select v-model="form.departmentId" clearable placeholder="Unassigned" class="department-select">
              <el-option
                v-for="department in departments"
                :key="department.id"
                :label="department.name"
                :value="department.id"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">Cancel</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">
        {{ isEdit ? 'Save changes' : 'Create employee' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.salary-input,
.department-select {
  width: 100%;
}
</style>
