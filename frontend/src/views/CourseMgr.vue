<template>
  <div class="cm-page">
    <!-- ================= 顶部 sticky 导航栏 ================= -->
    <header class="cm-topbar">
      <div class="cm-brand">
        <div class="cm-brand-icon">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v17.5H6.5A2.5 2.5 0 0 0 4 22z"/><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/>
          </svg>
        </div>
        <div class="cm-brand-text">
          <div class="cm-title">{{ t('课程管理') }}</div>
          <div class="cm-subtitle">{{ t('共 {n} 门课程', { n: allCount }) }}</div>
        </div>
      </div>

      <button class="cm-search-trigger" @click="openCommand" :aria-label="t('打开搜索')">
        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
        <span class="cm-search-ph">{{ filters.keyword || t('搜索课程名 / 代码 / 教师，支持拼音首字母…') }}</span>
        <kbd class="cm-kbd">{{ isMac ? '⌘' : 'Ctrl+' }} K</kbd>
      </button>

      <div class="cm-top-actions">
        <button class="cm-btn cm-btn-outline" @click="pickAndImport('courses', load)">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M12 16V4m0 0L7 9m5-5 5 5"/><path d="M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3"/></svg>
          <span>{{ t('导入 Excel') }}</span>
        </button>
        <button class="cm-btn cm-btn-solid" @click="openDialog(null)">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"><path d="M12 5v14M5 12h14"/></svg>
          <span>{{ t('新增课程') }}</span>
        </button>
      </div>
    </header>

    <div class="cm-layout">
      <!-- ================= 左侧分类树（240px，移动端隐藏） ================= -->
      <aside class="cm-tree">
        <div class="cm-tree-h"><b>{{ t('课程分类') }}</b><span>{{ visibleTree.length }} {{ t('类') }}</span></div>
        <div class="cm-tree-search">
          <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/></svg>
          <input v-model="treeKw" :placeholder="t('过滤分类 / 学院')" />
        </div>
        <div class="cm-tree-body">
          <button class="cm-node cm-root" :class="{ on: !categoryFilter && !collegeFilter }" @click="selectTree(null, null)">
            <span class="cm-node-label">{{ t('全部课程') }}</span><span class="cm-n">{{ allCount }}</span>
          </button>
          <div v-for="cat in visibleTree" :key="cat.name" class="cm-cat">
            <button class="cm-node" :class="{ on: categoryFilter === cat.name && !collegeFilter }" @click="clickCat(cat)">
              <span class="cm-caret" :class="{ open: isOpen('cat:' + cat.name) }">
                <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="m9 6 6 6-6 6"/></svg>
              </span>
              <span class="cm-node-label">{{ cat.name }}</span><span class="cm-n">{{ cat.count }}</span>
            </button>
            <div v-if="isOpen('cat:' + cat.name)" class="cm-leaves">
              <button v-for="col in cat.colleges" :key="col.name"
                      class="cm-node cm-leaf" :class="{ on: categoryFilter === cat.name && collegeFilter === (col.name === '全校公共课' ? '__none__' : col.name) }"
                      @click="selectTree(cat.name, col.name === '全校公共课' ? '__none__' : col.name)">
                <span class="cm-node-label">{{ col.name }}</span><span class="cm-n">{{ col.count }}</span>
              </button>
            </div>
          </div>
          <div v-if="!visibleTree.length" class="cm-tree-empty">{{ t('没有匹配的分类节点') }}</div>
        </div>
      </aside>

      <!-- ================= 右侧主区 ================= -->
      <main class="cm-main">
        <!-- 筛选轨道卡片：五个胶囊 chip，读写同一个 filters 状态对象 -->
        <section class="cm-filtercard">
          <div class="cm-chip-row">
            <div v-for="chip in chipDefs" :key="chip.key" class="cm-chip-wrap">
              <button class="cm-chip" :class="{ on: chip.active }" @click="toggleChip(chip.key)">
                <span class="cm-chip-label">{{ chip.label }}</span>
                <span v-if="chip.value" class="cm-chip-value">{{ chip.value }}</span>
                <svg class="ic cm-chip-arrow" :class="{ open: openChip === chip.key }" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><path d="m6 9 6 6 6-6"/></svg>
              </button>
              <div v-if="openChip === chip.key" class="cm-dropdown" @click.stop>
                <button class="cm-opt" :class="{ on: !chip.current }" @click="chip.clear(); openChip = null">
                  <span>{{ t('全部') }}{{ chip.label }}</span><span v-if="!chip.current" class="cm-opt-check">✓</span>
                </button>
                <button v-for="o in chip.options" :key="String(o.value)" class="cm-opt" :class="{ on: chip.current === o.value }" @click="chip.set(o.value); openChip = null">
                  <span>{{ o.label }}<span v-if="o.count !== undefined" class="cm-opt-n">{{ o.count }}</span></span>
                  <span v-if="chip.current === o.value" class="cm-opt-check">✓</span>
                </button>
              </div>
            </div>
            <button v-if="hasFilter" class="cm-reset" @click="clearFilters">{{ t('清空') }}</button>
          </div>
        </section>

        <!-- 数据表格卡片 -->
        <section class="cm-tablecard">
          <div class="cm-table-top">
            <div class="cm-scope">
              <span class="cm-scope-dot"></span>
              <b>{{ scopeLabel }}</b>
              <span class="cm-scope-n">{{ filtered.length }} {{ t('门') }}</span>
            </div>
            <div class="cm-table-actions">
              <button class="cm-btn cm-btn-ghost" @click="downloadTemplate('courses')">{{ t('模板') }}</button>
              <button class="cm-btn cm-btn-ghost" @click="exportCurrent">{{ t('导出') }}</button>
              <button class="cm-btn cm-btn-ghost" @click="prereqVisible = true">🔗 {{ t('先修链') }}</button>
            </div>
          </div>

          <div class="cm-table-scroll">
            <table class="cm-table">
              <thead>
                <tr>
                  <th class="col-name">{{ t('课程名') }}</th>
                  <th>{{ t('类别') }}</th>
                  <th class="col-credit">{{ t('学分') }}</th>
                  <th>{{ t('教师') }}</th>
                  <th class="col-cap">{{ t('容量 / 已报') }}</th>
                  <th>{{ t('状态') }}</th>
                  <th class="col-op">{{ t('操作') }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="c in paged" :key="c.id" class="cm-row" @click="detail = c">
                  <td>
                    <div class="cm-course-name">{{ c.name }}</div>
                    <div v-if="c.courseCode" class="cm-course-code">{{ c.courseCode }}</div>
                  </td>
                  <td><span class="cm-cat-tag" :class="'cat-' + c.category">{{ c.category }}</span></td>
                  <td class="col-credit">{{ c.credit }}</td>
                  <td>
                    <button v-if="c.teacherId" class="cm-teacher-link" @click.stop="filterByTeacher(c.teacherId, c.teacherName)">{{ c.teacherName || c.teacher || t('未分配') }}</button>
                    <span v-else class="cm-muted">{{ t('未分配') }}</span>
                  </td>
                  <td class="col-cap">
                    <div class="cm-cap-num"><b>{{ c.stat?.total || 0 }}</b><span> / {{ c.capacity }}</span></div>
                    <div class="cm-bar"><span :class="'bar-' + barClass(c)" :style="{ width: Math.min(100, ratio(c) * 100) + '%' }"></span></div>
                  </td>
                  <td><span class="cm-status" :class="'st-' + statusText(c)">{{ statusText(c) }}</span></td>
                  <td class="col-op">
                    <div class="cm-op-icons">
                      <button class="cm-icon-btn" :title="t('编辑')" @click.stop="editFromDrawer(c)">
                        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L8 18l-4 1 1-4Z"/></svg>
                      </button>
                      <button class="cm-icon-btn" :title="t('先修链')" @click.stop="openPrereqFor(c)">
                        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M10 13a5 5 0 0 0 7.5.5l3-3a5 5 0 0 0-7-7l-1.5 1.5"/><path d="M14 11a5 5 0 0 0-7.5-.5l-3 3a5 5 0 0 0 7 7l1.5-1.5"/></svg>
                      </button>
                      <button class="cm-icon-btn danger" :title="t('删除')" @click.stop="del(c)">
                        <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M3 6h18M8 6V4h8v2m-9 0 1 14h8l1-14"/></svg>
                      </button>
                    </div>
                  </td>
                </tr>
              </tbody>
            </table>

            <!-- 空结果态：SearchX + Dice 纠错建议 -->
            <div v-if="!filtered.length" class="cm-empty">
              <div class="cm-empty-icon">
                <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round"><circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4.5 4.5"/><path d="M8 10.5h5"/></svg>
              </div>
              <div class="cm-empty-title">{{ t('没有找到匹配的课程') }}</div>
              <div class="cm-empty-sub">{{ t('试试换个关键词，或看看下面的相似课程') }}</div>
              <div v-if="suggestChips.length" class="cm-suggest">
                <span class="cm-suggest-label">{{ t('是不是想找') }}</span>
                <button v-for="s in suggestChips" :key="s" class="cm-suggest-chip" @click="filters.keyword = s">{{ s }}</button>
              </div>
              <button class="cm-empty-reset" @click="clearFilters">{{ t('清空筛选') }}</button>
            </div>
          </div>

          <!-- 分页 -->
          <div class="cm-pagination">
            <span class="cm-pg-info">{{ t('第 {a}-{b} 条，共 {n} 条', { a: rangeStart, b: rangeEnd, n: filtered.length }) }}</span>
            <div class="cm-pg-right">
              <span class="cm-pg-size">{{ t('每页') }}</span>
              <select v-model.number="pageSize" class="cm-pg-select"><option v-for="n in [20,50,100]" :key="n" :value="n">{{ n }}</option></select>
              <div class="cm-pg-btns">
                <button class="cm-pg-btn" :disabled="page <= 1" @click="page--">‹</button>
                <template v-for="(p, i) in pageItems" :key="i">
                  <span v-if="p === '…'" class="cm-pg-dots">…</span>
                  <button v-else class="cm-pg-btn" :class="{ active: p === page }" @click="page = p as number">{{ p }}</button>
                </template>
                <button class="cm-pg-btn" :disabled="page >= totalPages" @click="page++">›</button>
              </div>
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
            <input ref="cmdInput" v-model="cmdTerm" :placeholder="t('搜索课程名 / 代码 / 教师，支持拼音首字母…')" />
            <kbd class="cm-kbd">esc</kbd>
          </div>
          <div class="cmd-results">
            <template v-if="cmdItems.length">
              <div v-if="cmdCourses.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('课程') }} <span>{{ cmdCourses.length }}</span></div>
                <button v-for="(c, i) in cmdCourses" :key="'c'+c.id"
                        class="cmd-item" :class="{ active: cmdCursor === i }"
                        @mouseenter="cmdCursor = i" @click="selectCmd(cmdItems[i])">
                  <span class="cmd-item-ico course">
                    <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M4 4.5A2.5 2.5 0 0 1 6.5 2H20v17.5H6.5A2.5 2.5 0 0 0 4 22z"/><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/></svg>
                  </span>
                  <span class="cmd-item-main"><b>{{ c.name }}</b><small>{{ c.courseCode || '—' }} · {{ c.teacherName || t('未分配') }} · {{ c.category }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === i">↵</span>
                </button>
              </div>
              <div v-if="cmdTeachers.length" class="cmd-group">
                <div class="cmd-group-h">{{ t('教师') }} <span>{{ cmdTeachers.length }}</span></div>
                <button v-for="(tc, j) in cmdTeachers" :key="'t'+tc.id"
                        class="cmd-item" :class="{ active: cmdCursor === cmdCourses.length + j }"
                        @mouseenter="cmdCursor = cmdCourses.length + j" @click="selectCmd(cmdItems[cmdCourses.length + j])">
                  <span class="cmd-item-ico teacher">
                    <svg class="ic" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/></svg>
                  </span>
                  <span class="cmd-item-main"><b>{{ tc.name }}</b><small>{{ tc.no || '—' }} · {{ tc.college || t('教师') }} · {{ teacherCourseCount(tc.id) }} {{ t('门课') }}</small></span>
                  <span class="cmd-enter" v-if="cmdCursor === cmdCourses.length + j">↵</span>
                </button>
              </div>
            </template>
            <div v-else class="cmd-empty">{{ t('没有联想结果，试试拼音首字母') }}</div>
          </div>
          <div class="cmd-footer">
            <span><kbd class="cm-kbd">↑</kbd><kbd class="cm-kbd">↓</kbd> {{ t('导航') }}</span>
            <span><kbd class="cm-kbd">↵</kbd> {{ t('选中') }}</span>
            <span class="cmd-footer-tip">{{ t('选中教师即加入筛选') }}</span>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ================= 课程详情抽屉 ================= -->
    <Teleport to="body">
      <div v-if="detail" class="cm-mask" @click="detail = null">
        <div class="cm-drawer" @click.stop>
          <div class="cm-drawer-h">
            <div><b>{{ detail.name }}</b><span>{{ detail.courseCode || '—' }} · {{ detail.category }}</span></div>
            <button class="cm-close" @click="detail = null">×</button>
          </div>
          <div class="cm-drawer-body">
            <div v-for="f in detailFields" :key="f.label" class="cm-field"><span>{{ f.label }}</span><b>{{ f.value || '—' }}</b></div>
            <div v-if="detail.intro" class="cm-intro">
              <div class="cm-intro-h">{{ t('课程简介') }}</div>
              <div class="cm-intro-body">{{ detail.intro }}</div>
            </div>
          </div>
          <div class="cm-drawer-f">
            <button class="cm-btn cm-btn-solid" @click="editFromDrawer(detail)">{{ t('编辑') }}</button>
            <button class="cm-btn cm-btn-outline" @click="openPrereqFor(detail)">{{ t('先修链') }}</button>
            <button class="cm-btn cm-btn-danger" @click="del(detail)">{{ t('删除') }}</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- ================= 先修链依赖图 ================= -->
    <el-dialog v-model="prereqVisible" :title="t('课程先修链')" width="640px">
      <div v-if="prereqPairs.length" class="pr-graph">
        <svg viewBox="0 0 560 200" class="pr-svg">
          <defs>
            <marker id="pr-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
              <path d="M0,0 L8,4 L0,8 Z" fill="#10b981" />
            </marker>
          </defs>
          <g v-for="(n, i) in prereqNodes" :key="'l' + i">
            <rect :x="30" :y="n.ly - 16" width="180" height="32" rx="8" class="pr-node" />
            <text :x="120" :y="n.ly + 5" text-anchor="middle" class="pr-label">{{ n.preName }}</text>
          </g>
          <g v-for="(n, i) in prereqNodes" :key="'r' + i">
            <rect :x="350" :y="n.ry - 16" width="180" height="32" rx="8" class="pr-node dep" />
            <text :x="440" :y="n.ry + 5" text-anchor="middle" class="pr-label">{{ n.depName }}</text>
            <line :x1="212" :y1="n.ly" :x2="346" :y2="n.ry" class="pr-edge" marker-end="url(#pr-arrow)" />
          </g>
        </svg>
        <div class="pr-tip">{{ t('箭头方向 = 先修 → 后续；未通过先修课的学生无法选修箭头指向的课程') }}</div>
      </div>
      <div v-else class="pr-empty">{{ t('尚未配置任何先修关系——在课程编辑中为课程设置「先修课程」即可建立依赖链') }}</div>
    </el-dialog>

    <!-- ================= 新增 / 编辑课程 ================= -->
    <el-dialog v-model="dlg" :title="form.id ? t('编辑课程') : t('新增课程')" width="520px">
      <el-form :model="form" label-position="top">
        <el-row :gutter="12">
          <el-col :span="24"><el-form-item :label="t('课程名称')"><el-input v-model="form.name" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('课程代码')"><el-input v-model="form.courseCode" :placeholder="t('如 CS1001')" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('学时')"><el-input-number v-model.number="form.hours" :min="1" :max="200" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('类别')">
            <el-select v-model="form.category" style="width:100%"><el-option v-for="c in CATEGORIES" :key="c" :label="c" :value="c" /></el-select>
          </el-form-item></el-col>
          <el-col :span="12" v-if="(form.category || '').includes('必修')"><el-form-item :label="t('开课学期（培养方案）')">
            <el-select v-model="form.semesterNo" clearable :placeholder="t('第几学期开课')" style="width:100%">
              <el-option v-for="n in 8" :key="n" :label="semesterTitle(n) + '（第 ' + n + ' 学期）'" :value="n" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('学分')"><el-input-number v-model.number="form.credit" :min="1" :max="6" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('授课教师')">
            <el-select v-model="form.teacherId" filterable :placeholder="t('选择教师')" style="width:100%">
              <el-option v-for="tc in teachers" :key="tc.id"
              :label="tc.name + ' (' + tc.no + ')' + (tc.college ? ' · ' + tc.college : '') + ' · 本学期 ' + (teacherLoad[tc.id] || 0) + '/' + teacherMax + ' 门' + ((teacherLoad[tc.id] || 0) >= teacherMax && form.teacherId !== tc.id ? ' · 已满' : '')"
              :disabled="(teacherLoad[tc.id] || 0) >= teacherMax && form.teacherId !== tc.id"
              :value="tc.id" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('容量')"><el-input-number v-model.number="form.capacity" :min="5" :max="500" style="width:100%" /></el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('先修课程')">
            <el-select v-model="form.prereqId" clearable filterable :placeholder="t('无（未通过先修不可选本课）')" style="width:100%">
              <el-option v-for="c2 in list.filter(x => x.id !== form.id)" :key="c2.id" :label="c2.name" :value="c2.id" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('适用学院（留空=全校公共课）')">
            <el-select v-model="form.targetCollege" filterable allow-create default-first-option clearable :placeholder="t('选择或输入学院')" style="width:100%">
              <el-option v-for="c in COLLEGES" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('适用专业（留空=全院通用，多个用 | 分隔）')">
            <el-input v-model="form.targetMajor" :placeholder="t('如 数据科学与大数据技术|物联网工程')" />
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('上课时间')">
            <el-select v-model="form.time" filterable allow-create default-first-option clearable
                       popper-class="course-time-popper"
                       :placeholder="t('选择标准排课时段（第1-10节，用于冲突检测与课表）')" style="width:100%">
              <el-option-group v-for="g in timeOptionGroups" :key="g.label" :label="t(g.label)">
                <el-option v-for="opt in g.options" :key="opt.value" :label="opt.label" :value="opt.value">
                  <div class="to-row"><span class="to-main">{{ opt.value }}</span><span class="to-meta">{{ opt.meta }}</span></div>
                </el-option>
              </el-option-group>
            </el-select>
            <div v-if="scheduleHint" class="time-hint" :class="{ warn: scheduleWarn }" :title="form.time || ''">
              <div>{{ scheduleHint }}</div><div v-if="form.time" class="time-hint-value">{{ form.time }}</div>
            </div>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('教室')">
            <el-select v-model="form.location" filterable clearable :placeholder="t('从教室管理中选择教室')" style="width:100%">
              <el-option v-for="r in roomOptions" :key="r" :label="r" :value="r" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('校区')">
            <el-select v-model="form.campus" filterable allow-create default-first-option clearable style="width:100%">
              <el-option v-for="cp in CAMPUS_OPTS" :key="cp" :label="cp" :value="cp" />
            </el-select>
          </el-form-item></el-col>
          <el-col :span="12"><el-form-item :label="t('考试时间')"><el-input v-model="form.examTime" :placeholder="t('留空按上课时间')" /></el-form-item></el-col>
          <el-col :span="24"><el-form-item :label="t('课程描述')"><el-input v-model="form.intro" type="textarea" :rows="2" /></el-form-item></el-col>
        </el-row>
      </el-form>
      <template #footer>
        <button class="cm-btn cm-btn-ghost" @click="dlg = false">{{ t('取消') }}</button>
        <button class="cm-btn cm-btn-solid" @click="save">{{ t('保存') }}</button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, toRefs, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import http from '@/api'
import { t } from '@/i18n'
import { semesterTitle } from '@/utils/semester'
import { COLLEGES } from '@/utils/colleges'
import { downloadTemplate, pickAndImport } from '@/utils/xlsxImport'
import { buildTimeOptionGroups, describeSchedule } from '@/utils/classSchedule'
import { suggestSimilar } from '@/utils/similarity'
import { initialsOf } from '@/utils/pinyin'

const CATEGORIES = ['公共必修', '专业必修', '专业选修', '公共选修']
const CAMPUS_OPTS = ['主校区', '东校区', '西校区']
const STATUS_OPTS = ['正常', '已满', '报名超额']
const isMac = typeof navigator !== 'undefined' && /Mac|iPhone|iPad/.test(navigator.platform)

/* ============================================================
 * 单一状态源：所有筛选（分类树 / 筛选轨道 chips / 命令面板 / 表格教师链接）
 * 都读写这同一个 filters 对象，绝不出现两套筛选逻辑。
 * 用 toRefs 拆出 ref，保持下方原有逻辑的 .value 写法不变。
 * ============================================================ */
const filters = reactive({
  keyword: '' as string,
  category: null as string | null,
  college: null as string | null,       // '__none__' = 全校公共课
  major: null as string | null,
  semester: null as number | null,
  teacherId: null as number | null,
  status: null as string | null,
  credits: null as number | null,
})
const { keyword: kw, category: categoryFilter, college: collegeFilter, major: majorFilter, semester: semesterFilter, teacherId: teacherFilter, status: statusFilter, credits: creditsFilter } = toRefs(filters)

const pageSize = ref(20)
const page = ref(1)
const detail = ref<any>(null)

/* —— 左侧树 —— */
const treeKw = ref('')
const openNodes = ref<Set<string>>(new Set(['cat:公共必修', 'cat:专业必修']))

/* —— 数据 —— */
const list = ref<any[]>([])
const teachers = ref<any[]>([])
const rooms = ref<string[]>([])
const allCount = computed(() => list.value.length)

/* 教师课时负载：同一学期内课程门数（平行班算一门），上限 teacherMaxCourses */
const teacherMax = ref(3)
const curSemester = ref<number>(0)
const teacherLoad = computed<Record<number, number>>(() => {
  const sem = form.semesterNo || semesterFilter.value || curSemester.value || null
  const seen: Record<number, Set<string>> = {}
  for (const c of list.value) {
    if (!c.teacherId) continue
    if (sem && c.semesterNo !== sem) continue
    const set = seen[c.teacherId] || (seen[c.teacherId] = new Set<string>())
    set.add(c.name || String(c.id))
  }
  const out: Record<number, number> = {}
  for (const k of Object.keys(seen)) out[k] = seen[k].size
  return out
})
function teacherCourseCount(id: number) { return teacherLoad.value[id] || 0 }

/* —— 选项（从真实数据提取） —— */
const collegeOpts = computed(() => {
  const s = new Set<string>()
  for (const c of list.value) { const col = (c.targetCollege || '').trim(); if (col && COLLEGES.includes(col)) s.add(col) }
  return [...s].sort((a, b) => a.localeCompare(b, 'zh'))
})
const majorOpts = computed(() => {
  const s = new Set<string>()
  for (const c of list.value) { const mj = (c.targetMajor || '').trim(); if (!mj) continue; for (const m of mj.split('|').map((x: string) => x.trim()).filter(Boolean)) s.add(m) }
  return [...s].sort((a, b) => a.localeCompare(b, 'zh'))
})
const semesterOpts = computed(() => [
  ...Array.from({ length: 8 }, (_, i) => ({ value: i + 1, label: semesterTitle(i + 1) + `（第 ${i + 1} 学期）` })),
  { value: -1, label: t('未排期') },
])

/* —— 左侧分类树：类别 → 适用学院，计数基于全量（不受其他筛选影响，读起来稳定） —— */
const visibleTree = computed(() => {
  const q = treeKw.value.trim().toLowerCase()
  return CATEGORIES.map(cat => {
    const courses = list.value.filter(c => c.category === cat)
    const colMap = new Map<string, number>()
    for (const c of courses) {
      const raw = (c.targetCollege || '').trim()
      const col = !raw ? '全校公共课' : (COLLEGES.includes(raw) ? raw : '专业专属/未标注')
      colMap.set(col, (colMap.get(col) || 0) + 1)
    }
    const colleges = [...colMap.entries()].map(([name, count]) => ({ name, count })).sort((a, b) => b.count - a.count)
      .filter(x => !q || cat.toLowerCase().includes(q) || x.name.toLowerCase().includes(q))
    return { name: cat, count: courses.length, colleges }
  }).filter(x => x.colleges.length)
})

/* —— 状态口径 —— */
function statusText(c: any): string {
  if (c.oversubscribed) return '报名超额'
  if (c.full) return '已满'
  return '正常'
}
function ratio(c: any): number { const cap = c.capacity || 0; return cap ? (c.stat?.total || 0) / cap : 0 }
function barClass(c: any): string { const r = ratio(c); return r >= 1 ? 'over' : r >= 0.7 ? 'mid' : 'ok' }

/* —— 文本 / 拼音首字母匹配（命令面板 + 全局关键词） —— */
function matchText(haystack: string, py: string, q: string): boolean {
  const ql = q.toLowerCase().replace(/\s+/g, '')
  if (!ql) return true
  if ((haystack || '').toLowerCase().replace(/\s+/g, '').includes(ql)) return true
  return !!py && py.includes(ql)
}

/* —— 过滤 + 分页（唯一分页/过滤出口） —— */
const filtered = computed(() => {
  const k = kw.value.trim()
  return list.value.filter(c => {
    if (categoryFilter.value && c.category !== categoryFilter.value) return false
    if (collegeFilter.value === '__none__') { if ((c.targetCollege || '').trim()) return false }
    else if (collegeFilter.value && (c.targetCollege || '').trim() !== collegeFilter.value) return false
    if (majorFilter.value) { const mj = c.targetMajor || ''; if (!mj.split('|').map((x: string) => x.trim()).includes(majorFilter.value)) return false }
    if (semesterFilter.value !== null) {
      if (semesterFilter.value === -1) { if (c.semesterNo != null) return false }
      else if (c.semesterNo !== semesterFilter.value) return false
    }
    if (teacherFilter.value !== null && c.teacherId !== teacherFilter.value) return false
    if (statusFilter.value && statusText(c) !== statusFilter.value) return false
    if (creditsFilter.value !== null && c.credit !== creditsFilter.value) return false
    if (k && !matchText(`${c.name || ''} ${c.courseCode || ''} ${c.teacherName || c.teacher || ''}`, c._py || '', k)) return false
    return true
  })
})
const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)))
const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value))
const rangeStart = computed(() => filtered.value.length ? (page.value - 1) * pageSize.value + 1 : 0)
const rangeEnd = computed(() => Math.min(page.value * pageSize.value, filtered.value.length))
const pageItems = computed<(number | '…')[]>(() => {
  const t2 = totalPages.value, c = page.value
  if (t2 <= 7) return Array.from({ length: t2 }, (_, i) => i + 1)
  const set = new Set([1, 2, t2 - 1, t2, c - 1, c, c + 1].filter(p => p >= 1 && p <= t2))
  const arr = [...set].sort((a, b) => a - b)
  const out: (number | '…')[] = []
  let prev = 0
  for (const p of arr) { if (p - prev > 1) out.push('…'); out.push(p); prev = p }
  return out
})
watch([kw, collegeFilter, majorFilter, semesterFilter, teacherFilter, statusFilter, creditsFilter, categoryFilter, pageSize], () => { page.value = 1 })
watch(totalPages, v => { if (page.value > v) page.value = v })

