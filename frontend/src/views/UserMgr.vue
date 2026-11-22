<template>
  <div class="um-page">
    <!-- ================= 顶部 sticky 导航栏 ================= -->
    <header class="um-topbar">
      <div class="um-brand">
        <div class="um-brand-icon">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>
          </svg>
        </div>
        <div class="um-brand-text">
          <div class="um-title">{{ t('用户管理') }}</div>
          <div class="um-subtitle">{{ t('共 {n} 人', { n: counts.all ?? total }) }}</div>
        </div>
      </div>

      <button class="um-search-trigger" @click="openCommand" :aria-label="t('打开搜索')">
        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <span class="um-search-ph">{{ filters.keyword || t('搜索姓名 / 学号 / 班级 / 手机号，支持拼音首字母…') }}</span>
        <kbd class="um-kbd">{{ isMac ? '⌘' : 'Ctrl+' }} K</kbd>
      </button>

      <div class="um-top-actions">
        <button class="um-btn um-btn-outline" @click="pickAndImport('students', load)">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V4m0 0L7 9m5-5 5 5"/><path d="M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3"/></svg>
          <span>{{ t('导入 Excel') }}</span>
        </button>
        <button class="um-btn um-btn-solid" @click="openDialog(null)">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
          <span>{{ t('新增用户') }}</span>
        </button>
      </div>
    </header>

    <div class="um-layout">
      <!-- ================= 左侧组织树（240px，移动端隐藏） ================= -->
      <aside class="um-tree">
        <div class="um-tree-h"><b>{{ t('组织结构') }}</b><span>{{ classOpts.length }} {{ t('个班') }}</span></div>
        <div class="um-tree-search">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
          <input v-model="treeKw" :placeholder="t('过滤学院 / 专业 / 班级')" />
        </div>
        <div class="um-tree-body">
          <button class="um-node um-root" :class="{ on: !classFilter && !majorFilter && !collegeFilter }" @click="selectTree(null, null, null)">
            <span class="um-node-label">{{ t('全部用户') }}</span><span class="um-n">{{ counts.all ?? total }}</span>
          </button>
          <div v-for="col in treeCols" :key="col.name" class="um-cat">
            <button class="um-node" :class="{ on: collegeFilter === col.name && !majorFilter && !classFilter }" @click="clickCollege(col)">
              <span class="um-caret" :class="{ open: isOpen('c:' + col.name) }">
                <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m9 6 6 6-6 6"/></svg>
              </span>
              <span class="um-node-label">{{ col.name }}</span><span class="um-n">{{ col.count }}</span>
            </button>
            <div v-if="isOpen('c:' + col.name)" class="um-leaves">
              <template v-for="maj in col.majors" :key="maj.name">
                <button class="um-node um-leaf" :class="{ on: collegeFilter === col.name && majorFilter === maj.name && !classFilter }" @click="selectTree(col.name, maj.name, null)">
                  <span class="um-caret" :class="{ open: isOpen('m:' + col.name + '/' + maj.name) }">
                    <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m9 6 6 6-6 6"/></svg>
                  </span>
                  <span class="um-node-label">{{ maj.name }}</span><span class="um-n">{{ maj.count }}</span>
                </button>
                <div v-if="isOpen('m:' + col.name + '/' + maj.name)" class="um-subleaves">
                  <button v-for="cl in visibleClasses(maj, 'm:' + col.name + '/' + maj.name)" :key="cl.value"
                          class="um-node um-leaf2" :class="{ on: classFilter === cl.value }" @click="selectTree(col.name, maj.name, cl.value)">
                    <span class="um-node-label">{{ cl.name }}</span><span class="um-n">{{ cl.count }}</span>
                  </button>
                  <button v-if="moreClasses(maj, 'm:' + col.name + '/' + maj.name)" class="um-more" @click="expandMaj('m:' + col.name + '/' + maj.name)">
                    ＋ {{ t('展开其余 {n} 个班', { n: maj.classes.length - 6 }) }}
                  </button>
                </div>
              </template>
            </div>
          </div>
          <div v-if="!treeCols.length" class="um-tree-empty">{{ t('没有匹配的组织节点') }}</div>
        </div>
      </aside>

      <!-- ================= 右侧主区 ================= -->
      <main class="um-main">
        <!-- 筛选轨道卡片：六种胶囊 chip，读写同一个 filters 状态对象 -->
        <section class="um-filtercard">
          <div class="um-chip-row">
            <div v-for="chip in chipDefs" :key="chip.key" class="um-chip-wrap">
              <button class="um-chip" :class="{ on: chip.active }" @click="toggleChip(chip.key)">
                <span class="um-chip-label">{{ chip.label }}</span>
                <span v-if="chip.value" class="um-chip-value">{{ chip.value }}</span>
                <svg class="ic um-chip-arrow" :class="{ open: openChip === chip.key }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
              </button>
              <div v-if="openChip === chip.key" class="um-dropdown" @click.stop>
                <button class="um-opt" :class="{ on: !chip.current }" @click="chip.clear(); openChip = null">
                  <span>{{ t('全部') }}{{ chip.label }}</span><span v-if="!chip.current" class="um-opt-check">✓</span>
                </button>
                <button v-for="o in chip.options" :key="String(o.value)" class="um-opt" :class="{ on: chip.current === o.value }" @click="chip.set(o.value); openChip = null">
                  <span>{{ o.label }}<span v-if="o.count !== undefined" class="um-opt-n">{{ o.count }}</span></span>
                  <span v-if="chip.current === o.value" class="um-opt-check">✓</span>
                </button>
              </div>
            </div>
            <button v-if="hasFilter" class="um-reset" @click="clearFilters">{{ t('清空') }}</button>
          </div>
        </section>

        <!-- 数据表格卡片 -->
        <section class="um-tablecard">
          <div class="um-table-top">
            <div class="um-scope">
              <span class="um-scope-dot"></span>
              <b>{{ scopeLabel }}</b>
              <span class="um-scope-n">{{ total }} {{ t('人') }}</span>
            </div>
            <div class="um-table-actions">
              <button class="um-btn um-btn-ghost" @click="downloadTemplate('students')">{{ t('模板') }}</button>
              <button class="um-btn um-btn-ghost" @click="exportCurrent">{{ t('导出') }}</button>
              <button class="um-btn um-btn-ghost" :class="{ on: batchMode }" @click="batchMode = !batchMode">{{ t('批量操作') }}</button>
            </div>
          </div>

          <!-- 角色分层 Tab：人数来自后端全库聚合，不随筛选变化 -->
          <div class="um-rolebar">
            <button v-for="tb in roleTabs" :key="tb.label" class="um-role-tab" :class="{ on: roleFilter === tb.value }" @click="roleFilter = tb.value">
              {{ tb.label }}<span>{{ tb.count }}</span>
            </button>
          </div>

          <div class="um-hint">{{ t('点击任意一行查看完整档案，可进行编辑 / 重置密码 / 删除') }}</div>

          <div class="um-table-scroll">
            <table class="um-table">
              <thead>
                <tr>
                  <th v-if="batchMode" class="um-check-col"><input type="checkbox" :checked="allChecked" @change="toggleAll" /></th>
                  <th>{{ t('学号/工号') }}</th>
                  <th>{{ t('姓名') }}</th>
                  <th>{{ t('角色') }}</th>
                  <th>{{ t('性别') }}</th>
                  <th>{{ t('年级') }}</th>
                  <th>{{ t('学院') }}</th>
                  <th>{{ t('专业') }}</th>
                  <th>{{ t('班级') }}</th>
                  <th class="col-op">{{ t('操作') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="u in paged" :key="u.id" class="um-row" @click="detail = u">
                  <td v-if="batchMode" class="um-check-col" @click.stop><input type="checkbox" :checked="selectedIds.includes(u.id)" @change="toggleSel(u.id)" /></td>
                  <td><b class="um-no">{{ u.no }}</b></td>
                  <td><span class="um-name">{{ u.name }}</span></td>
                  <td><span class="um-status" :class="roleTag(u.role)">{{ roleLabel(u.role) }}</span></td>
                  <td>{{ u.gender || '—' }}</td>
                  <td><span v-if="u.role === 'student' && u.grade" class="um-grade">{{ t('{n} 级', { n: u.grade }) }}</span><span v-else class="um-muted">—</span></td>
                  <td>{{ u.college || '—' }}</td>
                  <td>{{ u.major || '—' }}</td>
                  <td>{{ u.classNo || '—' }}</td>
                  <td class="col-op">
                    <div class="um-op-icons">
                      <button class="um-icon-btn" :title="t('编辑')" @click.stop="openDialog(u)">
                        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z"/></svg>
                      </button>
                      <button class="um-icon-btn" :title="t('重置密码')" @click.stop="resetPwd(u)">
                        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/><path d="m9 9 6 6m0-6-6 6"/></svg>
                      </button>
                      <button class="um-icon-btn danger" :title="t('删除')" @click.stop="del(u)">
                        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18M8 6V4h8v2m-9 0 1 14h8l1-14"/></svg>
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>

            <!-- 加载 / 错误 / 空结果态 -->
            <div v-if="loading && !paged.length" class="um-state">{{ t('加载中…') }}</div>
            <div v-else-if="loadError && !paged.length" class="um-state err">{{ loadError }} · <button class="um-empty-reset" @click="load">{{ t('重试') }}</button></div>
            <div v-else-if="!paged.length" class="um-empty">
              <div class="um-empty-icon">
                <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4.5 4.5"/><path d="M8 10.5h5"/></svg>
              </div>
              <div class="um-empty-title">{{ t('没有找到匹配的用户') }}</div>
              <div class="um-empty-sub">{{ t('试试换个关键词，或看看下面的相似结果') }}</div>
              <div v-if="suggestChips.length" class="um-suggest">
                <span class="um-suggest-label">{{ t('是不是想找') }}</span>
                <button v-for="s in suggestChips" :key="s" class="um-suggest-chip" @click="filters.keyword = s">{{ s }}</button>
              </div>
              <button class="um-empty-reset" @click="clearFilters">{{ t('清空筛选') }}</button>
            </div>
          </div>

          <!-- 批量操作条 -->
          <div v-if="batchMode && selectedIds.length" class="um-batch">
            <b>{{ t('已选 {n} 人', { n: selectedIds.length }) }}</b>
            <button class="um-btn um-btn-ghost" @click="batchReset">{{ t('批量重置密码') }}</button>
            <button class="um-btn um-btn-danger" @click="batchDelete">{{ t('批量删除') }}</button>
            <button class="um-reset" @click="selectedIds = []">{{ t('取消选择') }}</button>
          </div>

          <!-- 分页 -->
          <div class="um-pagination">
            <span class="um-pg-info">{{ t('第 {a}-{b} 条，共 {n} 条', { a: rangeStart, b: rangeEnd, n: total }) }}</span>
            <div class="um-pg-right">
              <span class="um-pg-size">{{ t('每页') }}</span>
              <select v-model.number="pageSize" class="um-pg-select"><option v-for="n in [20,50,100]" :key="n" :value="n">{{ n }}</option></select>
              <div class="um-pg-btns">
                <button class="um-pg-btn" :disabled="page <= 1" @click="page--">‹</button>
                <template v-for="(p, i) in pageItems" :key="i">
                  <span v-if="p === '…'" class="um-pg-dots">…</span>
                  <button v-else class="um-pg-btn" :class="{ active: p === page }" @click="page = p as number">{{ p }}</button>
                </template>
                <button class="um-pg-btn" :disabled="page >= totalPages" @click="page++">›</button>
              </div>
              <span v-if="totalPages > 7" class="um-jump">{{ t('跳至') }}
                <input v-model.number="jumpTo" type="number" min="1" :max="totalPages" @keyup.enter="doJump" />
                {{ t('页') }}<button class="um-pg-btn" @click="doJump">GO</button>
              </span>
            </div>
          </div>
        </section>
      </main>
    </div>

    <!-- ================= 命令面板（⌘K / Ctrl+K） ================= -->
    <Teleport to="body">
      <div v-if="cmdOpen" class="cmd-overlay" @click="closeCommand(false)">
        <div class="cmd-panel" @click.stop>
          <div class="cmd-input-row">
            <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
            <input ref="cmdInput" v-model="cmdTerm" :placeholder="t('搜索姓名 / 学号 / 班级 / 手机号，支持拼音首字母…')" />
            <kbd class="um-kbd">esc</kbd>
          </div>
          <div class="cmd-results">
            <template v-if="cmdItems.length">
              <div v-if="cmdUsers.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('用户') }} <span>{{ cmdUsers.length }}</span></div>
                <button v-for="(u, i) in cmdUsers" :key="'u' + u.id"
                        class="cmd-item" :class="{ active: cmdCursor === i }"
                        @mouseenter="cmdCursor = i" @click="selectCmd(cmdItems[i])">
                  <span class="cmd-item-ico user">
                    <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>
                  </span>
                  <span class="cmd-item-main"><b>{{ u.name }}</b><small>{{ u.no || '—' }} · {{ roleLabel(u.role) }} · {{ u.classNo || u.college || '—' }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === i">↵</span>
                </button>
              </div>
              <div v-if="cmdOrgs.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('组织') }} <span>{{ cmdOrgs.length }}</span></div>
                <button v-for="(o, j) in cmdOrgs" :key="'o' + o.key"
                        class="cmd-item" :class="{ active: cmdCursor === cmdUsers.length + j }"
                        @mouseenter="cmdCursor = cmdUsers.length + j" @click="selectCmd(cmdItems[cmdUsers.length + j])">
                  <span class="cmd-item-ico org">
                    <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M3 21h18M5 21V7l7-4 7 4v14M9 21v-6h6v6"/></svg>
                  </span>
                  <span class="cmd-item-main"><b>{{ o.label }}</b><small>{{ o.kindText }} · {{ o.count }} {{ t('人') }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === cmdUsers.length + j">↵</span>
                </button>
              </div>
            </template>
            <div v-else class="cmd-empty">{{ cmdLoading ? t('搜索中…') : t('没有联想结果，试试姓名 / 学号 / 拼音首字母') }}</div>
          </div>
          <div class="cmd-footer">
            <span><kbd class="um-kbd">↑</kbd><kbd class="um-kbd">↓</kbd> {{ t('导航') }}</span>
            <span><kbd class="um-kbd">↵</kbd> {{ t('选中') }}</span>
            <span class="cmd-footer-tip">{{ t('选中用户看档案 · 选中组织即筛选') }}</span>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ================= 用户详情抽屉 ================= -->
    <Teleport to="body">
      <div v-if="detail" class="um-mask" @click="detail = null">
        <div class="um-drawer" @click.stop>
          <div class="um-drawer-h">
            <div><b>{{ detail.name }}</b><span>{{ detail.no }} · {{ roleLabel(detail.role) }}</span></div>
            <button class="um-close" @click="detail = null">×</button>
          </div>
          <div class="um-drawer-body">
            <div v-for="f in detailFields" :key="f.label" class="um-field"><span>{{ f.label }}</span><b>{{ f.value || '—' }}</b></div>
          </div>
          <div class="um-drawer-f">
            <button class="um-btn um-btn-solid" @click="editFromDrawer(detail)">{{ t('编辑') }}</button>
            <button class="um-btn um-btn-outline" @click="resetPwd(detail)">{{ t('重置密码') }}</button>
            <button class="um-btn um-btn-danger" @click="del(detail)">{{ t('删除') }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ================= 新增 / 编辑用户 ================= -->
    <el-dialog v-model="dlg" :title="form.id ? t('编辑用户') : t('新增用户')" width="480px">
      <el-form :model="form" label-position="top">
        <el-row :gutter="12">
          <el-col :span="12"><el-form-item :label="t('学号/工号')"><el-input v-model="form.no" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('姓名')"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('角色')">
            <el-select v-model="form.role" style="width:100%"><el-option :label="t('学生')" value="student" /><el-option :label="t('教师')" value="teacher" /><el-option :label="t('管理员')" value="admin" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('密码')"><el-input v-model="form.password" type="password" show-password :placeholder="t('新增默认123456，编辑留空不改')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('性别（仅学生）')">
            <el-select v-if="form.role === 'student'" v-model="form.gender" clearable :placeholder="t('选择性别')" style="width:100%"><el-option :label="t('男')" value="男" /><el-option :label="t('女')" value="女" /></el-select>
            <el-input v-else disabled :placeholder="t('— 非学生无需性别 —')" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('学院')">
            <!-- 学院是课题库/导师隔离的依据：统一从 COLLEGES 取值，filterable 兼容历史自定义学院 -->
            <el-select v-model="form.college" filterable allow-create default-first-option :placeholder="t('选择或输入学院')" style="width:100%">
              <el-option v-for="c in (form.role === 'student' ? STUDENT_COLLEGES : COLLEGES)" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('专业')"><el-input v-model="form.major" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('年级（入学年份，仅学生）')">
            <el-input-number v-if="form.role === 'student'" v-model="form.grade" :min="2000" :max="2100" :controls="false" :placeholder="t('如 2023')" style="width:100%" />
            <el-input v-else disabled :placeholder="t('— 非学生无需年级 —')" />
          </el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <button class="um-btn um-btn-ghost" @click="dlg = false">{{ t('取消') }}</button>
        <button class="um-btn um-btn-solid" @click="save">{{ t('保存') }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, toRefs, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
import { encryptPassword } from '@/utils/rsa'
import { downloadTemplate, pickAndImport } from '@/utils/xlsxImport'
import { COLLEGES, STUDENT_COLLEGES } from '@/utils/colleges'
import { diceCoefficient } from '@/utils/similarity'
import { initialsOf } from '@/utils/pinyin'

const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)

/* ============================================================
 * 单一状态源：所有筛选（组织树 / 筛选轨道 chips / 命令面板 / 角色 Tab）
 * 都读写这同一个 filters 对象，绝不出现两套筛选逻辑。
 * 用 toRefs 拆出 ref，保持服务端分页 load() 的 .value 写法不变。
 * ============================================================ */
const filters = reactive({
  keyword: '' as string,
  role: null as string | null,
  college: null as string | null,
  major: null as string | null,
  grade: null as number | null,
  classNo: null as string | null,   // '__none__' = 未分班 / 教职工
  zsbOnly: null as boolean | null,
})
const { keyword: kw, role: roleFilter, college: collegeFilter, major: majorFilter, grade: gradeFilter, classNo: classFilter, zsbOnly: zsbFilter } = toRefs(filters)

/* —— 列表数据（服务端分页） —— */
const users = ref<any[]>([])
const total = ref(0)
const counts = ref<Record<string, number>>({})
const classOpts = ref<string[]>([])
const classCounts = ref<Record<string, number>>({})
const treeData = ref<any[]>([])
const loading = ref(false)
const loadError = ref('')
let reqSeq = 0
const pageSize = ref(20)
const page = ref(1)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))
const paged = computed(() => users.value)
const rangeStart = computed(() => total.value ? (page.value - 1) * pageSize.value + 1 : 0)
const rangeEnd = computed(() => Math.min(page.value * pageSize.value, total.value))
const pageItems = computed<(number | '…')[]>(() => {
  const tp = totalPages.value, c = page.value
  if (tp <= 7) return Array.from({ length: tp }, (_, i) => i + 1)
  const set = new Set([1, 2, tp - 1, tp, c - 1, c, c + 1].filter(p => p >= 1 && p <= tp))
  const arr = [...set].sort((a, b) => a - b)
  const out: (number | '…')[] = []
  let prev = 0
  for (const p of arr) { if (p - prev > 1) out.push('…'); out.push(p); prev = p }
  return out
})
const jumpTo = ref<number | null>(null)
function doJump() {
  const n = Number(jumpTo.value)
  if (!Number.isFinite(n) || n < 1) return
  page.value = Math.min(Math.max(1, Math.round(n)), totalPages.value)
  jumpTo.value = null
}

/* —— 筛选选项：register-options(专业字典 ∪ 学生档案)，学院→专业级联 —— */
const optLoaded = ref(false)
const optColleges = ref<string[]>([])
const optMajorsByCollege = ref<Record<string, string[]>>({})
const optGrades = ref<number[]>([])
const collegeOpts = computed(() => (optLoaded.value && optColleges.value.length ? optColleges.value : COLLEGES))
const majorOpts = computed(() => {
  if (collegeFilter.value) return optMajorsByCollege.value[collegeFilter.value] || []
  const all = new Set<string>()
  for (const list of Object.values(optMajorsByCollege.value)) list.forEach(m => all.add(m))
  return [...all].sort((a, b) => a.localeCompare(b, 'zh'))
})
const gradeOpts = computed(() => (optGrades.value.length ? optGrades.value : Array.from({ length: 6 }, (_, i) => 2021 + i)))
async function loadFilterOptions() {
  try {
    // 自助注册移除后，学院/专业/年级聚合迁至管理端独立接口
    const r: any = await http.get('/admin/major-dict')
    if (r?.success && r.data) {
      optColleges.value = r.data.colleges || []
      optMajorsByCollege.value = r.data.majors || {}
      optGrades.value = r.data.grades || []
      optLoaded.value = true
    }
  } catch { /* 选项加载失败时用兜底常量，不影响主列表 */ }
}

/* —— 角色 Tab：人数来自后端全库聚合，不随筛选变化 —— */
const roleLabel = (r: string) => ({ student: t('学生'), teacher: t('教师'), admin: t('管理员') } as any)[r] || r
const roleTag = (r: string) => ({ student: 'status-approved', teacher: 'status-pending', admin: 'status-warning' } as any)[r] || 'status-approved'
const roleTabs = computed(() => [
  { value: null, label: t('全部'), count: counts.value.all ?? total.value },
  { value: 'student', label: t('学生'), count: counts.value.student ?? 0 },
  { value: 'teacher', label: t('教师'), count: counts.value.teacher ?? 0 },
  { value: 'admin', label: t('管理员'), count: counts.value.admin ?? 0 },
])

/* —— 左侧组织树：学院 → 专业 → 班级（计数基于全库，读起来稳定） —— */
const treeKw = ref('')
const openNodes = ref<Set<string>>(new Set())
const expandedMaj = ref<Set<string>>(new Set())
function isOpen(k: string) { return treeKw.value.trim() ? true : openNodes.value.has(k) }
function toggleNode(k: string) { const s = new Set(openNodes.value); s.has(k) ? s.delete(k) : s.add(k); openNodes.value = s }
function expandMaj(k: string) { const s = new Set(expandedMaj.value); s.add(k); expandedMaj.value = s }
function visibleClasses(maj: any, k: string) {
  const list = maj.classes || []
  return expandedMaj.value.has(k) || treeKw.value.trim() ? list : list.slice(0, 6)
}
function moreClasses(maj: any, k: string) {
  return (maj.classes || []).length > 6 && !expandedMaj.value.has(k) && !treeKw.value.trim()
}
const treeCols = computed(() => {
  const q = treeKw.value.trim().toLowerCase()
  return treeData.value.map((col: any) => {
    const majors = (col.majors || []).map((maj: any) => ({
      ...maj,
      classes: (maj.classes || []).filter((cl: any) =>
        !q || String(col.name).toLowerCase().includes(q)
        || String(maj.name).toLowerCase().includes(q)
        || String(cl.name).toLowerCase().includes(q)),
    })).filter((m: any) => m.classes.length)
    return { ...col, majors }
  }).filter((c: any) => c.majors.length)
})
function clickCollege(col: any) {
  collegeFilter.value = collegeFilter.value === col.name ? null : col.name
  majorFilter.value = null; classFilter.value = null
  toggleNode('c:' + col.name)
}
function selectTree(col: string | null, maj: string | null, cl: string | null) {
  collegeFilter.value = col; majorFilter.value = maj; classFilter.value = cl
  selectedIds.value = []
}

/* —— 筛选轨道 chip 定义：读写同一个 filters —— */
type ChipKey = 'role' | 'college' | 'major' | 'grade' | 'zsb' | 'class'
const openChip = ref<ChipKey | null>(null)
function toggleChip(k: ChipKey) { openChip.value = openChip.value === k ? null : k }
const chipDefs = computed(() => [
  {
    key: 'role' as ChipKey, label: t('角色'), active: !!roleFilter.value, value: roleFilter.value ? roleLabel(roleFilter.value) : '', current: roleFilter.value,
    options: [
      { value: 'student', label: t('学生'), count: counts.value.student ?? 0 },
      { value: 'teacher', label: t('教师'), count: counts.value.teacher ?? 0 },
      { value: 'admin', label: t('管理员'), count: counts.value.admin ?? 0 },
    ],
    clear: () => { roleFilter.value = null }, set: (v: any) => { roleFilter.value = v as string | null },
  },
  {
    key: 'college' as ChipKey, label: t('学院'), active: !!collegeFilter.value, value: collegeFilter.value || '', current: collegeFilter.value,
    options: [...new Set([...collegeOpts.value, ...treeData.value.map((c: any) => c.name)])]
      .filter(Boolean).sort((a, b) => a.localeCompare(b, 'zh'))
      .map(c => ({ value: c, label: c, count: treeData.value.find((x: any) => x.name === c)?.count as number | undefined })),
    clear: () => { collegeFilter.value = null; majorFilter.value = null; classFilter.value = null }, set: (v: any) => { collegeFilter.value = v as string | null; majorFilter.value = null; classFilter.value = null },
  },
  {
    key: 'major' as ChipKey, label: t('专业'), active: !!majorFilter.value, value: majorFilter.value || '', current: majorFilter.value,
    options: majorOpts.value.map(m => ({ value: m, label: m, count: undefined as number | undefined })),
    clear: () => { majorFilter.value = null; classFilter.value = null }, set: (v: any) => { majorFilter.value = v as string | null; classFilter.value = null },
  },
  {
    key: 'grade' as ChipKey, label: t('年级'), active: gradeFilter.value !== null, value: gradeFilter.value !== null ? String(gradeFilter.value) : '', current: gradeFilter.value,
    options: gradeOpts.value.map(g => ({ value: g, label: t('{n} 级', { n: g }), count: undefined as number | undefined })),
    clear: () => { gradeFilter.value = null }, set: (v: any) => { gradeFilter.value = v as number | null },
  },
  {
    key: 'zsb' as ChipKey, label: t('学制'), active: zsbFilter.value !== null, value: zsbFilter.value !== null ? (zsbFilter.value ? t('仅专升本') : t('仅普通本科')) : '', current: zsbFilter.value,
    options: [{ value: true, label: t('仅专升本'), count: undefined as number | undefined }, { value: false, label: t('仅普通本科'), count: undefined as number | undefined }],
    clear: () => { zsbFilter.value = null }, set: (v: any) => { zsbFilter.value = v as boolean | null },
  },
  {
    key: 'class' as ChipKey, label: t('班级'), active: !!classFilter.value, value: classFilter.value === '__none__' ? t('未分班 / 教职工') : (classFilter.value || ''), current: classFilter.value,
    options: [{ value: '__none__', label: t('未分班 / 教职工'), count: undefined as number | undefined },
      ...classOpts.value.map(c => ({ value: c, label: c, count: classCounts.value[c] as number | undefined }))],
    clear: () => { classFilter.value = null }, set: (v: any) => { classFilter.value = v as string | null },
  },
])
const hasFilter = computed(() => !!(kw.value || roleFilter.value || collegeFilter.value || majorFilter.value || gradeFilter.value !== null || classFilter.value || zsbFilter.value !== null))
function clearFilters() {
  kw.value = ''; roleFilter.value = null; collegeFilter.value = null; majorFilter.value = null
  gradeFilter.value = null; classFilter.value = null; zsbFilter.value = null
  page.value === 1 ? load() : (page.value = 1)
}
const scopeLabel = computed(() => {
  if (classFilter.value === '__none__') return t('未分班 / 教职工')
  if (classFilter.value) return classFilter.value
  if (majorFilter.value) return majorFilter.value
  if (collegeFilter.value) return collegeFilter.value
  return t('全部用户')
})

/* —— 筛选变化触发重新拉取（服务端分页唯一过滤出口） —— */
watch([kw, roleFilter, collegeFilter, majorFilter, gradeFilter, classFilter, zsbFilter], () => {
  page.value === 1 ? load() : (page.value = 1)
})
watch(pageSize, () => { page.value === 1 ? load() : (page.value = 1) })
watch(page, load)
watch(totalPages, tp => { if (page.value > tp) page.value = tp })

/* —— 详情抽屉字段 —— */
const detail = ref<any>(null)
const detailFields = computed(() => !detail.value ? [] : [
  { label: t('学号/工号'), value: detail.value.no },
  { label: t('姓名'), value: detail.value.name },
  { label: t('角色'), value: roleLabel(detail.value.role) },
  { label: t('性别'), value: detail.value.gender || '' },
  { label: t('年级'), value: detail.value.grade ? `${detail.value.grade} ${t('级')}` : '' },
  { label: t('学院'), value: detail.value.college },
  { label: t('专业'), value: detail.value.major },
  { label: t('班级'), value: detail.value.classNo },
  { label: t('手机号'), value: detail.value.phone },
])

/* —— 批量操作 —— */
const batchMode = ref(false)
const selectedIds = ref<number[]>([])
const allChecked = computed(() => users.value.length > 0 && users.value.every((u: any) => selectedIds.value.includes(u.id)))
function toggleAll(e: any) { selectedIds.value = e.target.checked ? users.value.map((u: any) => u.id) : [] }
function toggleSel(id: number) {
  selectedIds.value = selectedIds.value.includes(id)
    ? selectedIds.value.filter(x => x !== id)
    : [...selectedIds.value, id]
}
async function batchReset() {
  if (!selectedIds.value.length) return
  try { await ElMessageBox.confirm(t('将选中的 {n} 个账号密码重置为 123456？', { n: selectedIds.value.length }), t('提示'), { type: 'warning' }) } catch { return }
  try {
    const pwd = await encryptPassword('123456')
    for (const id of selectedIds.value) await http.put(`/admin/users/${id}/reset-password`, { newPassword: pwd })
    ElMessage.success(t('已重置')); selectedIds.value = []; load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}
async function batchDelete() {
  if (!selectedIds.value.length) return
  try { await ElMessageBox.confirm(t('确定删除选中的 {n} 个账号？此操作不可撤销。', { n: selectedIds.value.length }), t('提示'), { type: 'warning' }) } catch { return }
  try {
    for (const id of selectedIds.value) await http.delete(`/admin/users/${id}`)
    ElMessage.success(t('已删除')); selectedIds.value = []; detail.value = null; load()
  } catch (e: any) { ElMessage.error(e?.message || t('操作失败')) }
}

/* —— 命令面板用户池：首次打开时懒加载一次全量用户，预计算拼音首字母 —— */
const cmdPool = ref<any[]>([])
const cmdPoolLoaded = ref(false)
const cmdLoading = ref(false)
async function ensureCmdPool() {
  if (cmdPoolLoaded.value || cmdLoading.value) return
  cmdLoading.value = true
  try {
    const r: any = await http.get('/admin/users')   // 兼容模式：不传 page/size 返回全量数组
    cmdPool.value = (Array.isArray(r?.data) ? r.data : []).map((u: any) => ({
      ...u,
      _py: initialsOf(`${u.name || ''} ${u.no || ''} ${u.classNo || ''} ${u.college || ''} ${u.major || ''} ${u.phone || ''}`),
    }))
    cmdPoolLoaded.value = true
  } catch { /* 联想不可用不影响主流程 */ }
  finally { cmdLoading.value = false }
}

/* —— 组织节点（学院 / 专业 / 班级）：本地 tree 展平，供命令面板联想 —— */
type OrgItem = { key: string; kind: 'college' | 'major' | 'class'; value: string; label: string; count: number; kindText: string; _py: string }
const allOrgs = computed<OrgItem[]>(() => {
  const out: OrgItem[] = []
  for (const col of treeData.value) {
    out.push({ key: 'c:' + col.name, kind: 'college', value: col.name, label: col.name, count: col.count || 0, kindText: t('学院'), _py: initialsOf(col.name) })
    for (const maj of (col.majors || [])) {
      out.push({ key: 'm:' + col.name + '/' + maj.name, kind: 'major', value: maj.name, label: maj.name, count: maj.count || 0, kindText: `${col.name} · ${t('专业')}`, _py: initialsOf(maj.name) })
      for (const cl of (maj.classes || [])) {
        out.push({ key: 'cl:' + cl.name, kind: 'class', value: cl.name, label: cl.name, count: cl.count || 0, kindText: t('班级'), _py: initialsOf(cl.name) })
      }
    }
  }
  return out
})

/* —— 空结果 Dice 纠错：候选 = 组织名 + 用户姓名 —— */
/* 人名多为 2-3 字，bigram Dice 对短词不敏感（如「张纬/张伟」得 0 分），
   故混合 bigram 与单字 Dice 取较高分，短名纠错更稳。 */
function charDice(a: string, b: string): number {
  const A = new Set(Array.from(a.toLowerCase().replace(/\s+/g, '')))
  const B = new Set(Array.from(b.toLowerCase().replace(/\s+/g, '')))
  if (!A.size || !B.size) return 0
  let inter = 0
  for (const c of A) if (B.has(c)) inter++
  return (2 * inter) / (A.size + B.size)
}
watch(kw, v => { if (v.trim() && !cmdPoolLoaded.value) ensureCmdPool() })
const suggestChips = computed(() => {
  const k = kw.value.trim()
  if (!k) return []
  const names = new Set<string>()
  for (const o of allOrgs.value) names.add(o.label)
  for (const u of cmdPool.value) if (u.name) names.add(u.name)
  const scored = new Map<string, number>()
  for (const n of names) {
    const s = Math.max(diceCoefficient(k, n), charDice(k, n))
    if (s >= 0.34) scored.set(n, s)
  }
  return [...scored.entries()].sort((a, b) => b[1] - a[1]).slice(0, 5).map(([n]) => n)
})

/* ============================================================
 * 命令面板：即时联想（用户 + 组织分组）、↑↓ 导航、Enter 选中、Esc 关闭
 * 用户/组织都与表格共享同一个 filters
 * ============================================================ */
const cmdOpen = ref(false)
const cmdTerm = ref('')
const cmdCursor = ref(0)
const cmdInput = ref<HTMLInputElement | null>(null)
function openCommand() {
  cmdTerm.value = kw.value
  cmdCursor.value = 0
  cmdOpen.value = true
  ensureCmdPool()
  nextTick(() => cmdInput.value?.focus())
}
function closeCommand(clear: boolean) {
  cmdOpen.value = false
  if (clear) { cmdTerm.value = ''; kw.value = '' }
  else { cmdTerm.value = kw.value }   // 关闭面板保留已输入关键词 → 空结果态/Dice 纠错可达
}
watch(cmdTerm, v => { if (cmdOpen.value) kw.value = v })
function matchText(haystack: string, py: string, q: string): boolean {
  const ql = q.toLowerCase().replace(/\s+/g, '')
  if (!ql) return true
  if ((haystack || '').toLowerCase().replace(/\s+/g, '').includes(ql)) return true
  return !!py && py.includes(ql)
}
const cmdUsers = computed(() => {
  const q = cmdTerm.value.trim()
  const arr = !q ? cmdPool.value.slice(0, 8)
    : cmdPool.value.filter(u => matchText(`${u.name || ''} ${u.no || ''} ${u.classNo || ''} ${u.college || ''} ${u.major || ''} ${u.phone || ''}`, u._py || '', q)).slice(0, 8)
  return arr
})
const cmdOrgs = computed(() => {
  const q = cmdTerm.value.trim()
  if (!q) return []
  return allOrgs.value.filter(o => matchText(o.label, o._py, q)).slice(0, 6)
})
type CmdItem = { kind: 'user' | 'org'; raw: any }
const cmdItems = computed<CmdItem[]>(() => [
  ...cmdUsers.value.map(u => ({ kind: 'user' as const, raw: u })),
  ...cmdOrgs.value.map(o => ({ kind: 'org' as const, raw: o })),
])
watch(cmdItems, () => { if (cmdCursor.value >= cmdItems.value.length) cmdCursor.value = 0 })
function selectCmd(item: CmdItem | undefined) {
  if (!item) return
  if (item.kind === 'user') {
    detail.value = item.raw
    cmdOpen.value = false; cmdTerm.value = ''; kw.value = ''
  } else {
    const o: OrgItem = item.raw
    if (o.kind === 'college') { collegeFilter.value = o.value; majorFilter.value = null; classFilter.value = null }
    else if (o.kind === 'major') { collegeFilter.value = allOrgs.value.find(x => x.key === o.key)?.kindText.split(' · ')[0] || null; majorFilter.value = o.value; classFilter.value = null }
    else { classFilter.value = o.value }
    cmdOpen.value = false; cmdTerm.value = ''; kw.value = ''
    ElMessage.success(t('已按组织筛选：{n}', { n: o.label }))
  }
}
function onGlobalKey(e: KeyboardEvent) {
  const meta = e.metaKey || e.ctrlKey
  if (meta && e.key.toLowerCase() === 'k') {
    e.preventDefault()
    cmdOpen.value ? closeCommand(false) : openCommand()
    return
  }
  if (!cmdOpen.value) return
  if (e.key === 'Escape') { e.preventDefault(); closeCommand(false) }
  else if (e.key === 'ArrowDown') { e.preventDefault(); if (cmdItems.value.length) cmdCursor.value = (cmdCursor.value + 1) % cmdItems.value.length }
  else if (e.key === 'ArrowUp') { e.preventDefault(); if (cmdItems.value.length) cmdCursor.value = (cmdCursor.value - 1 + cmdItems.value.length) % cmdItems.value.length }
  else if (e.key === 'Enter') { e.preventDefault(); selectCmd(cmdItems.value[cmdCursor.value]) }
}

/* —— 导出当前筛选范围（全量，不受分页影响） —— */
function currentParams(): any {
  const p: any = { page: 1, size: 10000 }
  if (roleFilter.value) p.role = roleFilter.value
  if (collegeFilter.value) p.college = collegeFilter.value
  if (majorFilter.value) p.major = majorFilter.value
  if (gradeFilter.value) p.grade = gradeFilter.value
  if (classFilter.value) p.classNo = classFilter.value
  if (zsbFilter.value !== null) p.zsbOnly = zsbFilter.value
  if (kw.value.trim()) p.keyword = kw.value.trim()
  return p
}
async function exportCurrent() {
  try {
    const p: any = currentParams()
    delete p.page; delete p.size
    const r: any = await http.get('/admin/users/export', { params: p, responseType: 'blob' })
    const blob = r instanceof Blob ? r : new Blob([r], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `用户-${scopeLabel.value}.xlsx`
    document.body.appendChild(a); a.click(); a.remove()
    URL.revokeObjectURL(a.href)
    ElMessage.success(t('已导出 Excel 文件'))
  } catch (e: any) { ElMessage.error(e?.message || t('导出失败')) }
}

/* —— 新增 / 编辑用户 —— */
const dlg = ref(false)
const form = reactive<any>({ id: null, no: '', name: '', role: 'student', password: '123456', college: '', major: '', grade: null, gender: '' })
function openDialog(u: any) {
  // 先清零再覆盖：编辑对象不含 password，若不清零会残留上次输入的密码（甚至被二次加密）覆盖目标用户密码。
  Object.assign(form, { id: null, no: '', name: '', role: 'student', password: '', college: '', major: '', grade: null })
  if (u) Object.assign(form, u)
  else form.password = '123456'
  dlg.value = true
}
function editFromDrawer(u: any) { detail.value = null; openDialog(u) }
async function save() {
  try {
    const payload: any = { ...form }
    if (form.password) payload.password = await encryptPassword(form.password)
    else if (!form.id) payload.password = await encryptPassword('123456')
    else delete payload.password
    if (form.id) await http.put(`/admin/users/${form.id}`, payload); else await http.post('/admin/users', payload)
    ElMessage.success(t('保存成功')); dlg.value = false; load()
  } catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
}
async function del(u: any) {
  try { await ElMessageBox.confirm(t('确定删除用户「{name}」？', { name: u.name }), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  try { await http.delete(`/admin/users/${u.id}`); ElMessage.success(t('已删除')); detail.value = null; load() } catch (e: any) { ElMessage.error(e?.message || t('删除失败')) }
}
async function resetPwd(u: any) {
  try { await ElMessageBox.confirm(t('确定重置「{name}」的密码为 123456？', { name: u.name }), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  try { await http.put(`/admin/users/${u.id}/reset-password`, { newPassword: await encryptPassword('123456') }); ElMessage.success(t('已重置')) } catch (e: any) { ElMessage.error(e?.message || t('重置失败')) }
}

/* —— 加载（服务端分页唯一出口） —— */
async function load() {
  const seq = ++reqSeq
  loading.value = true
  loadError.value = ''
  try {
    const params: any = { page: page.value, size: pageSize.value }
    if (roleFilter.value) params.role = roleFilter.value
    if (collegeFilter.value) params.college = collegeFilter.value
    if (majorFilter.value) params.major = majorFilter.value
    if (gradeFilter.value) params.grade = gradeFilter.value
    if (classFilter.value) params.classNo = classFilter.value
    if (zsbFilter.value !== null) params.zsbOnly = zsbFilter.value
    if (kw.value.trim()) params.keyword = kw.value.trim()
    const r: any = await http.get('/admin/users', { params })
    if (seq !== reqSeq) return   // 丢弃过期响应，避免旧请求覆盖新结果
    const d = r?.data
    if (Array.isArray(d)) {
      // 旧后端（未重启）：不支持 keyword/role 参数 → 本地过滤 + 切片分页兼容
      let list: any[] = d
      if (roleFilter.value) list = list.filter(u => u.role === roleFilter.value)
      if (zsbFilter.value !== null) list = list.filter(u => String(u.major || '').includes('专升本') === zsbFilter.value)
      if (classFilter.value) list = list.filter(u => u.classNo === classFilter.value)
      if (collegeFilter.value) list = list.filter(u => u.college === collegeFilter.value)
      if (majorFilter.value) list = list.filter(u => u.major === majorFilter.value)
      if (gradeFilter.value) list = list.filter(u => u.grade === gradeFilter.value)
      if (kw.value.trim()) {
        const k = kw.value.trim().toLowerCase()
        list = list.filter(u => ((u.no || '') + (u.name || '') + (u.classNo || '') + (u.phone || '')).toLowerCase().includes(k))
      }
      users.value = list.slice((page.value - 1) * pageSize.value, page.value * pageSize.value)
      total.value = list.length
      counts.value = { all: list.length }
      classOpts.value = [...new Set(list.map(u => u.classNo).filter(Boolean))].sort() as string[]
      const cc: Record<string, number> = {}
      list.forEach(u => { if (u.classNo) cc[u.classNo] = (cc[u.classNo] || 0) + 1 })
      classCounts.value = cc
    } else {
      users.value = d?.items || []
      total.value = d?.total || 0
      if (d?.counts) counts.value = d.counts
      if (d?.classes) classOpts.value = d.classes
      if (d?.classCounts) classCounts.value = d.classCounts
      if (d?.tree) {
        treeData.value = d.tree
        if (!openNodes.value.size && d.tree.length) {
          const c0 = d.tree[0], m0 = c0.majors?.[0]
          const s = new Set<string>()
          s.add('c:' + c0.name)
          if (m0) s.add('m:' + c0.name + '/' + m0.name)
          openNodes.value = s
        }
      }
    }
  } catch (e: any) {
    if (seq !== reqSeq) return
    loadError.value = e?.message || t('加载失败')
    ElMessage.error(loadError.value)
  } finally {
    if (seq === reqSeq) loading.value = false
  }
}

onMounted(() => { load(); loadFilterOptions(); window.addEventListener('keydown', onGlobalKey) })
onBeforeUnmount(() => { window.removeEventListener('keydown', onGlobalKey) })
</script>

<style scoped>
/* ================= 局部主题：翠绿 #10b981 系，兼容项目深浅主题变量 ================= */
.um-page {
  --g: #10b981;
  --g-dark: #059669;
  --g-soft: rgba(16,185,129,.10);
  --g-soft2: rgba(16,185,129,.16);
  display: flex; flex-direction: column; gap: 16px; padding: 4px 0;
}
.ic { display: block; }

/* ---------- 顶部 sticky 导航栏 ---------- */
.um-topbar {
  position: sticky; top: 0; z-index: 40;
  display: flex; align-items: center; gap: 16px; flex-wrap: wrap;
  padding: 12px 16px;
  background: color-mix(in srgb, var(--card, #fff) 86%, transparent);
  backdrop-filter: saturate(1.4) blur(14px);
  -webkit-backdrop-filter: saturate(1.4) blur(14px);
  border: 0.5px solid var(--border, #e5e7eb); border-radius: 16px;
  box-shadow: 0 1px 2px rgba(16,24,40,.03), 0 8px 24px rgba(16,24,40,.04);
}
.um-brand { display: flex; align-items: center; gap: 12px; flex: 0 0 auto; }
.um-brand-icon {
  width: 40px; height: 40px; border-radius: 12px; flex: 0 0 auto;
  display: grid; place-items: center; color: #fff;
  background: linear-gradient(135deg, #34d399, var(--g-dark));
  box-shadow: 0 4px 12px rgba(16,185,129,.32);
}
.um-brand-icon .ic { width: 21px; height: 21px; }
.um-title { font-size: 17px; font-weight: 700; color: var(--text, #0f172a); line-height: 1.2; }
.um-subtitle { font-size: 12px; color: var(--text-lighter, #94a3b8); margin-top: 2px; }
.um-search-trigger {
  flex: 1 1 320px; max-width: 520px; min-width: 180px; height: 38px;
  display: flex; align-items: center; gap: 9px; padding: 0 10px 0 14px;
  border: 1px solid var(--border, #e5e7eb); border-radius: 999px;
  background: var(--card, #fff); color: var(--text-lighter, #94a3b8);
  cursor: pointer; transition: all .18s; font-family: inherit;
}
.um-search-trigger:hover { border-color: var(--g); box-shadow: 0 0 0 3px var(--g-soft); }
.um-search-trigger .ic { width: 17px; height: 17px; flex: 0 0 auto; }
.um-search-ph { flex: 1; text-align: left; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.um-kbd {
  font-family: inherit; font-size: 11px; line-height: 1; white-space: nowrap;
  padding: 4px 7px; border-radius: 6px; border: 1px solid var(--border, #e5e7eb);
  background: color-mix(in srgb, var(--card, #fff) 92%, #000 8%); color: var(--text-light, #64748b);
  flex: 0 0 auto;
}
.um-top-actions { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; margin-left: auto; }
.um-btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  height: 36px; padding: 0 14px; border-radius: 10px; font-family: inherit; font-size: 13px; font-weight: 600;
  cursor: pointer; transition: all .16s; white-space: nowrap; border: 1px solid transparent;
}
.um-btn .ic { width: 15px; height: 15px; }
.um-btn-solid { background: var(--g); color: #fff; box-shadow: 0 2px 8px rgba(16,185,129,.26); }
.um-btn-solid:hover { background: var(--g-dark); transform: translateY(-1px); }
.um-btn-outline { background: var(--card, #fff); color: var(--text, #334155); border-color: var(--border, #e5e7eb); }
.um-btn-outline:hover { border-color: var(--g); color: var(--g); }
.um-btn-ghost { background: transparent; color: var(--text-light, #64748b); height: 30px; padding: 0 10px; font-weight: 500; }
.um-btn-ghost:hover { background: var(--g-soft); color: var(--g); }
.um-btn-ghost.on { background: var(--g-soft); color: var(--g-dark); font-weight: 600; }
.um-btn-danger { background: transparent; color: #ef4444; border-color: rgba(239,68,68,.35); }
.um-btn-danger:hover { background: rgba(239,68,68,.08); }

/* ---------- 主体：左树 + 右主区 ---------- */
.um-layout { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 16px; align-items: start; }

/* ---------- 左侧组织树 ---------- */
.um-tree {
  border: 0.5px solid var(--border, #e5e7eb); border-radius: 14px; background: var(--card, #fff); overflow: hidden;
  box-shadow: 0 1px 2px rgba(16,24,40,.03); position: sticky; top: 82px;
}
.um-tree-h { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px 8px; font-size: 13px; color: var(--text); }
.um-tree-h b { font-weight: 700; }
.um-tree-h span { font-size: 11px; color: var(--text-lighter); }
.um-tree-search { display: flex; align-items: center; gap: 7px; margin: 2px 10px 10px; padding: 0 10px; height: 32px; border: 1px solid var(--border); border-radius: 9px; background: color-mix(in srgb, var(--card) 94%, #000 6%); }
.um-tree-search .ic { width: 14px; height: 14px; color: var(--text-lighter); flex: 0 0 auto; }
.um-tree-search input { flex: 1; min-width: 0; border: none; outline: none; background: transparent; color: var(--text); font-family: inherit; font-size: 12px; }
.um-tree-search input::placeholder { color: var(--text-lighter); }
.um-tree-body { max-height: calc(100vh - 210px); overflow-y: auto; padding: 2px 8px 10px; }
.um-node {
  display: flex; align-items: center; gap: 6px; width: 100%; text-align: left; border: none; background: none;
  font-family: inherit; font-size: 12.5px; color: var(--text-light); cursor: pointer; border-radius: 9px; padding: 8px 9px; transition: all .14s;
}
.um-node:hover { background: var(--g-soft); color: var(--g); }
.um-node.on { background: var(--g); color: #fff; font-weight: 600; }
.um-node.on .um-n { color: rgba(255,255,255,.95); background: rgba(255,255,255,.22); }
.um-node-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.um-n { flex: 0 0 auto; font-size: 10.5px; color: var(--text-lighter); background: color-mix(in srgb, var(--card) 90%, #000 10%); border-radius: 999px; padding: 1px 7px; font-variant-numeric: tabular-nums; }
.um-root { font-weight: 600; color: var(--text); }
.um-caret { width: 13px; display: inline-flex; flex: 0 0 auto; color: var(--text-lighter); transition: transform .16s; }
.um-caret .ic { width: 13px; height: 13px; }
.um-caret.open { transform: rotate(90deg); }
.um-leaves { padding-left: 10px; }
.um-leaf { font-size: 12px; }
.um-subleaves { padding-left: 12px; }
.um-leaf2 { font-size: 11.5px; padding: 6px 9px 6px 14px; }
.um-more { border: none; background: none; color: var(--g); font-family: inherit; font-size: 11.5px; cursor: pointer; padding: 6px 9px 6px 14px; text-align: left; width: 100%; border-radius: 8px; }
.um-more:hover { background: var(--g-soft); }
.um-tree-empty { padding: 18px 8px; text-align: center; font-size: 12px; color: var(--text-lighter); }

/* ---------- 右侧主区 ---------- */
.um-main { min-width: 0; display: flex; flex-direction: column; gap: 14px; }
.um-filtercard, .um-tablecard {
  border: 0.5px solid var(--border, #e5e7eb); border-radius: 14px; background: var(--card, #fff);
  box-shadow: 0 1px 2px rgba(16,24,40,.03);
}
.um-filtercard { padding: 12px 14px; }
.um-chip-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.um-chip-wrap { position: relative; }
.um-chip {
  display: inline-flex; align-items: center; gap: 6px; height: 32px; padding: 0 11px;
  border: 1px solid var(--border); border-radius: 999px; background: var(--card); color: var(--text-light);
  font-family: inherit; font-size: 12.5px; cursor: pointer; transition: all .16s;
}
.um-chip:hover { border-color: var(--g); color: var(--g); }
.um-chip.on { background: var(--g-soft); border-color: var(--g); color: var(--g-dark); font-weight: 600; }
.um-chip-label { white-space: nowrap; }
.um-chip-value { max-width: 140px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--g-dark); }
.um-chip-arrow { width: 13px; height: 13px; transition: transform .16s; }
.um-chip-arrow.open { transform: rotate(180deg); }
.um-reset { border: none; background: none; color: var(--text-lighter); font-family: inherit; font-size: 12.5px; cursor: pointer; padding: 6px 8px; }
.um-reset:hover { color: #ef4444; }
.um-dropdown {
  position: absolute; top: calc(100% + 6px); left: 0; z-index: 30; min-width: 190px; max-height: 320px; overflow-y: auto;
  padding: 6px; border: 0.5px solid var(--border); border-radius: 12px; background: var(--card);
  box-shadow: 0 12px 32px rgba(16,24,40,.14);
}
.um-opt {
  display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%;
  border: none; background: none; font-family: inherit; font-size: 12.5px; color: var(--text-light);
  cursor: pointer; padding: 8px 10px; border-radius: 8px; text-align: left;
}
.um-opt:hover { background: var(--g-soft); color: var(--g); }
.um-opt.on { background: var(--g-soft); color: var(--g-dark); font-weight: 600; }
.um-opt-n { margin-left: 8px; font-size: 11px; color: var(--text-lighter); font-variant-numeric: tabular-nums; }
.um-opt-check { color: var(--g); font-weight: 700; }

/* ---------- 表格卡片 ---------- */
.um-table-top { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 12px 16px; border-bottom: 0.5px solid var(--border); }
.um-scope { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text); min-width: 0; }
.um-scope b { font-weight: 700; color: var(--g-dark); }
.um-scope-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--g); box-shadow: 0 0 0 3px var(--g-soft); flex: 0 0 auto; }
.um-scope-n { font-size: 11.5px; color: var(--text-light); background: color-mix(in srgb, var(--card) 90%, #000 10%); padding: 2px 9px; border-radius: 999px; }
.um-table-actions { display: flex; align-items: center; gap: 4px; margin-left: auto; }
.um-rolebar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 10px 16px 0; }
.um-role-tab {
  display: inline-flex; align-items: center; gap: 7px; padding: 6px 12px; border: 1px solid var(--border);
  border-radius: 999px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12.5px;
  cursor: pointer; transition: all .18s;
}
.um-role-tab:hover { border-color: var(--g); color: var(--g); }
.um-role-tab.on { background: var(--g); border-color: var(--g); color: #fff; font-weight: 600; }
.um-role-tab span { font-size: 11px; padding: 1px 6px; border-radius: 999px; background: color-mix(in srgb, var(--card) 88%, #000 12%); color: var(--text-lighter); font-variant-numeric: tabular-nums; }
.um-role-tab.on span { color: rgba(255,255,255,.95); background: rgba(255,255,255,.22); }
.um-hint { padding: 8px 16px 4px; font-size: 11.5px; color: var(--text-lighter); }
.um-table-scroll { overflow-x: auto; }
.um-table { width: 100%; min-width: 820px; border-collapse: collapse; font-size: 12.5px; }
.um-table thead th {
  position: sticky; top: 0; z-index: 5;
  padding: 11px 14px; text-align: left; white-space: nowrap;
  font-size: 11.5px; font-weight: 600; letter-spacing: .02em; color: var(--text-lighter);
  background: color-mix(in srgb, var(--card, #fff) 82%, transparent);
  backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px);
  border-bottom: 0.5px solid var(--border);
}
.um-row { cursor: pointer; transition: background .13s; }
.um-row:hover { background: var(--g-soft); }
.um-row td { padding: 10px 14px; border-bottom: 0.5px solid var(--border); color: var(--text-light); vertical-align: middle; }
.um-row:last-child td { border-bottom: none; }
.um-no { color: var(--text); font-weight: 600; font-variant-numeric: tabular-nums; }
.um-name { color: var(--text); font-weight: 500; }
.um-status { display: inline-block; padding: 3px 9px; border-radius: 6px; font-size: 11.5px; font-weight: 600; white-space: nowrap; }
.um-status.status-approved { background: rgba(16,185,129,.13); color: #059669; }
.um-status.status-pending { background: rgba(59,130,246,.13); color: #2563eb; }
.um-status.status-warning { background: rgba(139,92,246,.13); color: #7c3aed; }
.um-grade { display: inline-block; padding: 2px 9px; border-radius: 999px; font-size: 11.5px; font-weight: 600; background: var(--g-soft); color: var(--g-dark); white-space: nowrap; }
.um-muted { color: var(--text-lighter); }
.um-check-col { width: 36px; text-align: center; }
.um-check-col input { accent-color: var(--g); }
.col-op { width: 108px; }
.um-op-icons { display: flex; align-items: center; gap: 4px; opacity: 0; transition: opacity .15s; }
.um-row:hover .um-op-icons, .um-op-icons:focus-within { opacity: 1; }
.um-icon-btn {
  width: 28px; height: 28px; display: grid; place-items: center; border: 0.5px solid var(--border); border-radius: 8px;
  background: var(--card); color: var(--text-light); cursor: pointer; transition: all .14s;
}
.um-icon-btn .ic { width: 14px; height: 14px; }
.um-icon-btn:hover { border-color: var(--g); color: var(--g); background: var(--g-soft); }
.um-icon-btn.danger:hover { border-color: #ef4444; color: #ef4444; background: rgba(239,68,68,.08); }

/* ---------- 加载 / 错误 / 空结果态 ---------- */
.um-state { text-align: center; padding: 40px 20px; color: var(--text-light); font-size: 13px; }
.um-state.err { color: #ef4444; }
.um-empty { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 46px 20px 50px; }
.um-empty-icon { width: 56px; height: 56px; border-radius: 16px; display: grid; place-items: center; background: var(--g-soft); color: var(--g); margin-bottom: 14px; }
.um-empty-icon .ic { width: 28px; height: 28px; }
.um-empty-title { font-size: 15px; font-weight: 700; color: var(--text); }
.um-empty-sub { font-size: 12.5px; color: var(--text-lighter); margin-top: 5px; }
.um-suggest { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; justify-content: center; margin-top: 18px; }
.um-suggest-label { font-size: 12px; color: var(--text-light); }
.um-suggest-chip { border: 1px solid var(--border); background: var(--card); color: var(--g-dark); font-family: inherit; font-size: 12.5px; padding: 6px 12px; border-radius: 999px; cursor: pointer; transition: all .15s; }
.um-suggest-chip:hover { border-color: var(--g); background: var(--g-soft); }
.um-empty-reset { margin-top: 18px; border: none; background: none; color: var(--g); font-family: inherit; font-size: 12.5px; cursor: pointer; text-decoration: underline; text-underline-offset: 3px; }

/* ---------- 批量操作条 ---------- */
.um-batch { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; padding: 10px 16px; border-top: 0.5px solid var(--border); font-size: 12.5px; color: var(--text); }
.um-batch b { color: var(--g-dark); }

/* ---------- 分页 ---------- */
.um-pagination { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 12px 16px; border-top: 0.5px solid var(--border); }
.um-pg-info { font-size: 12px; color: var(--text-light); font-variant-numeric: tabular-nums; }
.um-pg-right { display: flex; align-items: center; gap: 8px; margin-left: auto; flex-wrap: wrap; }
.um-pg-size { font-size: 12px; color: var(--text-light); }
.um-pg-select { padding: 5px 8px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); color: var(--text); font-family: inherit; font-size: 12px; }
.um-pg-btns { display: flex; align-items: center; gap: 4px; }
.um-pg-btn { min-width: 30px; height: 30px; padding: 0 7px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12.5px; cursor: pointer; transition: all .14s; }
.um-pg-btn:hover:not(:disabled) { border-color: var(--g); color: var(--g); }
.um-pg-btn.active { background: var(--g); border-color: var(--g); color: #fff; font-weight: 700; }
.um-pg-btn:disabled { opacity: .4; cursor: not-allowed; }
.um-pg-dots { color: var(--text-lighter); padding: 0 2px; }
.um-jump { display: inline-flex; align-items: center; gap: 5px; font-size: 12px; color: var(--text-light); }
.um-jump input { width: 52px; padding: 5px 6px; border: 1px solid var(--border); border-radius: 6px; background: var(--card); color: var(--text); font-family: inherit; font-size: 12px; }

/* ---------- 命令面板 ---------- */
.cmd-overlay {
  position: fixed; inset: 0; z-index: 4000; display: flex; justify-content: center; align-items: flex-start;
  padding: 14vh 16px 16px; background: rgba(15,23,42,.42);
  backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); animation: cmdFade .16s ease;
}
.cmd-panel {
  width: 100%; max-width: 600px; max-height: 68vh; display: flex; flex-direction: column;
  border: 0.5px solid var(--border); border-radius: 16px; overflow: hidden; background: var(--card);
  box-shadow: 0 24px 70px rgba(15,23,42,.34); animation: cmdUp .2s cubic-bezier(.16,1,.3,1);
}
.cmd-input-row { display: flex; align-items: center; gap: 11px; padding: 14px 16px; border-bottom: 0.5px solid var(--border); }
.cmd-input-row .ic { width: 19px; height: 19px; color: var(--g); flex: 0 0 auto; }
.cmd-input-row input { flex: 1; min-width: 0; border: none; outline: none; background: transparent; color: var(--text); font-family: inherit; font-size: 15px; }
.cmd-input-row input::placeholder { color: var(--text-lighter); }
.cmd-results { flex: 1; overflow-y: auto; padding: 8px; }
.cmd-group + .cmd-group { margin-top: 6px; }
.cmd-group-h { display: flex; align-items: center; gap: 7px; padding: 6px 10px; font-size: 11px; font-weight: 700; letter-spacing: .04em; color: var(--text-lighter); text-transform: uppercase; }
.cmd-group-h span { font-weight: 500; }
.cmd-item {
  display: flex; align-items: center; gap: 11px; width: 100%; text-align: left;
  border: none; background: none; font-family: inherit; cursor: pointer; padding: 9px 10px; border-radius: 10px; color: var(--text);
}
.cmd-item.active { background: var(--g-soft); }
.cmd-item-ico { width: 32px; height: 32px; border-radius: 9px; display: grid; place-items: center; flex: 0 0 auto; }
.cmd-item-ico .ic { width: 17px; height: 17px; }
.cmd-item-ico.user { background: rgba(16,185,129,.13); color: var(--g); }
.cmd-item-ico.org { background: rgba(59,130,246,.13); color: #3b82f6; }
.cmd-item-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.cmd-item-main b { font-size: 13px; font-weight: 600; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-item-main small { font-size: 11px; color: var(--text-lighter); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-enter { color: var(--g); font-size: 14px; flex: 0 0 auto; }
.cmd-empty { padding: 36px 16px; text-align: center; color: var(--text-lighter); font-size: 13px; }
.cmd-footer { display: flex; align-items: center; gap: 16px; padding: 10px 16px; border-top: 0.5px solid var(--border); background: color-mix(in srgb, var(--card) 94%, #000 6%); font-size: 11px; color: var(--text-lighter); }
.cmd-footer span { display: inline-flex; align-items: center; gap: 4px; }
.cmd-footer .um-kbd { padding: 2px 6px; }
.cmd-footer-tip { margin-left: auto; color: var(--g); }
@keyframes cmdFade { from { opacity: 0 } to { opacity: 1 } }
@keyframes cmdUp { from { opacity: 0; transform: translateY(-10px) scale(.985) } to { opacity: 1; transform: none } }

/* ---------- 详情抽屉 ---------- */
.um-mask { position: fixed !important; inset: 0; background: rgba(0,0,0,.42); z-index: 3500 !important; display: flex; justify-content: flex-end; animation: cmdFade .16s ease; }
.um-drawer { width: 400px; max-width: 92vw; height: 100%; background: var(--card); border-left: 0.5px solid var(--border); display: flex; flex-direction: column; animation: drawerIn .22s cubic-bezier(.16,1,.3,1); }
@keyframes drawerIn { from { transform: translateX(24px); opacity: .6 } to { transform: none; opacity: 1 } }
.um-drawer-h { display: flex; align-items: center; padding: 18px 20px; border-bottom: 0.5px solid var(--border); }
.um-drawer-h b { font-size: 16px; color: var(--text); }
.um-drawer-h span { display: block; font-size: 12px; color: var(--text-lighter); margin-top: 3px; }
.um-close { margin-left: auto; border: none; background: none; font-size: 24px; color: var(--text-light); cursor: pointer; line-height: 1; }
.um-drawer-body { flex: 1; overflow-y: auto; padding: 12px 20px; }
.um-field { display: flex; justify-content: space-between; gap: 12px; padding: 11px 0; border-bottom: 0.5px solid var(--border); font-size: 12.5px; }
.um-field span { color: var(--text-lighter); flex: 0 0 auto; }
.um-field b { color: var(--text); font-weight: 500; text-align: right; word-break: break-all; }
.um-drawer-f { display: flex; gap: 8px; flex-wrap: wrap; padding: 14px 20px; border-top: 0.5px solid var(--border); }

@media (max-width: 1100px) {
  .um-search-trigger { order: 3; flex-basis: 100%; max-width: none; }
  .um-top-actions { order: 2; }
}
@media (max-width: 860px) {
  .um-layout { grid-template-columns: 1fr; }
  .um-tree { display: none; }
  .um-tree-body { max-height: 220px; }
}
@media (max-width: 560px) {
  .um-topbar { padding: 12px; }
  .um-btn-outline span { display: none; }
  .um-kbd { display: none; }
  .um-pg-right { margin-left: 0; width: 100%; justify-content: flex-end; }
}
</style>
