(function () {
  'use strict';

  if (window.__ROYAL32_PERFORMANCE__) return;
  window.__ROYAL32_PERFORMANCE__ = true;

  var PIXI = window.PIXI;
  if (!PIXI || !PIXI.Application || !PIXI.Application.prototype) return;

  var isMobile = /Android|Mobile|iPhone|iPad/i.test(navigator.userAgent || '');
  var originalInit = PIXI.Application.prototype.init;

  PIXI.Application.prototype.init = function (options) {
    var optimized = {};
    var key;
    options = options || {};
    for (key in options) {
      if (Object.prototype.hasOwnProperty.call(options, key)) optimized[key] = options[key];
    }

    optimized.antialias = false;
    optimized.autoDensity = true;
    optimized.powerPreference = 'high-performance';
    optimized.resolution = Math.min(
      Number(options.resolution) || 1,
      isMobile ? 0.90 : 1.0
    );

    var app = this;
    return originalInit.call(this, optimized).then(function (result) {
      if (app.ticker) {
        app.ticker.maxFPS = 60;
        app.ticker.minFPS = 30;
      }
      try {
        if (app.renderer) app.renderer.roundPixels = true;
      } catch (ignored) {}
      return result;
    });
  };

  try {
    if (PIXI.filters && PIXI.filters.AdvancedBloomFilter) {
      PIXI.filters.AdvancedBloomFilter = undefined;
    }
    if (PIXI.AdvancedBloomFilter) PIXI.AdvancedBloomFilter = undefined;
  } catch (ignored) {}

  document.documentElement.classList.add('royal32-fluid-mode');
}());
