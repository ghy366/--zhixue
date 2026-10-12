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

  /* ---------- 极光光带（缓慢流动） ---------- */
  var auroraBands = [];
  function buildAurora() {
    auroraBands = [
      { y: H * 0.15, amp: 34, len: 0.9, speed: 0.00016, thick: 150, c: '99,102,241', a: 0.09, ph: 0 },
      { y: H * 0.23, amp: 46, len: 0.6, speed: 0.00011, thick: 200, c: '34,211,238', a: 0.06, ph: 2.1 },
      { y: H * 0.09, amp: 28, len: 1.3, speed: 0.00021, thick: 120, c: '168,85,247', a: 0.07, ph: 4.4 }
    ];
  }
  function drawAurora(t) {
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    ctx.lineCap = 'round';
    for (var i = 0; i < auroraBands.length; i++) {
      var b = auroraBands[i];
      var g = ctx.createLinearGradient(0, b.y - b.thick / 2, 0, b.y + b.thick / 2);
      g.addColorStop(0, 'rgba(' + b.c + ',0)');
      g.addColorStop(0.5, 'rgba(' + b.c + ',' + b.a + ')');
      g.addColorStop(1, 'rgba(' + b.c + ',0)');
      ctx.strokeStyle = g;
      ctx.lineWidth = b.thick;
      ctx.beginPath();
      for (var x = -40; x <= W + 40; x += 26) {
        var y = b.y
          + Math.sin(x * 0.004 * b.len + t * b.speed + b.ph) * b.amp
          + Math.sin(x * 0.0011 * b.len - t * b.speed * 0.7 + b.ph * 2) * b.amp * 0.5;
        if (x === -40) ctx.moveTo(x, y); else ctx.lineTo(x, y);
      }
      ctx.stroke();
    }
    ctx.restore();
  }

  /* ---------- 星座连线（亮星之间） ---------- */
  var constLines = [];
  function buildConstellation() {
    constLines = [];
    var bright = stars.filter(function (s) { return s.r > 1.15; });
    for (var i = 0; i < bright.length; i++) {
      for (var j = i + 1; j < bright.length; j++) {
        var dx = bright[i].x - bright[j].x, dy = bright[i].y - bright[j].y;
        if (dx * dx + dy * dy < 130 * 130 && Math.random() < 0.45) {
          constLines.push([bright[i], bright[j]]);
          if (constLines.length > 26) return;
        }
      }
    }
  }
  function drawConstellation() {
    if (!constLines.length) return;
    ctx.save();
    ctx.strokeStyle = 'rgba(148,163,184,0.10)';
    ctx.lineWidth = 0.6;
    for (var i = 0; i < constLines.length; i++) {
      ctx.beginPath();
      ctx.moveTo(constLines[i][0].x, constLines[i][0].y);
      ctx.lineTo(constLines[i][1].x, constLines[i][1].y);
      ctx.stroke();
    }
    ctx.restore();
  }

  /* ---------- 主题星球（云带大气 + 行星环 + 晨昏线） ---------- */
  var planet = { x: 0, y: 0, r: 0, bands: [], spots: [] };
  function buildPlanet() {
    planet.x = W * 0.86;
    planet.y = H * 0.15;
    planet.r = Math.min(W, H) * 0.075;
    // 横向云带（弧线色带，模拟木星/海王星条纹）
    planet.bands = [];
    var nb = 6;
    for (var i = 0; i < nb; i++) {
      planet.bands.push({
        y: -planet.r + (i + 0.25 + Math.random() * 0.6) * (planet.r * 2 / nb),
        amp: planet.r * (0.03 + Math.random() * 0.09),
        h: planet.r * (0.07 + Math.random() * 0.12),
        hue: Math.random() < 0.5 ? '165,180,252' : (Math.random() < 0.5 ? '103,232,249' : '196,181,253'),
        a: 0.12 + Math.random() * 0.20,
        ph: Math.random() * Math.PI * 2,
        drift: 0.0009 + Math.random() * 0.0016
      });
    }
    // 表面光斑（风暴/暗斑）
    planet.spots = [];
    for (var j = 0; j < 8; j++) {
      var ang = Math.random() * Math.PI * 2;
      var dist = Math.sqrt(Math.random()) * planet.r * 0.88;
      planet.spots.push({
        dx: Math.cos(ang) * dist,
        dy: Math.sin(ang) * dist * 0.8,
        r: planet.r * (0.05 + Math.random() * 0.13),
        a: 0.05 + Math.random() * 0.09
      });
    }
  }

  function drawPlanet(t) {
    var p = planet;
    ctx.save();
    ctx.translate(p.x, p.y);

    // ① 大气外层辉光（大范围紫蓝光晕）
    var glow = ctx.createRadialGradient(0, 0, p.r * 0.5, 0, 0, p.r * 3.2);
    glow.addColorStop(0, 'rgba(139,148,255,0.30)');
    glow.addColorStop(0.35, 'rgba(99,102,241,0.13)');
    glow.addColorStop(0.7, 'rgba(99,102,241,0.04)');
    glow.addColorStop(1, 'rgba(99,102,241,0)');
    ctx.fillStyle = glow;
    ctx.beginPath();
    ctx.arc(0, 0, p.r * 3.2, 0, Math.PI * 2);
    ctx.fill();

    // ② 行星环 —— 后段（上半弧在星球后面），先画，更淡
    ctx.save();
    ctx.scale(1, 0.34);
    ctx.rotate(-0.12);
    var ringR = p.r * 1.62;
    var lines = [0, 0.16, 0.30];   // 三条同心环，0 为内圈
    for (var lg = 0; lg < lines.length; lg++) {
      var rr = ringR + lines[lg] * p.r * 0.42;
      var lw = lg === 0 ? p.r * 0.20 : p.r * 0.07;
      // 环透明度随半径变化：内圈略亮
      var baseA = lg === 0 ? 0.34 : 0.22;
      // 上（后）半弧
      ctx.strokeStyle = 'rgba(196,181,253,' + (baseA * 0.55).toFixed(2) + ')';
      ctx.lineWidth = lw;
      ctx.beginPath();
      ctx.arc(0, 0, rr, Math.PI, Math.PI * 2);
      ctx.stroke();
    }
    ctx.restore();

    // ③ 星球本体：基础大气球体
    var body = ctx.createRadialGradient(-p.r * 0.4, -p.r * 0.4, p.r * 0.1, 0, 0, p.r);
    body.addColorStop(0, 'rgba(203,213,255,0.96)');
    body.addColorStop(0.25, 'rgba(140,148,250,0.94)');
    body.addColorStop(0.6, 'rgba(79,70,229,0.92)');
    body.addColorStop(0.85, 'rgba(49,46,129,0.96)');
    body.addColorStop(1, 'rgba(20,16,60,0.98)');
    ctx.fillStyle = body;
    ctx.beginPath();
    ctx.arc(0, 0, p.r, 0, Math.PI * 2);
    ctx.fill();

    // ④ 云带纹理（clip 在球体内，横向弧带缓慢流动）
    ctx.save();
    ctx.beginPath();
    ctx.arc(0, 0, p.r, 0, Math.PI * 2);
    ctx.clip();
    for (var bi = 0; bi < p.bands.length; bi++) {
      var b = p.bands[bi];
      ctx.beginPath();
      var segs = 26;
      var startedB = false;
      for (var sx = -p.r; sx <= p.r; sx += (p.r * 2) / segs) {
        var wave = Math.sin(sx * 0.02 + t * b.drift * 1.0 + b.ph) * b.amp;
        var by = b.y + wave;
        if (!startedB) { ctx.moveTo(sx, by); startedB = true; } else ctx.lineTo(sx, by);
      }
      ctx.strokeStyle = 'rgba(' + b.hue + ',' + b.a.toFixed(2) + ')';
      ctx.lineWidth = b.h;
      ctx.lineCap = 'round';
      ctx.stroke();
    }
    // 表面光斑
    for (var si = 0; si < p.spots.length; si++) {
      var sp = p.spots[si];
      ctx.fillStyle = 'rgba(15,12,50,' + sp.a.toFixed(2) + ')';
      ctx.beginPath();
      ctx.ellipse(sp.dx, sp.dy, sp.r * 1.4, sp.r, 0, 0, Math.PI * 2);
      ctx.fill();
    }
    ctx.restore();

    // ⑤ 晨昏线：光从左上来，右下转暗（弯月立体感）
    ctx.save();
    ctx.beginPath();
    ctx.arc(0, 0, p.r, 0, Math.PI * 2);
    ctx.clip();
    var sh = ctx.createRadialGradient(-p.r * 0.5, -p.r * 0.5, p.r * 0.1, p.r * 0.25, p.r * 0.25, p.r * 1.7);
    sh.addColorStop(0, 'rgba(226,232,255,0.20)');
    sh.addColorStop(0.5, 'rgba(2,6,23,0.18)');
    sh.addColorStop(1, 'rgba(2,6,23,0.80)');
    ctx.fillStyle = sh;
    ctx.fillRect(-p.r, -p.r, p.r * 2, p.r * 2);
    ctx.restore();

    // ⑥ 受光侧 rim 高光（左上月牙）
    ctx.strokeStyle = 'rgba(224,231,255,0.55)';
    ctx.lineWidth = 1.6;
    ctx.beginPath();
    ctx.arc(0, 0, p.r - 0.9, Math.PI * 0.85, Math.PI * 1.7);
    ctx.stroke();

    // ⑦ 行星环 —— 前段（下半弧在星球前面，更亮）
    ctx.save();
    ctx.scale(1, 0.34);
    ctx.rotate(-0.12);
    ringR = p.r * 1.62;
    for (var lg2 = 0; lg2 < lines.length; lg2++) {
      var rr2 = ringR + lines[lg2] * p.r * 0.42;
      var lw2 = lg2 === 0 ? p.r * 0.20 : p.r * 0.07;
      var baseA2 = lg2 === 0 ? 0.34 : 0.22;
      // 渐变：环内侧亮、两端淡
      ctx.strokeStyle = 'rgba(196,181,253,' + baseA2.toFixed(2) + ')';
      ctx.lineWidth = lw2;
      ctx.beginPath();
      ctx.arc(0, 0, rr2, 0, Math.PI);
      ctx.stroke();
    }
    ctx.restore();

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

        // 深空底色：顶部偏紫、底部深邃
        var base = ctx.createLinearGradient(0, 0, 0, H);
        base.addColorStop(0, 'rgba(30,24,66,0.50)');
        base.addColorStop(0.5, 'rgba(12,16,40,0.22)');
        base.addColorStop(1, 'rgba(4,8,24,0.42)');
        ctx.fillStyle = base;
        ctx.fillRect(0, 0, W, H);

        // 极光光带
        drawAurora(t);

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

        // 星座连线
        drawConstellation();

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
    buildAurora();
    buildConstellation();
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
