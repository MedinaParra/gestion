(function () {
  'use strict';

  var base = window.RoyalNative;
  if (!base || window.__ROYAL31_BRIDGE__) return;

  function emit(name, detail) {
    try {
      var event;
      if (typeof window.CustomEvent === 'function') {
        event = new CustomEvent(name, { detail: detail });
      } else {
        event = document.createEvent('CustomEvent');
        event.initCustomEvent(name, false, false, detail);
      }
      window.dispatchEvent(event);
    } catch (ignored) {}
  }

  function call(name, args, fallback) {
    try {
      if (base && typeof base[name] === 'function') {
        return base[name].apply(base, args || []);
      }
    } catch (error) {
      emit('royal31:native-error', { method: name, error: String(error) });
    }
    return fallback;
  }

  var wrapped = {
    getState: function () {
      return call('getState', arguments, '{}');
    },
    changeBet: function (delta) {
      var raw = call('changeBet', [delta], '{}');
      try { emit('royal31:state', JSON.parse(raw)); } catch (ignored) {}
      return raw;
    },
    requestSpin: function () {
      emit('royal31:spin-requested', { at: Date.now() });
      var raw = call('requestSpin', arguments, '{"error":"NO RESPONSE"}');
      try { emit('royal31:spin-result', JSON.parse(raw)); }
      catch (error) { emit('royal31:native-error', { method: 'requestSpin', error: String(error) }); }
      return raw;
    },
    reportReady: function (engine) {
      emit('royal31:renderer-ready', { engine: engine });
      return call('reportReady', [engine], undefined);
    },
    reportError: function (detail) {
      emit('royal31:renderer-error', { detail: detail });
      return call('reportError', [detail], undefined);
    },
    vibrate: function (millis) {
      return call('vibrate', [millis], undefined);
    }
  };

  try {
    window.__ROYAL31_BRIDGE__ = base;
    window.RoyalNative = wrapped;
  } catch (ignored) {}
})();
