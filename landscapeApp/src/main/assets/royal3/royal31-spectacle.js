(function () {
  'use strict';

  var W = 1280, H = 720;
  var REEL_X = 226, REEL_Y = 157, CELL_W = 148, CELL_H = 142;
  var overlay, ctx, stageCanvas;
  var particles = [], rings = [], beams = [], sparks = [], texts = [], coins = [], lightning = [];
  var running = false, ready = false, spinActive = false, anticipation = false;
  var screenFlash = 0, vignette = 0, shake = 0, zoom = 1, targetZoom = 1, chroma = 0;
  var lastTime = performance.now(), elapsed = 0, result = null, spinStartedAt = 0;
  var timers = [];
  var palette = ['#ffd75e', '#65ecff', '#ff5bd6', '#8c6dff', '#ffffff', '#ff7f32'];

  function now() { return performance.now(); }
  function clamp(v, a, b) { return Math.max(a, Math.min(b, v)); }
  function rand(a, b) { return a + Math.random() * (b - a); }
  function choose(list) { return list[(Math.random() * list.length) | 0]; }
  function easeOutExpo(t) { return t === 1 ? 1 : 1 - Math.pow(2, -10 * t); }
  function easeOutBack(t) { var c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); }

  function schedule(fn, delay) {
    var id = setTimeout(fn, delay);
    timers.push(id);
    return id;
  }

  function clearTimers() {
    while (timers.length) clearTimeout(timers.pop());
  }

  function install() {
    if (document.getElementById('royal31-overlay')) return;
    overlay = document.createElement('canvas');
    overlay.id = 'royal31-overlay';
    overlay.width = W;
    overlay.height = H;
    overlay.setAttribute('aria-hidden', 'true');
    overlay.style.cssText = 'position:fixed;inset:0;width:100%;height:100%;z-index:4;pointer-events:none;mix-blend-mode:screen;opacity:0;transition:opacity .35s ease;';
    document.body.appendChild(overlay);
    ctx = overlay.getContext('2d', { alpha: true, desynchronized: true });

    var style = document.createElement('style');
    style.textContent =
      '#stage canvas{transform-origin:50% 50%;will-change:transform,filter;transition:filter .18s linear;}' +
      'body.royal31-active #stage canvas{filter:saturate(1.18) contrast(1.06);}' +
      'body.royal31-anticipation #stage canvas{filter:saturate(1.45) contrast(1.22) brightness(.68) drop-shadow(10px 0 0 rgba(255,30,135,.22)) drop-shadow(-10px 0 0 rgba(30,225,255,.22));}' +
      'body.royal31-win #stage canvas{filter:saturate(1.6) contrast(1.15) brightness(1.08) drop-shadow(8px 0 0 rgba(255,90,210,.22)) drop-shadow(-8px 0 0 rgba(60,230,255,.22));}';
    document.head.appendChild(style);

    stageCanvas = document.querySelector('#stage canvas');
    window.addEventListener('royal31:renderer-ready', function () {
      ready = true;
      overlay.style.opacity = '1';
      stageCanvas = document.querySelector('#stage canvas');
      introSequence();
    });
    window.addEventListener('royal31:spin-requested', onSpinRequested);
    window.addEventListener('royal31:spin-result', function (event) { onSpinResult(event.detail || {}); });
    window.addEventListener('royal31:renderer-error', function () { overlay.style.opacity = '0'; });

    window.addEventListener('pointerup', function (event) {
      if (spinActive || !ready) return;
      var x = event.clientX / Math.max(1, window.innerWidth) * W;
      var y = event.clientY / Math.max(1, window.innerHeight) * H;
      if (x > 1030 && y > 360 && y < 610) {
        energyTap(x, y);
        audio.ensure();
      }
    }, true);

    audio = new SpectacleAudio();
    running = true;
    requestAnimationFrame(frame);
  }

  function introSequence() {
    screenFlash = .32;
    radialBurst(640, 330, 70, '#7cecff', 1.2);
    for (var i = 0; i < 5; i++) {
      schedule(function (index) {
        return function () { reelBlade(index, '#ffd85f', .65); };
      }(i), 100 + i * 100);
    }
    floatingText('SPECTACLE 3.1', 640, 610, '#9eefff', 26, 1100);
  }

  function onSpinRequested() {
    clearTimers();
    spinActive = true;
    anticipation = false;
    result = null;
    spinStartedAt = now();
    document.body.classList.add('royal31-active');
    document.body.classList.remove('royal31-win', 'royal31-anticipation');
    targetZoom = 1.035;
    shake = 9;
    chroma = 1;
    screenFlash = .24;
    vignette = .38;
    energyTap(1147, 475);
    speedTunnel(1147, 475, 120);
    radialBurst(1147, 475, 90, '#ffb332', 1.8);
    audio.launch();
  }

  function onSpinResult(data) {
    result = data || {};
    if (data.error) {
      spinActive = false;
      targetZoom = 1;
      document.body.classList.remove('royal31-active');
      return;
    }

    anticipation = !!data.anticipation;
    var stopTimes = [];
    for (var i = 0; i < 5; i++) stopTimes.push(1600 + i * 260 + (anticipation && i === 4 ? 850 : 0) + i * 85);

    schedule(function () {
      speedTunnel(640, 360, 70);
      audio.reelRush();
    }, 430);

    if (anticipation) schedule(startAnticipation, 1700);

    for (var r = 0; r < 5; r++) {
      schedule(function (index) {
        return function () {
          reelImpact(index, index === 4);
          audio.reelStop(index, index === 4);
        };
      }(r), stopTimes[r]);
    }

    var finish = stopTimes[4] + 180;
    schedule(function () {
      spinActive = false;
      anticipation = false;
      document.body.classList.remove('royal31-active', 'royal31-anticipation');
      targetZoom = 1;
      vignette = 0;
      if ((data.payout || 0) > 0 || data.feature) winSequence(data);
      else nearMissExit();
    }, finish);
  }

  function energyTap(x, y) {
    addRing(x, y, '#ffe172', 25, 300, 7, .7);
    addRing(x, y, '#ff6e2e', 40, 430, 3, .45);
    radialBurst(x, y, 38, '#ffd45c', 1.1);
  }

  function speedTunnel(cx, cy, count) {
    for (var i = 0; i < count; i++) {
      var angle = rand(0, Math.PI * 2);
      var inner = rand(30, 190);
      var length = rand(45, 260);
      beams.push({
        x1: cx + Math.cos(angle) * inner,
        y1: cy + Math.sin(angle) * inner,
        x2: cx + Math.cos(angle) * (inner + length),
        y2: cy + Math.sin(angle) * (inner + length),
        life: rand(.25, .75), max: rand(.25, .75), width: rand(1, 5), color: choose(palette)
      });
    }
  }

  function reelImpact(index, last) {
    var x = REEL_X + CELL_W * (index + .5);
    var y = REEL_Y + CELL_H * 1.5;
    reelBlade(index, last ? '#fff0a0' : '#71ecff', last ? 1.2 : .8);
    addRing(x, y, last ? '#ffd75f' : '#5eeaff', 35, last ? 530 : 360, last ? 10 : 6, .9);
    radialBurst(x, y, last ? 85 : 42, last ? '#ffd75f' : '#65eaff', last ? 2.1 : 1.2);
    screenFlash = Math.max(screenFlash, last ? .32 : .13);
    shake = Math.max(shake, last ? 18 : 10);
    chroma = Math.max(chroma, last ? 1.4 : .65);
    targetZoom = last ? 1.055 : 1.025;
    schedule(function () { targetZoom = anticipation && index === 4 ? 1.045 : 1.015; }, 120);
    if (last) {
      for (var j = 0; j < 5; j++) {
        schedule(function () { lightningArc(x, y, rand(120, 230), choose(['#73efff', '#ffe272'])); }, j * 45);
      }
    }
  }

  function reelBlade(index, color, strength) {
    var x = REEL_X + CELL_W * (index + .5);
    beams.push({ x1: x, y1: REEL_Y - 90, x2: x, y2: REEL_Y + CELL_H * 3 + 90, life: .36, max: .36, width: 18 * strength, color: color, blade: true });
    beams.push({ x1: x - 34, y1: REEL_Y - 40, x2: x + 34, y2: REEL_Y + CELL_H * 3 + 40, life: .25, max: .25, width: 4, color: '#ffffff', blade: true });
  }

  function startAnticipation() {
    if (!spinActive) return;
    anticipation = true;
    document.body.classList.add('royal31-anticipation');
    targetZoom = 1.075;
    vignette = .82;
    floatingText('ROYAL CHANCE', 640, 90, '#fff0a4', 43, 1900);
    floatingText('ÚLTIMO CARRETE', 640, 132, '#79efff', 18, 1750);
    audio.anticipation();

    var x = REEL_X + CELL_W * 4.5;
    var y = REEL_Y + CELL_H * 1.5;
    for (var i = 0; i < 12; i++) {
      schedule(function () {
        lightningArc(x, y, rand(120, 330), choose(['#62eaff', '#ffd868', '#ff5bd2']));
        addRing(x, y, choose(['#62eaff', '#ffd868']), rand(20, 70), rand(260, 520), rand(2, 7), .55);
        shake = Math.max(shake, 4);
      }, i * 125);
    }
    schedule(function () { speedTunnel(x, y, 60); }, 550);
  }

  function winSequence(data) {
    var amount = Number(data.payout || 0);
    var feature = !!data.feature;
    var tier = feature ? 3 : amount > 1800 ? 2 : amount > 600 ? 1 : 0;
    var title = feature ? 'FREE SPINS' : tier === 2 ? 'ROYAL WIN' : tier === 1 ? 'BIG WIN' : 'WIN';
    document.body.classList.add('royal31-win');
    screenFlash = feature ? .48 : .38;
    vignette = .25;
    targetZoom = 1.085;
    shake = tier >= 2 || feature ? 22 : 13;
    chroma = tier >= 2 || feature ? 1.8 : 1.1;
    audio.win(amount, feature, tier);

    crownPortal(605, 350, feature ? '#6feaff' : '#ffd75c', tier);
    radialBurst(605, 350, feature ? 260 : 140 + tier * 50, feature ? '#66ecff' : '#ffd65c', 2 + tier * .65);
    coinStorm(feature ? 170 : 80 + tier * 55);
    fireworkFan(feature ? 8 : 4 + tier * 2);
    showHeroText(title, amount, feature, tier);

    for (var i = 0; i < 8 + tier * 4; i++) {
      schedule(function () {
        addRing(605, 350, choose(['#ffd75c', '#69eaff', '#ff58d0', '#ffffff']), rand(35, 110), rand(380, 800), rand(2, 9), .72);
      }, i * 95);
    }

    schedule(function () { targetZoom = 1.02; }, 700);
    schedule(function () {
      document.body.classList.remove('royal31-win');
      targetZoom = 1;
      vignette = 0;
    }, feature ? 5200 : 3900 + tier * 550);
  }

  function nearMissExit() {
    floatingText('TRY AGAIN', 605, 630, '#b9c5da', 19, 800);
    targetZoom = 1;
    vignette = 0;
  }

  function showHeroText(title, amount, feature, tier) {
    texts.push({
      text: title,
      sub: feature ? 'BONUS ACTIVADO' : amount.toLocaleString('es-CL') + ' CR',
      x: 605, y: 340, life: feature ? 4.8 : 3.4 + tier * .45, max: feature ? 4.8 : 3.4 + tier * .45,
      size: feature ? 78 : 82 + tier * 12,
      color: feature ? '#9ff7ff' : '#fff0a0',
      hero: true, tier: tier
    });
  }

  function floatingText(text, x, y, color, size, duration) {
    texts.push({ text: text, x: x, y: y, life: duration / 1000, max: duration / 1000, size: size, color: color, hero: false });
  }

  function crownPortal(x, y, color, tier) {
    for (var i = 0; i < 3 + tier; i++) {
      rings.push({ x: x, y: y, r: 70 + i * 24, target: 430 + i * 70, life: 1.5 + i * .18, max: 1.5 + i * .18, width: 9 - i, color: color, spin: (i % 2 ? -1 : 1) * rand(.6, 1.8), arc: true });
    }
    for (var p = 0; p < 10; p++) {
      sparks.push({ x: x, y: y, angle: p / 10 * Math.PI * 2, radius: 80, speed: rand(80, 190), life: 2.4, max: 2.4, color: choose(['#ffd85c', '#68ebff', '#ffffff']) });
    }
  }

  function fireworkFan(count) {
    for (var i = 0; i < count; i++) {
      schedule(function (index) {
        return function () {
          var x = 180 + (index % 5) * 230 + rand(-40, 40);
          var y = rand(100, 340);
          radialBurst(x, y, 45, choose(palette), 1.5);
          addRing(x, y, choose(palette), 12, 220, 4, .5);
          audio.sparkle(index);
        };
      }(i), i * 220);
    }
  }

  function coinStorm(count) {
    for (var i = 0; i < count; i++) {
      coins.push({
        x: rand(20, W - 20), y: rand(-500, -20), vx: rand(-70, 70), vy: rand(130, 470),
        rot: rand(0, Math.PI * 2), spin: rand(-10, 10), size: rand(5, 14), life: rand(2.2, 5),
        color: Math.random() > .25 ? '#ffd34e' : choose(['#6cecff', '#ff72db', '#ffffff'])
      });
    }
  }

  function radialBurst(x, y, count, color, power) {
    for (var i = 0; i < count; i++) {
      var angle = rand(0, Math.PI * 2);
      var speed = rand(90, 360) * power;
      particles.push({
        x: x + rand(-12, 12), y: y + rand(-12, 12), vx: Math.cos(angle) * speed,
        vy: Math.sin(angle) * speed - rand(0, 85), gravity: rand(80, 240),
        life: rand(.45, 1.7), max: 1.7, size: rand(1.5, 8), color: Math.random() > .22 ? color : '#ffffff',
        trail: Math.random() > .55, spin: rand(-8, 8), rot: rand(0, 6.28)
      });
    }
    trimCollections();
  }

  function addRing(x, y, color, start, target, width, alpha) {
    rings.push({ x: x, y: y, r: start, target: target, life: .72, max: .72, width: width, color: color, alpha: alpha, spin: 0, arc: false });
  }

  function lightningArc(x, y, radius, color) {
    var pts = [];
    var start = rand(0, Math.PI * 2);
    var segments = 10 + ((Math.random() * 8) | 0);
    for (var i = 0; i <= segments; i++) {
      var angle = start + i / segments * rand(1.1, 2.7);
      var rr = radius * (.55 + i / segments * .45) + rand(-24, 24);
      pts.push({ x: x + Math.cos(angle) * rr, y: y + Math.sin(angle) * rr });
    }
    lightning.push({ pts: pts, life: .22, max: .22, color: color, width: rand(2, 5) });
  }

  function trimCollections() {
    if (particles.length > 650) particles.splice(0, particles.length - 650);
    if (coins.length > 260) coins.splice(0, coins.length - 260);
    if (rings.length > 80) rings.splice(0, rings.length - 80);
  }

  function frame(time) {
    if (!running) return;
    var dt = Math.min(.034, Math.max(.001, (time - lastTime) / 1000));
    lastTime = time;
    elapsed += dt;
    update(dt);
    draw();
    requestAnimationFrame(frame);
  }

  function update(dt) {
    zoom += (targetZoom - zoom) * Math.min(1, dt * 7.5);
    shake *= Math.pow(.04, dt);
    chroma *= Math.pow(.08, dt);
    screenFlash *= Math.pow(.025, dt);

    if (stageCanvas) {
      var sx = shake > .2 ? rand(-shake, shake) : 0;
      var sy = shake > .2 ? rand(-shake * .55, shake * .55) : 0;
      stageCanvas.style.transform = 'translate(' + sx.toFixed(2) + 'px,' + sy.toFixed(2) + 'px) scale(' + zoom.toFixed(4) + ')';
    }

    updateList(particles, dt, function (p) {
      p.life -= dt; p.vy += p.gravity * dt; p.x += p.vx * dt; p.y += p.vy * dt; p.rot += p.spin * dt;
      p.vx *= Math.pow(.58, dt); p.vy *= Math.pow(.86, dt);
    });
    updateList(coins, dt, function (c) {
      c.life -= dt; c.vy += 260 * dt; c.x += c.vx * dt; c.y += c.vy * dt; c.rot += c.spin * dt;
      if (c.y > H + 40) c.life = 0;
    });
    updateList(rings, dt, function (r) {
      r.life -= dt; r.r += (r.target - r.r) * Math.min(1, dt * 5); if (r.spin) r.spin += dt;
    });
    updateList(beams, dt, function (b) { b.life -= dt; });
    updateList(lightning, dt, function (l) { l.life -= dt; });
    updateList(texts, dt, function (t) { t.life -= dt; });
    updateList(sparks, dt, function (s) { s.life -= dt; s.radius += s.speed * dt; s.angle += dt * 1.2; });
  }

  function updateList(list, dt, fn) {
    for (var i = list.length - 1; i >= 0; i--) {
      fn(list[i], dt);
      if (list[i].life <= 0) list.splice(i, 1);
    }
  }

  function draw() {
    if (!ctx) return;
    ctx.clearRect(0, 0, W, H);
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';

    drawBeams();
    drawRings();
    drawLightning();
    drawParticles();
    drawCoins();
    drawSparks();
    drawTexts();

    ctx.restore();
    drawCinema();
  }

  function drawBeams() {
    for (var i = 0; i < beams.length; i++) {
      var b = beams[i], a = clamp(b.life / b.max, 0, 1);
      ctx.strokeStyle = rgba(b.color, a * (b.blade ? .72 : .55));
      ctx.lineWidth = b.width * (.35 + a);
      ctx.shadowColor = b.color; ctx.shadowBlur = b.blade ? 28 : 14;
      ctx.beginPath(); ctx.moveTo(b.x1, b.y1); ctx.lineTo(b.x2, b.y2); ctx.stroke();
    }
    ctx.shadowBlur = 0;
  }

  function drawRings() {
    for (var i = 0; i < rings.length; i++) {
      var r = rings[i], a = clamp(r.life / r.max, 0, 1);
      ctx.strokeStyle = rgba(r.color, a * (r.alpha || .8));
      ctx.lineWidth = Math.max(.6, r.width * a);
      ctx.shadowColor = r.color; ctx.shadowBlur = 20;
      ctx.beginPath();
      if (r.arc) {
        var start = elapsed * r.spin;
        ctx.arc(r.x, r.y, r.r, start, start + Math.PI * 1.35);
      } else ctx.arc(r.x, r.y, r.r, 0, Math.PI * 2);
      ctx.stroke();
    }
    ctx.shadowBlur = 0;
  }

  function drawLightning() {
    for (var i = 0; i < lightning.length; i++) {
      var l = lightning[i], a = clamp(l.life / l.max, 0, 1);
      if (!l.pts.length) continue;
      ctx.strokeStyle = rgba(l.color, a);
      ctx.lineWidth = l.width * a;
      ctx.shadowColor = l.color; ctx.shadowBlur = 22;
      ctx.beginPath(); ctx.moveTo(l.pts[0].x, l.pts[0].y);
      for (var p = 1; p < l.pts.length; p++) ctx.lineTo(l.pts[p].x, l.pts[p].y);
      ctx.stroke();
    }
    ctx.shadowBlur = 0;
  }

  function drawParticles() {
    for (var i = 0; i < particles.length; i++) {
      var p = particles[i], a = clamp(p.life / Math.min(p.max, .8), 0, 1);
      ctx.save(); ctx.translate(p.x, p.y); ctx.rotate(p.rot);
      ctx.fillStyle = rgba(p.color, a);
      ctx.shadowColor = p.color; ctx.shadowBlur = 12;
      if (p.trail) {
        ctx.fillRect(-p.size * 4, -p.size * .5, p.size * 5, p.size);
      } else {
        ctx.beginPath(); ctx.arc(0, 0, p.size * (.55 + a), 0, Math.PI * 2); ctx.fill();
      }
      ctx.restore();
    }
    ctx.shadowBlur = 0;
  }

  function drawCoins() {
    for (var i = 0; i < coins.length; i++) {
      var c = coins[i], a = clamp(c.life / .7, 0, 1);
      ctx.save(); ctx.translate(c.x, c.y); ctx.rotate(c.rot);
      var squash = .25 + .75 * Math.abs(Math.cos(c.rot));
      ctx.scale(squash, 1);
      ctx.fillStyle = rgba(c.color, a);
      ctx.strokeStyle = rgba('#ffffff', a * .75);
      ctx.lineWidth = 1.4;
      ctx.shadowColor = c.color; ctx.shadowBlur = 10;
      ctx.beginPath(); ctx.arc(0, 0, c.size, 0, Math.PI * 2); ctx.fill(); ctx.stroke();
      ctx.beginPath(); ctx.arc(0, 0, c.size * .58, 0, Math.PI * 2); ctx.stroke();
      ctx.restore();
    }
    ctx.shadowBlur = 0;
  }

  function drawSparks() {
    for (var i = 0; i < sparks.length; i++) {
      var s = sparks[i], a = clamp(s.life / s.max, 0, 1);
      var x = s.x + Math.cos(s.angle) * s.radius;
      var y = s.y + Math.sin(s.angle) * s.radius;
      ctx.fillStyle = rgba(s.color, a);
      ctx.shadowColor = s.color; ctx.shadowBlur = 18;
      ctx.beginPath(); ctx.arc(x, y, 4 + 7 * a, 0, Math.PI * 2); ctx.fill();
    }
    ctx.shadowBlur = 0;
  }

  function drawTexts() {
    for (var i = 0; i < texts.length; i++) {
      var t = texts[i], progress = 1 - t.life / t.max;
      var alpha = Math.min(1, (1 - progress) * 4, progress * 7);
      var scale = t.hero ? easeOutBack(clamp(progress * 2.4, 0, 1)) : easeOutExpo(clamp(progress * 3, 0, 1));
      var lift = t.hero ? 0 : progress * 18;
      ctx.save(); ctx.translate(t.x, t.y - lift); ctx.scale(scale, scale);
      ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.font = '900 ' + t.size + 'px Arial Black, Arial, sans-serif';
      ctx.lineWidth = t.hero ? 12 : 5;
      ctx.strokeStyle = rgba(t.hero ? '#57106f' : '#071020', alpha);
      ctx.fillStyle = rgba(t.color, alpha);
      ctx.shadowColor = t.color; ctx.shadowBlur = t.hero ? 40 : 20;
      ctx.strokeText(t.text, 0, 0); ctx.fillText(t.text, 0, 0);
      if (t.sub) {
        ctx.font = '800 ' + Math.max(20, t.size * .30) + 'px Arial, sans-serif';
        ctx.fillStyle = rgba('#ffffff', alpha * .95);
        ctx.shadowBlur = 16;
        ctx.fillText(t.sub, 0, t.size * .72);
      }
      ctx.restore();
    }
    ctx.shadowBlur = 0;
  }

  function drawCinema() {
    if (vignette > .01) {
      var gradient = ctx.createRadialGradient(640, 340, 170, 640, 340, 760);
      gradient.addColorStop(0, 'rgba(0,0,0,0)');
      gradient.addColorStop(.56, 'rgba(0,0,0,' + (vignette * .18) + ')');
      gradient.addColorStop(1, 'rgba(0,0,0,' + vignette + ')');
      ctx.fillStyle = gradient; ctx.fillRect(0, 0, W, H);
    }

    if (anticipation) {
      var pulse = .5 + .5 * Math.sin(elapsed * 8);
      ctx.fillStyle = 'rgba(0,0,0,' + (.12 + pulse * .08) + ')';
      ctx.fillRect(0, 0, W, 42 + pulse * 14);
      ctx.fillRect(0, H - 42 - pulse * 14, W, 42 + pulse * 14);
      ctx.strokeStyle = 'rgba(90,235,255,' + (.35 + pulse * .35) + ')';
      ctx.lineWidth = 3 + pulse * 4;
      ctx.strokeRect(REEL_X + CELL_W * 4 + 4, REEL_Y + 4, CELL_W - 8, CELL_H * 3 - 8);
    }

    if (chroma > .03) {
      ctx.globalCompositeOperation = 'screen';
      ctx.fillStyle = 'rgba(255,0,120,' + (.018 * chroma) + ')'; ctx.fillRect(chroma * 4, 0, W, H);
      ctx.fillStyle = 'rgba(0,220,255,' + (.018 * chroma) + ')'; ctx.fillRect(-chroma * 4, 0, W, H);
      ctx.globalCompositeOperation = 'source-over';
    }

    if (screenFlash > .005) {
      ctx.fillStyle = 'rgba(255,250,220,' + clamp(screenFlash, 0, .65) + ')';
      ctx.fillRect(0, 0, W, H);
    }
  }

  function rgba(hex, alpha) {
    if (!hex || hex.charAt(0) !== '#') return hex;
    var raw = hex.slice(1);
    if (raw.length === 3) raw = raw.charAt(0)+raw.charAt(0)+raw.charAt(1)+raw.charAt(1)+raw.charAt(2)+raw.charAt(2);
    var n = parseInt(raw, 16);
    return 'rgba(' + ((n >> 16) & 255) + ',' + ((n >> 8) & 255) + ',' + (n & 255) + ',' + clamp(alpha, 0, 1) + ')';
  }

  function SpectacleAudio() {
    this.ctx = null;
    this.master = null;
  }

  SpectacleAudio.prototype.ensure = function () {
    if (this.ctx) {
      if (this.ctx.state === 'suspended') this.ctx.resume();
      return true;
    }
    try {
      var AudioContextCtor = window.AudioContext || window.webkitAudioContext;
      if (!AudioContextCtor) return false;
      this.ctx = new AudioContextCtor({ latencyHint: 'interactive' });
      this.master = this.ctx.createGain();
      this.master.gain.value = .24;
      this.master.connect(this.ctx.destination);
      return true;
    } catch (ignored) { return false; }
  };

  SpectacleAudio.prototype.tone = function (frequency, duration, delay, type, gain, endFrequency) {
    if (!this.ensure()) return;
    var t = this.ctx.currentTime + (delay || 0);
    var oscillator = this.ctx.createOscillator();
    var envelope = this.ctx.createGain();
    oscillator.type = type || 'sine';
    oscillator.frequency.setValueAtTime(Math.max(25, frequency), t);
    if (endFrequency) oscillator.frequency.exponentialRampToValueAtTime(Math.max(25, endFrequency), t + duration);
    envelope.gain.setValueAtTime(.0001, t);
    envelope.gain.exponentialRampToValueAtTime(gain || .08, t + .015);
    envelope.gain.exponentialRampToValueAtTime(.0001, t + duration);
    oscillator.connect(envelope); envelope.connect(this.master);
    oscillator.start(t); oscillator.stop(t + duration + .04);
  };

  SpectacleAudio.prototype.noise = function (duration, delay, gain, cutoff, sweep) {
    if (!this.ensure()) return;
    var sr = this.ctx.sampleRate, length = Math.max(1, Math.floor(sr * duration));
    var buffer = this.ctx.createBuffer(1, length, sr), data = buffer.getChannelData(0);
    for (var i = 0; i < length; i++) data[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / length, .45);
    var source = this.ctx.createBufferSource(), filter = this.ctx.createBiquadFilter(), envelope = this.ctx.createGain();
    var t = this.ctx.currentTime + (delay || 0);
    source.buffer = buffer; filter.type = 'bandpass'; filter.frequency.setValueAtTime(cutoff || 900, t);
    if (sweep) filter.frequency.exponentialRampToValueAtTime(Math.max(60, sweep), t + duration);
    filter.Q.value = .65;
    envelope.gain.setValueAtTime(gain || .08, t); envelope.gain.exponentialRampToValueAtTime(.0001, t + duration);
    source.connect(filter); filter.connect(envelope); envelope.connect(this.master); source.start(t);
  };

  SpectacleAudio.prototype.launch = function () {
    this.noise(1.3, 0, .11, 280, 5200);
    this.tone(38, .9, 0, 'sine', .18, 92);
    this.tone(130, .42, .04, 'sawtooth', .06, 880);
    this.tone(55, .22, .02, 'triangle', .15, 32);
  };

  SpectacleAudio.prototype.reelRush = function () {
    this.noise(.75, 0, .06, 1400, 3500);
    this.tone(170, .55, 0, 'triangle', .045, 760);
  };

  SpectacleAudio.prototype.reelStop = function (index, last) {
    var base = 72 + index * 18;
    this.tone(base, .25, 0, 'sine', last ? .20 : .11, Math.max(34, base - 35));
    this.tone(880 + index * 150, .075, 0, 'square', last ? .07 : .035, 240);
    this.noise(.12, 0, last ? .10 : .05, 1200 + index * 210, 350);
    if (last) this.tone(42, .55, 0, 'sine', .24, 28);
  };

  SpectacleAudio.prototype.anticipation = function () {
    for (var i = 0; i < 7; i++) {
      this.tone(105 + i * 25, .19, i * .18, 'sine', .065 + i * .006, 155 + i * 34);
      this.tone(45, .13, i * .18, 'sine', .09, 38);
    }
    this.noise(1.4, .15, .045, 460, 3200);
  };

  SpectacleAudio.prototype.win = function (amount, feature, tier) {
    var notes = feature ? [196,247,294,392,494,588,784] : tier >= 2 ? [147,196,247,294,392,494,659] : tier === 1 ? [196,247,330,392,494] : [220,277,330,440];
    for (var i = 0; i < notes.length; i++) {
      this.tone(notes[i], .45, i * .09, 'triangle', .10 + tier * .012, notes[i] * 1.45);
      this.tone(notes[i] / 2, .55, i * .09, 'sine', .07, notes[i] * .7);
    }
    this.tone(36, .85, 0, 'sine', .23, 28);
    this.noise(.55, .02, .09, 2600, 6500);
  };

  SpectacleAudio.prototype.sparkle = function (index) {
    this.tone(900 + index * 90, .13, 0, 'sine', .028, 1500 + index * 80);
  };

  var audio;
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', install);
  else install();
})();
