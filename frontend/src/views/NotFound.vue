<template>
  <div class="nf">
    <div class="nf-card">
      <div class="nf-emoji">🧭</div>
      <div class="nf-code">404</div>
      <div class="nf-title">页面走丢了</div>
      <p class="nf-desc">你访问的页面不存在，或已被移动。检查一下地址，或返回首页继续。</p>
      <button class="nf-btn" @click="$router.push('/dashboard')">
        <span class="icon" v-html="svgIcon('home')" /> 回到首页
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
const svgIcon = (n: string) => `<svg class="icon"><use href="#i-${n}"/></svg>`
</script>

<style scoped>
.nf {
  min-height: 100vh; display: flex; flex-direction: column;
  align-items: center; justify-content: center; padding: 24px;
  background: radial-gradient(circle at 30% 20%, rgba(99,102,241,.12), transparent 55%),
              radial-gradient(circle at 80% 80%, rgba(236,72,153,.10), transparent 50%);
}
.nf-card {
  text-align: center; padding: 44px 48px; border-radius: 22px;
  background: linear-gradient(145deg, rgba(255,255,255,.82), rgba(255,255,255,.5));
  backdrop-filter: blur(16px) saturate(1.7); -webkit-backdrop-filter: blur(16px) saturate(1.7);
  border: 1px solid rgba(255,255,255,.6);
  box-shadow: 0 20px 60px rgba(79,70,229,.16), inset 0 1px 2px rgba(255,255,255,.9);
  max-width: 420px; animation: nfIn .5s cubic-bezier(.2,.8,.2,1) both;
}
@keyframes nfIn { from { opacity: 0; transform: translateY(16px) scale(.98); } to { opacity: 1; transform: none; } }
.nf-emoji { font-size: 52px; line-height: 1; }
.nf-code {
  font-size: 72px; font-weight: 800; margin: 10px 0 2px; line-height: 1;
  background: linear-gradient(135deg, #6366f1, #ec4899);
  -webkit-background-clip: text; background-clip: text; color: transparent;
}
.nf-title { font-size: 20px; font-weight: 700; color: var(--text); margin-bottom: 8px; }
.nf-desc { color: var(--text-light); font-size: 14px; line-height: 1.6; margin: 0 0 22px; }
.nf-btn {
  display: inline-flex; align-items: center; gap: 8px;
  border: none; cursor: pointer; padding: 11px 22px; border-radius: 12px;
  font-size: 14px; font-weight: 600; color: #fff;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  box-shadow: 0 8px 22px rgba(99,102,241,.4); transition: transform .15s, box-shadow .15s;
}
.nf-btn:hover { transform: translateY(-2px); box-shadow: 0 12px 28px rgba(99,102,241,.5); }
.nf-btn .icon { width: 16px; height: 16px; display: inline-block; }
.nf-btn .icon svg { width: 100%; height: 100%; }
html[data-theme="dark"] .nf-card {
  background: linear-gradient(145deg, rgba(30,41,59,.82), rgba(30,41,59,.58));
  border-color: rgba(255,255,255,.1);
  box-shadow: 0 20px 60px rgba(0,0,0,.5), inset 0 1px 2px rgba(255,255,255,.07);
}
</style>
