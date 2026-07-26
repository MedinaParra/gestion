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

  function safeStorageGet(key, fallback) {
    try {
      var value = window.localStorage.getItem(key);
      return value === null ? fallback : value;
    } catch (ignored) {
      return fallback;
    }
  }

  function safeStorageSet(key, value) {
    try { window.localStorage.setItem(key, String(value)); } catch (ignored) {}
  }

  function installAudioDirector() {
    if (window.RoyalAudio) return;
    var AudioContextCtor = window.AudioContext || window.webkitAudioContext;
    var volume = Math.max(0, Math.min(1, Number(safeStorageGet('royalspin.volume', '0.72')) || 0.72));
    var muted = safeStorageGet('royalspin.muted', '0') === '1';
    var records = [];

    function targetGain() { return muted ? 0 : volume; }

    function applyRecord(record, immediate) {
      try {
        var t = record.context.currentTime;
        record.master.gain.cancelScheduledValues(t);
        if (immediate) record.master.gain.setValueAtTime(targetGain(), t);
        else record.master.gain.setTargetAtTime(targetGain(), t, 0.025);
      } catch (ignored) {}
    }

    if (AudioContextCtor && window.AudioNode && window.AudioNode.prototype) {
      try {
        var originalConnect = window.AudioNode.prototype.connect;
        var masters = typeof WeakMap === 'function' ? new WeakMap() : null;

        function findRecord(context) {
          if (masters) return masters.get(context) || null;
          for (var i = 0; i < records.length; i++) {
            if (records[i].context === context) return records[i];
          }
          return null;
        }

        function ensureMaster(context) {
          var existing = findRecord(context);
          if (existing) return existing;
          var master = context.createGain();
          master.__royalMasterBypass = true;
          originalConnect.call(master, context.destination);
          var record = { context: context, master: master };
          records.push(record);
          if (masters) masters.set(context, record);
          applyRecord(record, true);
          return record;
        }

        window.AudioNode.prototype.connect = function (destination) {
          try {
            var context = this.context;
            if (context && destination === context.destination && !this.__royalMasterBypass) {
              return originalConnect.call(this, ensureMaster(context).master);
            }
          } catch (ignored) {}
          return originalConnect.apply(this, arguments);
        };
      } catch (ignored) {}
    }

    window.RoyalAudio = {
      getVolume: function () { return volume; },
      isMuted: function () { return muted; },
      setVolume: function (next) {
        volume = Math.max(0, Math.min(1, Number(next) || 0));
        safeStorageSet('royalspin.volume', volume.toFixed(3));
        for (var i = 0; i < records.length; i++) applyRecord(records[i], false);
        emit('royal32:audio-state', { volume: volume, muted: muted });
      },
      setMuted: function (next) {
        muted = !!next;
        safeStorageSet('royalspin.muted', muted ? '1' : '0');
        for (var i = 0; i < records.length; i++) applyRecord(records[i], false);
        emit('royal32:audio-state', { volume: volume, muted: muted });
      },
      toggleMuted: function () { this.setMuted(!muted); },
      suspend: function () {
        for (var i = 0; i < records.length; i++) {
          try { if (records[i].context.state === 'running') records[i].context.suspend(); } catch (ignored) {}
        }
      },
      resume: function () {
        for (var i = 0; i < records.length; i++) {
          try { if (records[i].context.state === 'suspended') records[i].context.resume(); } catch (ignored) {}
        }
      }
    };
  }

  installAudioDirector();

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

  document.addEventListener('visibilitychange', function () {
    if (!window.RoyalAudio) return;
    if (document.hidden) window.RoyalAudio.suspend();
    else window.RoyalAudio.resume();
  });

  try {
    window.__ROYAL31_BRIDGE__ = base;
    window.RoyalNative = wrapped;
  } catch (ignored) {}
})();
