<template>
  <div class="an-kpis">
    <div
      v-for="(it, i) in items"
      :key="it.label"
      class="an-kpi"
      :class="'tone-' + (it.tone || 'blue')"
      :style="{ animationDelay: (i * 60) + 'ms' }"
    >
      <div class="an-kpi-icon" v-html="svgIcon(it.icon || 'bar-chart')" />
      <div class="an-kpi-body">
        <div class="an-kpi-value" :class="{ 'is-text': typeof it.value === 'string' && it.value.length > 3 }" :title="String(it.value)">{{ it.value }}</div>
        <div class="an-kpi-label">{{ it.label }}</div>
        <div v-if="it.hint" class="an-kpi-hint">{{ it.hint }}</div>
      </div>
      <span class="an-kpi-glow" aria-hidden="true" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  items: Array<{ label: string; value: string | number; hint?: string; icon?: string; tone?: string }>
}>()

const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
</script>
