<template>
  <div class="zx-page stats-page liquid">
    <div class="page-head">
      <h2>📊 成绩查询</h2>
      <div class="gpa-badge">
        <span>累计 GPA</span>
        <b>{{ gpaText }}</b>
      </div>
    </div>

    <el-select v-model="selectedSem" placeholder="全部学期" clearable style="width:200px" @change="load" />

    <el-table :data="grades" v-loading="loading" style="width:100%">
      <el-table-column prop="courseName" label="课程名" min-width="180" />
      <el-table-column prop="teacherName" label="教师" width="140" />
      <el-table-column prop="category" label="类型" width="110" align="center" />
      <el-table-column prop="credit" label="学分" width="80" align="center" />
      <el-table-column label="成绩" width="120" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.totalScore != null" :type="scoreTag(row.totalScore)" size="small">{{ row.totalScore }}</el-tag>
          <span v-else class="muted">未公布</span>
        </template>
      </el-table-column>
      <el-table-column label="绩点" width="100" align="center">
        <template #default="{ row }">{{ row.totalScore != null ? scoreToGpa(row.totalScore).toFixed(2) : '—' }}</template>
      </el-table-column>
      <el-table-column label="明细" width="170" align="center">
        <template #default="{ row }">
          <span v-if="row.regularScore != null || row.finalScore != null" class="muted">
            平时 {{ row.regularScore ?? '—' }} · 期末 {{ row.finalScore ?? '—' }}
          </span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import http from '@/api'

interface Grade {
  courseId: number; courseName: string; teacherName: string; category: string
  credit: number; regularScore: number | null; finalScore: number | null; totalScore: number | null
}

const loading = ref(false)
const grades = ref<Grade[]>([])
const semesters = ref<any[]>([])
const selectedSem = ref<string | number | null>(null)
const gpa = ref<number | null>(null)

const gpaText = computed(() => gpa.value != null ? gpa.value.toFixed(2) : '—')

/** 与后端 GradeService.scoreToGpa 一致的 4.0 制换算 */
function scoreToGpa(s: number): number {
  if (s >= 90) return 4.0
  if (s >= 85) return 3.7
  if (s >= 80) return 3.3
  if (s >= 75) return 3.0
  if (s >= 70) return 2.7
  if (s >= 65) return 2.3
  if (s >= 60) return 2.0
  return 0.0
}

function scoreTag(s: number) {
  if (s >= 85) return 'success'
  if (s >= 60) return 'primary'
  return 'danger'
}

async function load() {
  loading.value = true
  try {
    const r: any = await http.get('/enrollments/scores')
    grades.value = (r?.data?.list || r?.data || []) as Grade[]
    // 按学分加权计算 GPA（与后端换算规则一致）
    const scored = grades.value.filter(g => g.totalScore != null && g.credit > 0)
    if (scored.length) {
      const weighted = scored.reduce((s, g) => s + scoreToGpa(g.totalScore!) * g.credit, 0)
      const credits = scored.reduce((s, g) => s + g.credit, 0)
      gpa.value = credits > 0 ? Math.round((weighted / credits) * 100) / 100 : null
    } else {
      gpa.value = null
    }
  } catch {}
  loading.value = false
}
onMounted(load)
</script>

<style scoped>
.zx-page { display: flex; flex-direction: column; gap: 14px; }
.page-head { display: flex; justify-content: space-between; align-items: center; }
.page-head h2 { margin: 0; color: var(--text); font-size: 20px; }

.gpa-badge {
  display: flex; align-items: center; gap: 10px;
  background: linear-gradient(135deg, rgba(245,158,11,0.18), rgba(168,85,247,0.12));
  border: 1px solid rgba(245,158,11,0.3);
  border-radius: 10px; padding: 8px 18px;
}
.gpa-badge span { font-size: 13px; color: #fbbf24; }
.gpa-badge b { font-size: 22px; font-weight: 700; color: #fde68a; font-family: 'JetBrains Mono', Consolas, monospace; }

:deep(.el-table) {
  --el-table-tr-bg-color: var(--card);
  --el-table-header-bg-color: var(--border);
  --el-table-border-color: var(--border);
  --el-table-text-color: var(--text);
  --el-table-header-text-color: var(--primary);
  background: var(--card); border-radius: 8px; overflow: hidden;
}
.muted { color: var(--text-lighter); font-size: 12px; }
</style>