/* —— 筛选轨道 chip 定义：读写同一个 filters —— */
type ChipKey = 'category' | 'college' | 'teacher' | 'status' | 'credits'
const openChip = ref<ChipKey | null>(null)
function toggleChip(k: ChipKey) { openChip.value = openChip.value === k ? null : k }
const chipDefs = computed(() => [
  {
    key: 'category' as ChipKey, label: t('类别'), active: !!categoryFilter.value, value: categoryFilter.value || '', current: categoryFilter.value,
    options: CATEGORIES.map(c => ({ value: c, label: c, count: list.value.filter(x => x.category === c).length })),
    clear: () => { categoryFilter.value = null }, set: (v: any) => { categoryFilter.value = v as string | null },
  },
  {
    key: 'college' as ChipKey, label: t('学院'), active: !!collegeFilter.value, value: collegeFilter.value === '__none__' ? t('全校公共课') : (collegeFilter.value || ''), current: collegeFilter.value,
    options: [{ value: '__none__', label: t('全校公共课'), count: list.value.filter(x => !(x.targetCollege || '').trim()).length },
      ...collegeOpts.value.map(c => ({ value: c, label: c, count: list.value.filter(x => (x.targetCollege || '').trim() === c).length }))],
    clear: () => { collegeFilter.value = null }, set: (v: any) => { collegeFilter.value = v as string | null },
  },
  {
    key: 'teacher' as ChipKey, label: t('教师'), active: teacherFilter.value !== null, value: teacherFilter.value !== null ? (teachers.value.find(x => x.id === teacherFilter.value)?.name || String(teacherFilter.value)) : '', current: teacherFilter.value,
    options: teachers.value.map(tc => ({ value: tc.id, label: tc.name, count: list.value.filter(x => x.teacherId === tc.id).length })),
    clear: () => { teacherFilter.value = null }, set: (v: any) => { teacherFilter.value = v as number | null },
  },
  {
    key: 'status' as ChipKey, label: t('状态'), active: !!statusFilter.value, value: statusFilter.value || '', current: statusFilter.value,
    options: STATUS_OPTS.map(s => ({ value: s, label: s, count: list.value.filter(x => statusText(x) === s).length })),
    clear: () => { statusFilter.value = null }, set: (v: any) => { statusFilter.value = v as string | null },
  },
  {
    key: 'credits' as ChipKey, label: t('学分'), active: creditsFilter.value !== null, value: creditsFilter.value !== null ? String(creditsFilter.value) : '', current: creditsFilter.value,
    options: [1, 2, 3, 4, 5, 6].map(n => ({ value: n, label: `${n} ${t('学分')}`, count: list.value.filter(x => x.credit === n).length })),
    clear: () => { creditsFilter.value = null }, set: (v: any) => { creditsFilter.value = v as number | null },
  },
])
const hasFilter = computed(() => !!(kw.value || categoryFilter.value || collegeFilter.value || majorFilter.value || semesterFilter.value !== null || teacherFilter.value !== null || statusFilter.value || creditsFilter.value !== null))
function clearFilters() {
  kw.value = ''; categoryFilter.value = null; collegeFilter.value = null; majorFilter.value = null
  semesterFilter.value = null; teacherFilter.value = null; statusFilter.value = null; creditsFilter.value = null
  page.value = 1
}
function filterByTeacher(id: number, name?: string) {
  teacherFilter.value = teacherFilter.value === id ? null : id
  ElMessage.success(t('已按教师筛选：{n}', { n: name || '' }))
}

