/*
 * MusicApp → Folia adapter
 * 代理到 Folia 源码注入的 window.__foliaExternal API
 */
(function () {
  'use strict';

  var _ext = null;
  function waitExt(cb) {
    if (_ext) { cb(); return; }
    var tries = 0;
    var t = setInterval(function () {
      _ext = window.__foliaExternal || window.foliaExternal;
      if (_ext) { clearInterval(t); cb(); return; }
      tries++;
      if (tries > 200) clearInterval(t); // ~10s timeout
    }, 50);
  }

  // 新 API (直接透传)
  window.foliaExternal = {
    setSong: function (json) { waitExt(function () { _ext.setSong(json); }); },
    setLyrics: function (lrcText) { waitExt(function () { _ext.setLyrics(lrcText); }); },
    setPositionMs: function (ms) { waitExt(function () { _ext.setPositionMs(ms); }); },
    setPlaying: function (bool) { waitExt(function () { _ext.setPlaying(!!bool); }); },
    clear: function () { waitExt(function () { _ext.clear(); }); }
  };

  // 旧 API (兼容 FoliaWebViewLyrics.kt)
  window.folia = {
    setSong: function (json) { waitExt(function () { _ext.setSong(json); }); },
    setLrcText: function (lrc) { waitExt(function () { _ext.setLyrics(lrc); }); },
    setLyrics: function (lrc) { waitExt(function () { _ext.setLyrics(lrc); }); },
    setPosition: function (ms) { waitExt(function () { _ext.setPositionMs(ms); }); },
    setPositionMs: function (ms) { waitExt(function () { _ext.setPositionMs(ms); }); },
    setPlaying: function (bool) { waitExt(function () { _ext.setPlaying(!!bool); }); },
    destroy: function () { waitExt(function () { _ext.clear(); }); }
  };
})();
