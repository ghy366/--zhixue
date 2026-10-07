/* ============================================================
   智学选课 · 暗色主题星空粒子背景（Knowledge OS 风格）
   仅在 data-theme="dark" 时渲染：星空 + 粒子星球 + 流星
   ============================================================ */
(function () {
  'use strict';

  var canvas = document.createElement('canvas');
  canvas.id = 'cosmic-bg';
  document.body.insertBefore(canvas, document.body.firstChild);
  var ctx = canvas.getContext('2d');

  var DPR = Math.min(window.devicePixelRatio || 1, 2);
  var W = 0, H = 0;

  function resize() {
    W = window.innerWidth;
    H = window.innerHeight;
    canvas.width = W * DPR;
    canvas.height = H * DPR;
    canvas.style.width = W + 'px';
    canvas.style.height = H + 'px';
    ctx.setTransform(DPR, 0, 0, DPR, 0, 0);
    buildScene();
  }

  /* ---------- 星空 ---------- */
  var stars = [];
  function buildStars() {
    stars = [];
    var count = Math.round((W * H) / 9000);
    count = Math.max(90, Math.min(220, count));
    for (var i = 0; i < count; i++) {
      stars.push({
        x: Math.random() * W,
        y: Math.random() * H,
        r: 0.4 + Math.random() * 1.3,
        base: 0.25 + Math.random() * 0.55,
        phase: Math.random() * Math.PI * 2,
        speed: 0.4 + Math.random() * 1.1,
        drift: 0.02 + Math.random() * 0.06,
        hue: Math.random() < 0.12 ? (Math.random() < 0.5 ? '190,220,255' : '255,225,190') : '255,255,255'
      });
    }
  }

  /* ---------- 星云微光 ---------- */
  var nebulae = [];
  function buildNebulae() {
    nebulae = [
      { x: W * 0.78, y: H * 0.24, r: Math.max(W, H) * 0.34, c: '99,102,241', a: 0.10 },
      { x: W * 0.12, y: H * 0.85, r: Math.max(W, H) * 0.30, c: '34,211,238', a: 0.07 },
      { x: W * 0.45, y: H * 0.05, r: Math.max(W, H) * 0.26, c: '168,85,247', a: 0.06 }
    ];
  }

  /* ---------- 粒子星球 ---------- */
  var planet = { x: 0, y: 0, r: 0, orbit: [], halo: [], rot: 0 };
  function buildPlanet() {
    planet.x = W * 0.84;
    planet.y = H * 0.30;
    planet.r = Math.min(W, H) * 0.085;
    planet.rot = 0;

    // 轨道环粒子（围绕星球的粒子带）
    planet.orbit = [];
    var n = 90;
    for (var i = 0; i < n; i++) {
      planet.orbit.push({
        a: (i / n) * Math.PI * 2,          // 环上角度
        tilt: 0.32,                        // 椭圆倾斜压缩
        r: 1 + Math.random() * 1.8,
        w: 0.0025 + Math.random() * 0.004, // 角速度
        c: Math.random() < 0.35 ? '165,180,252' : (Math.random() < 0.5 ? '103,232,249' : '226,232,240'),
        tw: Math.random() * Math.PI * 2
      });
    }
    // 星球周围漂浮的逸散粒子
    planet.halo = [];
    for (var j = 0; j < 36; j++) {
      var ang = Math.random() * Math.PI * 2;
      var dist = planet.r * (1.35 + Math.random() * 1.1);
      planet.halo.push({
        dx: Math.cos(ang) * dist,
        dy: Math.sin(ang) * dist * 0.7,
        r: 0.6 + Math.random() * 1.4,
        a: 0.12 + Math.random() * 0.3,
        tw: Math.random() * Math.PI * 2
      });
    }
  }

  function drawPlanet(t) {
    var p = planet;
    ctx.save();
    ctx.translate(p.x, p.y);

    // 外发光
    var glow = ctx.createRadialGradient(0, 0, p.r * 0.6, 0, 0, p.r * 2.6);
    glow.addColorStop(0, 'rgba(129,140,248,0.22)');
    glow.addColorStop(0.5, 'rgba(99,102,241,0.08)');
    glow.addColorStop(1, 'rgba(99,102,241,0)');
    ctx.fillStyle = glow;
    ctx.beginPath();
    ctx.arc(0, 0, p.r * 2.6, 0, Math.PI * 2);
    ctx.fill();

    // 星球本体：中心亮、边缘暗的球体
    var body = ctx.createRadialGradient(-p.r * 0.35, -p.r * 0.35, p.r * 0.1, 0, 0, p.r);
    body.addColorStop(0, 'rgba(199,210,254,0.95)');
    body.addColorStop(0.35, 'rgba(129,140,248,0.85)');
    body.addColorStop(0.75, 'rgba(67,56,202,0.9)');
    body.addColorStop(1, 'rgba(30,27,75,0.95)');
    ctx.fillStyle = body;
    ctx.beginPath();
    ctx.arc(0, 0, p.r, 0, Math.PI * 2);
    ctx.fill();

    // 表面经纬粒子纹理（随时间缓慢旋转的亮点）
    for (var k = 0; k < 26; k++) {
      var la = (k / 26) * Math.PI * 2 + p.rot * 0.6;
      var lx = Math.cos(la) * p.r * 0.55;
      var ly = Math.sin(la * 1.7) * p.r * 0.4;
      var la2 = 0.25 + 0.2 * (0.5 + 0.5 * Math.sin(t * 0.001 + k));
      ctx.fillStyle = 'rgba(224,231,255,' + la2.toFixed(2) + ')';
      ctx.beginPath();
      ctx.arc(lx, ly, 1.1, 0, Math.PI * 2);
      ctx.fill();
    }

    // 粒子环（前后遮挡：后半在星球后，前半在星球前）
    var i, o, ox, oy, al;
    for (i = 0; i < p.orbit.length; i++) {
      o = p.orbit[i];
      o.a += o.w;
      ox = Math.cos(o.a) * p.r * 1.75;
      oy = Math.sin(o.a) * p.r * 1.75 * o.tilt;
      if (oy < 0) { // 后半环
        al = (0.35 + 0.3 * Math.sin(t * 0.002 + o.tw)) * 0.55;
        ctx.fillStyle = 'rgba(' + o.c + ',' + al.toFixed(2) + ')';
        ctx.beginPath();
        ctx.arc(ox, oy, o.r, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    for (i = 0; i < p.orbit.length; i++) {
      o = p.orbit[i];
      ox = Math.cos(o.a) * p.r * 1.75;
      oy = Math.sin(o.a) * p.r * 1.75 * o.tilt;
      if (oy >= 0) { // 前半环
        al = 0.5 + 0.4 * Math.sin(t * 0.002 + o.tw);
        ctx.fillStyle = 'rgba(' + o.c + ',' + al.toFixed(2) + ')';
        ctx.beginPath();
        ctx.arc(ox, oy, o.r, 0, Math.PI * 2);
        ctx.fill();
      }
    }

    // 逸散粒子
    for (i = 0; i < p.halo.length; i++) {
      var h = p.halo[i];
      al = h.a * (0.5 + 0.5 * Math.sin(t * 0.0015 + h.tw));
      ctx.fillStyle = 'rgba(199,210,254,' + al.toFixed(2) + ')';
      ctx.beginPath();
      ctx.arc(h.dx, h.dy + Math.sin(t * 0.0008 + h.tw) * 3, h.r, 0, Math.PI * 2);
      ctx.fill();
    }

    ctx.restore();
  }

  /* ---------- 流星 ---------- */
  var meteors = [];
  function spawnMeteor() {
    var fromLeft = Math.random() < 0.5;
    meteors.push({
      x: fromLeft ? -20 : W * (0.3 + Math.random() * 0.7),
      y: Math.random() * H * 0.4,
      vx: (fromLeft ? 1 : -1) * (5 + Math.random() * 4),
      vy: 2.4 + Math.random() * 2.2,
      len: 90 + Math.random() * 110,
      life: 1
    });
  }
  function drawMeteors() {
    for (var i = meteors.length - 1; i >= 0; i--) {
      var m = meteors[i];
      m.x += m.vx;
      m.y += m.vy;
      m.life -= 0.012;
      if (m.life <= 0 || m.x < -200 || m.x > W + 200 || m.y > H + 100) {
        meteors.splice(i, 1);
        continue;
      }
      var mag = Math.sqrt(m.vx * m.vx + m.vy * m.vy);
      var tx = m.x - (m.vx / mag) * m.len;
      var ty = m.y - (m.vy / mag) * m.len;
      var g = ctx.createLinearGradient(m.x, m.y, tx, ty);
      g.addColorStop(0, 'rgba(255,255,255,' + (0.75 * m.life).toFixed(2) + ')');
      g.addColorStop(0.3, 'rgba(165,180,252,' + (0.35 * m.life).toFixed(2) + ')');
      g.addColorStop(1, 'rgba(165,180,252,0)');
      ctx.strokeStyle = g;
      ctx.lineWidth = 1.6;
      ctx.beginPath();
      ctx.moveTo(m.x, m.y);
      ctx.lineTo(tx, ty);
      ctx.stroke();
    }
  }

  /* ---------- 主循环 ---------- */
  var running = false;
  function isDark() {
    return document.documentElement.getAttribute('data-theme') === 'dark';
  }

  function frame(t) {
    if (running) {
      if (isDark()) {
        ctx.clearRect(0, 0, W, H);

        // 星云微光
        for (var i = 0; i < nebulae.length; i++) {
          var n = nebulae[i];
          var g = ctx.createRadialGradient(n.x, n.y, 0, n.x, n.y, n.r);
          g.addColorStop(0, 'rgba(' + n.c + ',' + n.a + ')');
          g.addColorStop(1, 'rgba(' + n.c + ',0)');
          ctx.fillStyle = g;
          ctx.fillRect(n.x - n.r, n.y - n.r, n.r * 2, n.r * 2);
        }

        // 星星（闪烁 + 缓慢漂移）
        for (var s = 0; s < stars.length; s++) {
          var st = stars[s];
          st.x += st.drift;
          if (st.x > W + 2) st.x = -2;
          var a = st.base * (0.55 + 0.45 * Math.sin(t * 0.001 * st.speed + st.phase));
          ctx.fillStyle = 'rgba(' + st.hue + ',' + a.toFixed(2) + ')';
          ctx.beginPath();
          ctx.arc(st.x, st.y, st.r, 0, Math.PI * 2);
          ctx.fill();
        }

        drawPlanet(t);
        drawMeteors();
      }
      requestAnimationFrame(frame);
    }
  }

  function start() {
    if (!running) {
      running = true;
      requestAnimationFrame(frame);
    }
  }

  function buildScene() {
    buildStars();
    buildNebulae();
    buildPlanet();
  }

  window.addEventListener('resize', resize);

  // 主题切换时确保运行（无论何时切到暗色都能亮起来）
  new MutationObserver(function () { if (isDark()) start(); })
    .observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });

  // 随机流星节奏
  setInterval(function () {
    if (isDark() && running && Math.random() < 0.6) spawnMeteor();
  }, 5000);

  resize();
  if (isDark()) start(); else start(); // 循环常驻，暗色时才绘制
  spawnMeteor();
})();