/* —— 左侧树交互 —— */
function isOpen(k: string) { return openNodes.value.has(k) }
function toggleNode(k: string) { const s = new Set(openNodes.value); s.has(k) ? s.delete(k) : s.add(k); openNodes.value = s }
function clickCat(cat: any) {
  categoryFilter.value = categoryFilter.value === cat.name ? null : cat.name
  collegeFilter.value = null
  toggleNode('cat:' + cat.name)
}
function selectTree(cat: string | null, col: string | null) { categoryFilter.value = cat; collegeFilter.value = col }
const scopeLabel = computed(() => {
  const parts: string[] = []
  if (categoryFilter.value) parts.push(categoryFilter.value)
  if (collegeFilter.value) parts.push(collegeFilter.value === '__none__' ? t('全校公共课') : collegeFilter.value)
  return parts.length ? parts.join(' · ') : t('全部课程')
})

/* —— 空结果 Dice 纠错建议 —— */
const suggestChips = computed(() => {
  const k = kw.value.trim()
  if (!k) return []
  const names = [...new Set(list.value.map(c => c.name))]
  return suggestSimilar(k, names, 5, 0.15)
})

/* ============================================================
 * 命令面板：即时联想（课程 + 教师分组）、↑↓ 导航、Enter 选中、Esc 关闭
 * ============================================================ */
