// SwingBridge Emulators — https://github.com/vaadin/swingbridge-emulators
// The `window.emul*` globals and `emul.*` localStorage keys on this page are ours.
// Vaadinx preferences helper. Browser-side glue between
// vaadinx.util.prefs.VaadinPreferences and the browser's localStorage.
//
// Loaded as a servlet-served static resource (META-INF/resources/emul/) via
// Page.addJavaScript("context://emul/prefs-helper.js") from PrefsCache on the
// UI's first preferences access — mirrors clipboard-helper.js / D_clipboard_helper_js. The
// context:// URL scheme routes through Vaadin's static-resource handler
// without going through Vite bundling. See D_preferences.
//
// Storage layout: one localStorage entry per Preferences node, keyed
// "emul.prefs:<absolutePath>" (e.g. "emul.prefs:/com/example/app"), whose value
// is a JSON object of that node's { key: value } string pairs. One-blob-per-node
// (rather than one-entry-per-key) sidesteps the separator-ambiguity problem:
// Swing pref keys may themselves contain '/', but node paths never do, so the
// node path is an unambiguous localStorage key on its own.
//
// readAll() returns the whole tree in a single round-trip so the server can
// warm its per-UI cache once and then serve every getInt/get/... synchronously.
// Writes (writeNode/removeNode/clearAll) are fire-and-forget from the server.

(function () {
  if (window.emulPrefs) return; // idempotent on hot reload

  var PREFIX = "emul.prefs:";

  // Return every persisted node as { "<absolutePath>": { key: value, ... }, ... }.
  // Corrupt entries (non-JSON, e.g. hand-edited) are skipped rather than
  // failing the whole warm-up.
  function readAll() {
    var out = {};
    try {
      for (var i = 0; i < localStorage.length; i++) {
        var k = localStorage.key(i);
        if (k && k.indexOf(PREFIX) === 0) {
          var path = k.substring(PREFIX.length);
          try {
            out[path] = JSON.parse(localStorage.getItem(k));
          } catch (e) {
            /* skip corrupt entry */
          }
        }
      }
    } catch (e) {
      /* localStorage unavailable (private mode / disabled) — return {} */
    }
    return out;
  }

  // Persist one node's full { key: value } map (passed already JSON-stringified
  // by the server so key-order and escaping match the server's view exactly).
  function writeNode(path, json) {
    try {
      localStorage.setItem(PREFIX + path, json);
    } catch (e) {
      /* quota exceeded / disabled — fire-and-forget, nothing to report */
    }
    return null;
  }

  function removeNode(path) {
    try {
      localStorage.removeItem(PREFIX + path);
    } catch (e) {
      /* ignore */
    }
    return null;
  }

  // Remove every emul.prefs:* entry. Collect keys first — removing during the
  // localStorage.length iteration renumbers indices and skips entries.
  function clearAll() {
    try {
      var keys = [];
      for (var i = 0; i < localStorage.length; i++) {
        var k = localStorage.key(i);
        if (k && k.indexOf(PREFIX) === 0) keys.push(k);
      }
      keys.forEach(function (k) {
        localStorage.removeItem(k);
      });
    } catch (e) {
      /* ignore */
    }
    return null;
  }

  window.emulPrefs = {
    readAll: readAll,
    writeNode: writeNode,
    removeNode: removeNode,
    clearAll: clearAll
  };
})();