const cmdOpen = ref(false)
const cmdTerm = ref('')
const cmdCursor = ref(0)
const cmdInput = ref<HTMLInputElement | null>(null)
function openCommand() {
  cmdTerm.value = kw.value
  cmdCursor.value = 0
  cmdOpen.value = true
  nextTick(() => cmdInput.value?.focus())
}
function closeCommand(clear: boolean) {
  cmdOpen.value = false
  if (clear) { cmdTerm.value = ''; kw.value = '' }
  else { cmdTerm.value = kw.value }   // 关闭面板保留已输入关键词 → 空结果态/Dice 纠错可达
}
watch(cmdTerm, v => { if (cmdOpen.value) kw.value = v })
const cmdCourses = computed(() => {
  const q = cmdTerm.value.trim()
  const arr = !q ? list.value.slice(0, 8)
    : list.value.filter(c => matchText(`${c.name || ''} ${c.courseCode || ''} ${c.teacherName || ''}`, c._py || '', q)).slice(0, 8)
  return arr
})
const cmdTeachers = computed(() => {
  const q = cmdTerm.value.trim()
  const arr = !q ? teachers.value.slice(0, 5)
    : teachers.value.filter(tc => matchText(`${tc.name || ''} ${tc.no || ''} ${tc.college || ''}`, tc._py || '', q)).slice(0, 5)
  return arr
})
type CmdItem = { kind: 'course' | 'teacher'; id: number; label: string; raw: any }
const cmdItems = computed<CmdItem[]>(() => [
  ...cmdCourses.value.map(c => ({ kind: 'course' as const, id: c.id, label: c.name, raw: c })),
  ...cmdTeachers.value.map(tc => ({ kind: 'teacher' as const, id: tc.id, label: tc.name, raw: tc })),
])
watch(cmdItems, () => { if (cmdCursor.value >= cmdItems.value.length) cmdCursor.value = 0 })
function selectCmd(item: CmdItem | undefined) {
  if (!item) return
  if (item.kind === 'course') {
    cmdOpen.value = false; cmdTerm.value = ''; kw.value = ''
    detail.value = item.raw
  } else {
    teacherFilter.value = item.id
    cmdOpen.value = false; cmdTerm.value = ''; kw.value = ''
    ElMessage.success(t('已按教师筛选：{n}', { n: item.label }))
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

/* —— 详情抽屉字段 —— */
const detailFields = computed(() => {
  const c = detail.value
  if (!c) return []
  const total = c.stat?.total || 0
  const remain = Math.max(0, (c.capacity || 0) - total)
  return [
    { label: t('课程名称'), value: c.name },
    { label: t('课程代码'), value: c.courseCode },
    { label: t('类别'), value: c.category },
    { label: t('学分'), value: c.credit },
    { label: t('学时'), value: c.hours },
    { label: t('授课教师'), value: c.teacherName || c.teacher || '未分配' },
    { label: t('适用学院'), value: (c.targetCollege || '').trim() || t('全校公共课') },
    { label: t('适用专业'), value: (c.targetMajor || '').trim() || t('全院/全校通用') },
    { label: t('开课学期'), value: c.semesterNo != null ? `${semesterTitle(c.semesterNo)}（第 ${c.semesterNo} 学期）` : t('未排期') },
    { label: t('容量 / 已报 / 余量'), value: `${c.capacity ?? '-'} / ${total} / ${remain}` },
    { label: t('状态'), value: statusText(c) },
    { label: t('上课时间'), value: c.time },
    { label: t('教室'), value: c.location },
    { label: t('校区'), value: c.campus },
    { label: t('考试时间'), value: c.examTime || t('按上课时间') },
    { label: t('先修课程'), value: c.prereqName || (c.prereqId ? t('已设置') : t('无')) },
    { label: t('课程评分'), value: c.rating != null ? `${Number(c.rating).toFixed(1)}（${c.ratingCount || 0} 人）` : t('暂无评价') },
  ]
})

/* —— 导出当前筛选范围 —— */
async function exportCurrent() {
  try {
    const rows = filtered.value
    const header = ['课程名', '课程代码', '类别', '学分', '学时', '教师', '适用学院', '适用专业', '开课学期', '容量', '已报', '状态', '上课时间', '教室']
    const csv = [header.join(','), ...rows.map(c => [
      c.name, c.courseCode, c.category, c.credit, c.hours, c.teacherName || c.teacher || '',
      c.targetCollege || '', (c.targetMajor || '').replace(/\|/g, '/'), c.semesterNo ?? '',
      c.capacity, c.stat?.total || 0, statusText(c), c.time || '', c.location || '',
    ].map(v => `"${String(v ?? '').replace(/"/g, '""')}"`).join(','))].join('\n')
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
    const a = document.createElement('a')
    a.href = URL.createObjectURL(blob)
    a.download = `课程-${scopeLabel.value}.csv`
    document.body.appendChild(a); a.click(); a.remove()
    URL.revokeObjectURL(a.href)
    ElMessage.success(t('已导出当前筛选范围'))
  } catch (e: any) { ElMessage.error(e?.message || t('导出失败')) }
}

/* —— 先修链 —— */
const prereqVisible = ref(false)
const prereqPairs = computed(() => list.value.filter(c => c.prereqId).map(c => ({
  preId: c.prereqId,
  preName: (list.value.find(x => x.id === c.prereqId) || {}).name || '先修课程',
  depName: c.name,
})))
const prereqNodes = computed(() => {
  const n = prereqPairs.value.length
  if (!n) return []
  return prereqPairs.value.map((p, i) => {
    const step = Math.min(150, 170 / Math.max(1, n - 1))
    const y = n === 1 ? 100 : 30 + i * step
    return { ...p, ly: y, ry: y }
  })
})
function openPrereqFor(_c: any) { prereqVisible.value = true }

/* —— 上课时间选项 —— */
const BUILTIN_TIME_GROUPS = buildTimeOptionGroups()
const BUILTIN_TIME_VALUES = new Set(BUILTIN_TIME_GROUPS.flatMap(g => g.options.map(o => o.value)))
const timeOptionGroups = computed(() => {
  const groups = BUILTIN_TIME_GROUPS.map(g => ({ ...g }))
  const legacy = (form.time || '').trim()
  if (legacy && !BUILTIN_TIME_VALUES.has(legacy)) groups.push({ label: '已有课程时间（自定义）', options: [{ value: legacy, label: legacy, meta: '沿用原有排课' }] })
  return groups
})
const scheduleInfo = computed(() => describeSchedule(form.time || ''))
const scheduleWarn = computed(() => {
  const s = scheduleInfo.value
  if (!s) return false
  const raw = (form.time || '').trim()
  return s.nonStandard || s.sameDay || (!!raw && !/^周[一二三四五六日]\s*\d{2}:\d{2}-\d{2}:\d{2}( \/ 周[一二三四五六日]\s*\d{2}:\d{2}-\d{2}:\d{2})*$/.test(raw))
})
const scheduleHint = computed(() => {
  const raw = (form.time || '').trim()
  if (!raw) return ''
  const s = scheduleInfo.value
  if (!s) return t('格式无法识别，应为「周一 08:00-09:40」，多次课用 / 分隔')
  let msg = t('每周 {h} 学时 · {n} 次连堂 · 当前学分 {c}', { h: s.hours, n: s.count, c: form.credit ?? '-' })
  if (s.nonStandard) msg += t(' · 非标准节次，课表无法定位，请从下拉选择')
  if (s.sameDay) msg += t(' · 同一天排了两次课，请检查')
  if (s.nightOrWeekend && !s.nonStandard) msg += t(' · 晚间/周末一般安排公选、重修或辅修课')
  return msg
})

/* —— 编辑弹窗 —— */
const dlg = ref(false)
const form = reactive<any>({ id: null, name: '', courseCode: '', hours: 2, category: '公共必修', credit: 3, teacherId: null, capacity: 60, time: '', location: '', campus: '', examTime: '', intro: '', semesterNo: null, prereqId: null, targetCollege: '', targetMajor: '' })
function openDialog(c: any) { Object.assign(form, c || { id: null, name: '', courseCode: '', hours: 2, category: '公共必修', credit: 3, teacherId: null, capacity: 60, time: '', location: '', campus: '', examTime: '', intro: '', semesterNo: null, prereqId: null, targetCollege: '', targetMajor: '' }); dlg.value = true }
function editFromDrawer(c: any) { detail.value = null; openDialog(c) }
async function save() {
  try {
    if (form.id) await http.put(`/courses/${form.id}`, form); else await http.post('/courses', form)
    ElMessage.success(t('保存成功')); dlg.value = false; load()
  } catch (e: any) { ElMessage.error(e?.message || t('保存失败')) }
}
async function del(c: any) {
  try { await ElMessageBox.confirm(t('确定删除课程「{name}」？', { name: c.name }), t('提示'), { type: 'warning', confirmButtonText: t('确定'), cancelButtonText: t('取消') }) } catch { return }
  try { await http.delete(`/courses/${c.id}`); ElMessage.success(t('已删除')); detail.value = null; load() } catch (e: any) { ElMessage.error(e?.message || t('删除失败')) }
}

/* —— 加载：预计算拼音首字母索引（命令面板 / 关键词联想用） —— */
async function load() {
  try {
    const [c, tc, cfg, cr, cur] = await Promise.all([
      http.get('/courses'), http.get('/courses/teachers'),
      http.get('/system/config').catch(() => null),
      http.get('/classrooms').catch(() => null),
      http.get('/system/current-semester').catch(() => null),
    ])
    curSemester.value = Number((cur as any)?.data?.semesterNo) || 0
    teacherMax.value = Number(cfg?.data?.teacherMaxCourses) || 3
    list.value = (c?.data || []).map((x: any) => ({ ...x, _py: initialsOf(`${x.name || ''} ${x.courseCode || ''} ${x.teacherName || x.teacher || ''}`) }))
    teachers.value = (tc?.data || []).map((x: any) => ({ ...x, _py: initialsOf(x.name || '') }))
    rooms.value = (cr?.data || []).map((x: any) => x.name).filter(Boolean)
  } catch {}
}

onMounted(() => { load(); window.addEventListener('keydown', onGlobalKey) })
onBeforeUnmount(() => { window.removeEventListener('keydown', onGlobalKey) })
</script>

<style scoped>
/* ================= 局部主题：翠绿 #10b981 系，兼容项目深浅主题变量 ================= */
.cm-page {
  --g: #10b981;
  --g-dark: #059669;
  --g-soft: rgba(16,185,129,.10);
  --g-soft2: rgba(16,185,129,.16);
  display: flex; flex-direction: column; gap: 16px; padding: 4px 0;
}

/* ---------- 顶部 sticky 导航栏 ---------- */
.cm-topbar {
  position: sticky; top: 0; z-index: 40;
  display: flex; align-items: center; gap: 16px; flex-wrap: wrap;
  padding: 12px 16px;
  background: color-mix(in srgb, var(--card, #fff) 86%, transparent);
  backdrop-filter: saturate(1.4) blur(14px);
  -webkit-backdrop-filter: saturate(1.4) blur(14px);
  border: 0.5px solid var(--border, #e5e7eb); border-radius: 16px;
  box-shadow: 0 1px 2px rgba(16,24,40,.03), 0 8px 24px rgba(16,24,40,.04);
}
.cm-brand { display: flex; align-items: center; gap: 12px; flex: 0 0 auto; }
.cm-brand-icon {
  width: 40px; height: 40px; border-radius: 12px; flex: 0 0 auto;
  display: grid; place-items: center; color: #fff;
  background: linear-gradient(135deg, #34d399, var(--g-dark));
  box-shadow: 0 4px 12px rgba(16,185,129,.32);
}
.cm-brand-icon .ic { width: 21px; height: 21px; }
.cm-title { font-size: 17px; font-weight: 700; color: var(--text, #0f172a); line-height: 1.2; }
.cm-subtitle { font-size: 12px; color: var(--text-lighter, #94a3b8); margin-top: 2px; }
.cm-search-trigger {
  flex: 1 1 320px; max-width: 520px; min-width: 180px; height: 38px;
  display: flex; align-items: center; gap: 9px; padding: 0 10px 0 14px;
  border: 1px solid var(--border, #e5e7eb); border-radius: 999px;
  background: var(--card, #fff); color: var(--text-lighter, #94a3b8);
  cursor: pointer; transition: all .18s; font-family: inherit;
}
.cm-search-trigger:hover { border-color: var(--g); box-shadow: 0 0 0 3px var(--g-soft); }
.cm-search-trigger .ic { width: 17px; height: 17px; flex: 0 0 auto; }
.cm-search-ph { flex: 1; text-align: left; font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.cm-kbd {
  font-family: inherit; font-size: 11px; line-height: 1; white-space: nowrap;
  padding: 4px 7px; border-radius: 6px; border: 1px solid var(--border, #e5e7eb);
  background: color-mix(in srgb, var(--card, #fff) 92%, #000 8%); color: var(--text-light, #64748b);
  flex: 0 0 auto;
}
.cm-top-actions { display: flex; align-items: center; gap: 8px; flex: 0 0 auto; margin-left: auto; }
.cm-btn {
  display: inline-flex; align-items: center; justify-content: center; gap: 6px;
  height: 36px; padding: 0 14px; border-radius: 10px; font-family: inherit; font-size: 13px; font-weight: 600;
  cursor: pointer; transition: all .16s; white-space: nowrap; border: 1px solid transparent;
}
.cm-btn .ic { width: 15px; height: 15px; }
.cm-btn-solid { background: var(--g); color: #fff; box-shadow: 0 2px 8px rgba(16,185,129,.26); }
.cm-btn-solid:hover { background: var(--g-dark); transform: translateY(-1px); }
.cm-btn-outline { background: var(--card, #fff); color: var(--text, #334155); border-color: var(--border, #e5e7eb); }
.cm-btn-outline:hover { border-color: var(--g); color: var(--g); }
.cm-btn-ghost { background: transparent; color: var(--text-light, #64748b); height: 30px; padding: 0 10px; font-weight: 500; }
.cm-btn-ghost:hover { background: var(--g-soft); color: var(--g); }
.cm-btn-danger { background: transparent; color: #ef4444; border-color: rgba(239,68,68,.35); }
.cm-btn-danger:hover { background: rgba(239,68,68,.08); }

/* ---------- 主体：左树 + 右主区 ---------- */
.cm-layout { display: grid; grid-template-columns: 240px minmax(0, 1fr); gap: 16px; align-items: start; }

/* ---------- 左侧分类树 ---------- */
.cm-tree {
  border: 0.5px solid var(--border, #e5e7eb); border-radius: 14px; background: var(--card, #fff); overflow: hidden;
  box-shadow: 0 1px 2px rgba(16,24,40,.03); position: sticky; top: 82px;
}
.cm-tree-h { display: flex; align-items: center; justify-content: space-between; padding: 12px 14px 8px; font-size: 13px; color: var(--text); }
.cm-tree-h b { font-weight: 700; }
.cm-tree-h span { font-size: 11px; color: var(--text-lighter); }
.cm-tree-search { display: flex; align-items: center; gap: 7px; margin: 2px 10px 10px; padding: 0 10px; height: 32px; border: 1px solid var(--border); border-radius: 9px; background: color-mix(in srgb, var(--card) 94%, #000 6%); }
.cm-tree-search .ic { width: 14px; height: 14px; color: var(--text-lighter); flex: 0 0 auto; }
.cm-tree-search input { flex: 1; min-width: 0; border: none; outline: none; background: transparent; color: var(--text); font-family: inherit; font-size: 12px; }
.cm-tree-search input::placeholder { color: var(--text-lighter); }
.cm-tree-body { max-height: calc(100vh - 210px); overflow-y: auto; padding: 2px 8px 10px; }
.cm-node {
  display: flex; align-items: center; gap: 6px; width: 100%; text-align: left; border: none; background: none;
  font-family: inherit; font-size: 12.5px; color: var(--text-light); cursor: pointer; border-radius: 9px; padding: 8px 9px; transition: all .14s;
}
.cm-node:hover { background: var(--g-soft); color: var(--g); }
.cm-node.on { background: var(--g); color: #fff; font-weight: 600; }
.cm-node.on .cm-n { color: rgba(255,255,255,.95); background: rgba(255,255,255,.22); }
.cm-node-label { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cm-n { flex: 0 0 auto; font-size: 10.5px; color: var(--text-lighter); background: color-mix(in srgb, var(--card) 90%, #000 10%); border-radius: 999px; padding: 1px 7px; font-variant-numeric: tabular-nums; }
.cm-root { font-weight: 600; color: var(--text); }
.cm-caret { width: 13px; display: inline-flex; flex: 0 0 auto; color: var(--text-lighter); transition: transform .16s; }
.cm-caret .ic { width: 13px; height: 13px; }
.cm-caret.open { transform: rotate(90deg); }
.cm-leaves { padding-left: 12px; }
.cm-leaf { font-size: 12px; padding: 7px 9px 7px 14px; }
.cm-tree-empty { padding: 18px 8px; text-align: center; font-size: 12px; color: var(--text-lighter); }

/* ---------- 右侧主区 ---------- */
.cm-main { min-width: 0; display: flex; flex-direction: column; gap: 14px; }
.cm-filtercard, .cm-tablecard {
  border: 0.5px solid var(--border, #e5e7eb); border-radius: 14px; background: var(--card, #fff);
  box-shadow: 0 1px 2px rgba(16,24,40,.03);
}
.cm-filtercard { padding: 12px 14px; }
.cm-chip-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.cm-chip-wrap { position: relative; }
.cm-chip {
  display: inline-flex; align-items: center; gap: 6px; height: 32px; padding: 0 11px;
  border: 1px solid var(--border); border-radius: 999px; background: var(--card); color: var(--text-light);
  font-family: inherit; font-size: 12.5px; cursor: pointer; transition: all .16s;
}
.cm-chip:hover { border-color: var(--g); color: var(--g); }
.cm-chip.on { background: var(--g-soft); border-color: var(--g); color: var(--g-dark); font-weight: 600; }
.cm-chip-label { white-space: nowrap; }
.cm-chip-value { max-width: 120px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--g-dark); }
.cm-chip-arrow { width: 13px; height: 13px; transition: transform .16s; }
.cm-chip-arrow.open { transform: rotate(180deg); }
.cm-reset { border: none; background: none; color: var(--text-lighter); font-family: inherit; font-size: 12.5px; cursor: pointer; padding: 6px 8px; }
.cm-reset:hover { color: #ef4444; }
.cm-dropdown {
  position: absolute; top: calc(100% + 6px); left: 0; z-index: 30; min-width: 190px; max-height: 320px; overflow-y: auto;
  padding: 6px; border: 0.5px solid var(--border); border-radius: 12px; background: var(--card);
  box-shadow: 0 12px 32px rgba(16,24,40,.14);
}
.cm-opt {
  display: flex; align-items: center; justify-content: space-between; gap: 12px; width: 100%;
  border: none; background: none; font-family: inherit; font-size: 12.5px; color: var(--text-light);
  cursor: pointer; padding: 8px 10px; border-radius: 8px; text-align: left;
}
.cm-opt:hover { background: var(--g-soft); color: var(--g); }
.cm-opt.on { background: var(--g-soft); color: var(--g-dark); font-weight: 600; }
.cm-opt-n { margin-left: 8px; font-size: 11px; color: var(--text-lighter); font-variant-numeric: tabular-nums; }
.cm-opt-check { color: var(--g); font-weight: 700; }

/* ---------- 表格卡片 ---------- */
.cm-table-top { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 12px 16px; border-bottom: 0.5px solid var(--border); }
.cm-scope { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text); min-width: 0; }
.cm-scope b { font-weight: 700; color: var(--g-dark); }
.cm-scope-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--g); box-shadow: 0 0 0 3px var(--g-soft); flex: 0 0 auto; }
.cm-scope-n { font-size: 11.5px; color: var(--text-light); background: color-mix(in srgb, var(--card) 90%, #000 10%); padding: 2px 9px; border-radius: 999px; }
.cm-table-actions { display: flex; align-items: center; gap: 4px; margin-left: auto; }
.cm-table-scroll { overflow-x: auto; }
.cm-table { width: 100%; min-width: 760px; border-collapse: collapse; font-size: 12.5px; }
.cm-table thead th {
  position: sticky; top: 0; z-index: 5;
  padding: 11px 14px; text-align: left; white-space: nowrap;
  font-size: 11.5px; font-weight: 600; letter-spacing: .02em; color: var(--text-lighter);
  background: color-mix(in srgb, var(--card, #fff) 82%, transparent);
  backdrop-filter: blur(10px); -webkit-backdrop-filter: blur(10px);
  border-bottom: 0.5px solid var(--border);
}
.cm-row { cursor: pointer; transition: background .13s; }
.cm-row:hover { background: var(--g-soft); }
.cm-row td { padding: 10px 14px; border-bottom: 0.5px solid var(--border); color: var(--text-light); vertical-align: middle; }
.cm-row:last-child td { border-bottom: none; }
.cm-course-name { font-weight: 600; color: var(--text); font-size: 13px; }
.cm-course-code { margin-top: 2px; font-size: 11px; color: var(--text-lighter); font-variant-numeric: tabular-nums; }
.cm-cat-tag { display: inline-block; padding: 3px 9px; border-radius: 6px; font-size: 11.5px; font-weight: 600; white-space: nowrap; }
.cat-公共必修 { background: rgba(16,185,129,.13); color: #059669; }
.cat-专业必修 { background: rgba(59,130,246,.13); color: #2563eb; }
.cat-专业选修 { background: rgba(139,92,246,.13); color: #7c3aed; }
.cat-公共选修 { background: rgba(245,158,11,.14); color: #d97706; }
.col-credit { text-align: center; width: 66px; font-variant-numeric: tabular-nums; }
.cm-teacher-link { border: none; background: none; padding: 0; color: var(--g-dark); font-family: inherit; font-size: 12.5px; cursor: pointer; }
.cm-teacher-link:hover { text-decoration: underline; }
.cm-muted { color: var(--text-lighter); }
.col-cap { width: 130px; }
.cm-cap-num { font-size: 12px; font-variant-numeric: tabular-nums; color: var(--text-lighter); }
.cm-cap-num b { color: var(--text); font-size: 12.5px; }
.cm-bar { margin-top: 5px; height: 4px; border-radius: 999px; background: color-mix(in srgb, var(--card) 88%, #000 12%); overflow: hidden; }
.cm-bar span { display: block; height: 100%; border-radius: 999px; transition: width .3s; }
.bar-ok { background: #10b981; }
.bar-mid { background: #f59e0b; }
.bar-over { background: #ef4444; }
.cm-status { display: inline-block; padding: 3px 9px; border-radius: 999px; font-size: 11.5px; font-weight: 600; white-space: nowrap; }
.st-正常 { background: rgba(16,185,129,.13); color: #059669; }
.st-已满 { background: rgba(245,158,11,.14); color: #d97706; }
.st-报名超额 { background: rgba(239,68,68,.13); color: #dc2626; }
.col-op { width: 108px; }
.cm-op-icons { display: flex; align-items: center; gap: 4px; opacity: 0; transition: opacity .15s; }
.cm-row:hover .cm-op-icons, .cm-op-icons:focus-within { opacity: 1; }
.cm-icon-btn {
  width: 28px; height: 28px; display: grid; place-items: center; border: 0.5px solid var(--border); border-radius: 8px;
  background: var(--card); color: var(--text-light); cursor: pointer; transition: all .14s;
}
.cm-icon-btn .ic { width: 14px; height: 14px; }
.cm-icon-btn:hover { border-color: var(--g); color: var(--g); background: var(--g-soft); }
.cm-icon-btn.danger:hover { border-color: #ef4444; color: #ef4444; background: rgba(239,68,68,.08); }

/* ---------- 空结果态 ---------- */
.cm-empty { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 46px 20px 50px; }
.cm-empty-icon { width: 56px; height: 56px; border-radius: 16px; display: grid; place-items: center; background: var(--g-soft); color: var(--g); margin-bottom: 14px; }
.cm-empty-icon .ic { width: 28px; height: 28px; }
.cm-empty-title { font-size: 15px; font-weight: 700; color: var(--text); }
.cm-empty-sub { font-size: 12.5px; color: var(--text-lighter); margin-top: 5px; }
.cm-suggest { display: flex; align-items: center; gap: 7px; flex-wrap: wrap; justify-content: center; margin-top: 18px; }
.cm-suggest-label { font-size: 12px; color: var(--text-light); }
.cm-suggest-chip { border: 1px solid var(--border); background: var(--card); color: var(--g-dark); font-family: inherit; font-size: 12.5px; padding: 6px 12px; border-radius: 999px; cursor: pointer; transition: all .15s; }
.cm-suggest-chip:hover { border-color: var(--g); background: var(--g-soft); }
.cm-empty-reset { margin-top: 18px; border: none; background: none; color: var(--g); font-family: inherit; font-size: 12.5px; cursor: pointer; text-decoration: underline; text-underline-offset: 3px; }

/* ---------- 分页 ---------- */
.cm-pagination { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; padding: 12px 16px; border-top: 0.5px solid var(--border); }
.cm-pg-info { font-size: 12px; color: var(--text-light); font-variant-numeric: tabular-nums; }
.cm-pg-right { display: flex; align-items: center; gap: 8px; margin-left: auto; }
.cm-pg-size { font-size: 12px; color: var(--text-light); }
.cm-pg-select { padding: 5px 8px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); color: var(--text); font-family: inherit; font-size: 12px; }
.cm-pg-btns { display: flex; align-items: center; gap: 4px; }
.cm-pg-btn { min-width: 30px; height: 30px; padding: 0 7px; border: 1px solid var(--border); border-radius: 8px; background: var(--card); color: var(--text-light); font-family: inherit; font-size: 12.5px; cursor: pointer; transition: all .14s; }
.cm-pg-btn:hover:not(:disabled) { border-color: var(--g); color: var(--g); }
.cm-pg-btn.active { background: var(--g); border-color: var(--g); color: #fff; font-weight: 700; }
.cm-pg-btn:disabled { opacity: .4; cursor: not-allowed; }
.cm-pg-dots { color: var(--text-lighter); padding: 0 2px; }

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
.cmd-item-ico.course { background: rgba(16,185,129,.13); color: var(--g); }
.cmd-item-ico.teacher { background: rgba(59,130,246,.13); color: #3b82f6; }
.cmd-item-main { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.cmd-item-main b { font-size: 13px; font-weight: 600; color: var(--text); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-item-main small { font-size: 11px; color: var(--text-lighter); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cmd-enter { color: var(--g); font-size: 14px; flex: 0 0 auto; }
.cmd-empty { padding: 36px 16px; text-align: center; color: var(--text-lighter); font-size: 13px; }
.cmd-footer { display: flex; align-items: center; gap: 16px; padding: 10px 16px; border-top: 0.5px solid var(--border); background: color-mix(in srgb, var(--card) 94%, #000 6%); font-size: 11px; color: var(--text-lighter); }
.cmd-footer span { display: inline-flex; align-items: center; gap: 4px; }
.cmd-footer .cm-kbd { padding: 2px 6px; }
.cmd-footer-tip { margin-left: auto; color: var(--g); }
@keyframes cmdFade { from { opacity: 0 } to { opacity: 1 } }
@keyframes cmdUp { from { opacity: 0; transform: translateY(-10px) scale(.985) } to { opacity: 1; transform: none } }

/* ---------- 详情抽屉 ---------- */
.cm-mask { position: fixed !important; inset: 0; background: rgba(0,0,0,.42); z-index: 3500 !important; display: flex; justify-content: flex-end; animation: cmdFade .16s ease; }
.cm-drawer { width: 400px; max-width: 92vw; height: 100%; background: var(--card); border-left: 0.5px solid var(--border); display: flex; flex-direction: column; animation: drawerIn .22s cubic-bezier(.16,1,.3,1); }
@keyframes drawerIn { from { transform: translateX(24px); opacity: .6 } to { transform: none; opacity: 1 } }
.cm-drawer-h { display: flex; align-items: center; padding: 18px 20px; border-bottom: 0.5px solid var(--border); }
.cm-drawer-h b { font-size: 16px; color: var(--text); }
.cm-drawer-h span { display: block; font-size: 12px; color: var(--text-lighter); margin-top: 3px; }
.cm-close { margin-left: auto; border: none; background: none; font-size: 24px; color: var(--text-light); cursor: pointer; line-height: 1; }
.cm-drawer-body { flex: 1; overflow-y: auto; padding: 12px 20px; }
.cm-field { display: flex; justify-content: space-between; gap: 12px; padding: 11px 0; border-bottom: 0.5px solid var(--border); font-size: 12.5px; }
.cm-field span { color: var(--text-lighter); flex: 0 0 auto; }
.cm-field b { color: var(--text); font-weight: 500; text-align: right; word-break: break-all; }
.cm-intro { margin-top: 16px; }
.cm-intro-h { font-size: 12px; font-weight: 700; color: var(--text); margin-bottom: 8px; }
.cm-intro-body { font-size: 12.5px; line-height: 1.75; color: var(--text-light); background: var(--g-soft); border-radius: 10px; padding: 12px 14px; }
.cm-drawer-f { display: flex; gap: 8px; padding: 14px 20px; border-top: 0.5px solid var(--border); }

/* ---------- 先修链 ---------- */
.pr-svg { width: 100%; height: auto; }
.pr-node { fill: rgba(16,185,129,.1); stroke: rgba(16,185,129,.5); }
.pr-node.dep { fill: rgba(56,189,248,.1); stroke: rgba(56,189,248,.5); }
.pr-label { font-size: 12.5px; fill: var(--text); font-weight: 600; }
.pr-edge { stroke: var(--g); stroke-width: 1.6; stroke-dasharray: 5 3; fill: none; }
.pr-tip { margin-top: 8px; font-size: 12px; color: var(--text-light); text-align: center; }
.pr-empty { text-align: center; padding: 30px; color: var(--text-light); font-size: 13px; }

/* ---------- 编辑弹窗时间提示 ---------- */
.time-hint { margin-top: 5px; font-size: 11px; line-height: 1.5; color: var(--text-light); }
.time-hint.warn { color: #e0872b; }
.time-hint-value { margin-top: 1px; font-variant-numeric: tabular-nums; word-break: break-all; opacity: .82; }

@media (max-width: 1100px) {
  .cm-search-trigger { order: 3; flex-basis: 100%; max-width: none; }
  .cm-top-actions { order: 2; }
}
@media (max-width: 860px) {
  .cm-layout { grid-template-columns: 1fr; }
  .cm-tree { display: none; }
  .cm-tree-body { max-height: 220px; }
}
@media (max-width: 560px) {
  .cm-topbar { padding: 12px; }
  .cm-btn-outline span { display: none; }
  .cm-kbd { display: none; }
  .cm-pg-right { margin-left: 0; width: 100%; justify-content: flex-end; }
}
</style>

<!-- 时间下拉由 Element Plus 传送到 body，需全局样式 -->
<style>
.course-time-popper.el-select-dropdown,
.el-select-dropdown.course-time-popper { max-width: 660px !important; }
.course-time-popper .el-select-group__title { font-size: 12px; font-weight: 700; color: var(--primary, #10b981); }
.course-time-popper .el-select-dropdown__item { height: auto; min-height: 34px; line-height: 1.5; padding: 6px 12px; }
.course-time-popper .to-row { display: flex; align-items: baseline; justify-content: space-between; gap: 14px; }
.course-time-popper .to-main { white-space: nowrap; font-variant-numeric: tabular-nums; }
.course-time-popper .to-meta { flex-shrink: 0; font-size: 11px; color: var(--text-light, #94a3b8); white-space: nowrap; }
@media (max-width: 700px) {
  .course-time-popper .to-row { flex-direction: column; gap: 0; }
  .course-time-popper .to-meta { text-align: left; }
}
</style>
