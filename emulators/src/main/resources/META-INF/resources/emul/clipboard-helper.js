// SwingBridge Emulators — https://github.com/vaadin/swingbridge-emulators
// The `window.emul*` globals and `emul.*` localStorage keys on this page are ours.
// Vaadinx clipboard helper. Browser-side glue between
// vaadinx.awt.datatransfer.WebClipboard and the Async Clipboard API.
//
// Loaded as a servlet-served static resource (META-INF/resources/emul/) via
// @JavaScript("context://emul/clipboard-helper.js") on WebClipboard. The
// context:// URL scheme routes through Vaadin's static-resource handler
// without going through Vite bundling. See D_clipboard sub-decision D_clipboard_helper_js.
//
// Functions return a result object the Java side decodes:
//   write: { ok: true } | { error: "<DOMException.name | NO_SECURE_CONTEXT | NO_CLIPBOARD_API | UNKNOWN>", message: "<msg>" }
//   read:  { ok: true, text: <string|null>, imageMime: <string|null>, imageBase64: <string|null> } | { error: ..., message: ... }
//
// The Java side throws IllegalStateException on NO_SECURE_CONTEXT /
// NO_CLIPBOARD_API (deployment-fixable per D_clipboard_throw_warn_split), and WARNs + returns
// empty / fire-and-forget on every other error (R_match_swing_errors best-effort).

(function () {
  if (window.emulClipboard) return; // idempotent on hot reload

  function preflightError() {
    if (!window.isSecureContext) {
      return {
        error: "NO_SECURE_CONTEXT",
        message: "Browser clipboard requires a secure context (HTTPS or localhost)."
      };
    }
    if (!navigator.clipboard || typeof navigator.clipboard.read !== "function") {
      return {
        error: "NO_CLIPBOARD_API",
        message: "Browser does not support the Async Clipboard API."
      };
    }
    return null;
  }

  function bytesToBase64(bytes) {
    // String.fromCharCode(...big-array) blows the call stack on large
    // images. Chunk to keep the spread within the engine's argument limit.
    const CHUNK = 0x8000;
    let out = "";
    for (let i = 0; i < bytes.length; i += CHUNK) {
      out += String.fromCharCode.apply(null, bytes.subarray(i, i + CHUNK));
    }
    return btoa(out);
  }

  function base64ToBlob(base64, mime) {
    const bin = atob(base64);
    const bytes = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
    return new Blob([bytes], { type: mime });
  }

  // Write text and/or PNG image to the system clipboard. Either argument
  // may be null; at least one must be non-null (caller's responsibility,
  // not enforced here). Always uses navigator.clipboard.write with a
  // single ClipboardItem holding the multiple MIME entries — atomic write.
  async function writeClipboard(text, imagePngBase64) {
    const pre = preflightError();
    if (pre) return pre;

    try {
      const types = {};
      if (text !== null && text !== undefined) {
        types["text/plain"] = new Blob([text], { type: "text/plain" });
      }
      if (imagePngBase64 !== null && imagePngBase64 !== undefined) {
        types["image/png"] = base64ToBlob(imagePngBase64, "image/png");
      }
      const item = new ClipboardItem(types);
      await navigator.clipboard.write([item]);
      return { ok: true };
    } catch (e) {
      return { error: e.name || "UNKNOWN", message: e.message || String(e) };
    }
  }

  // Read text and the first image/* entry from the system clipboard.
  // Returns whichever flavors were present; absent flavors come back as
  // null. The Java side translates this into a WebClipboardSnapshot that
  // advertises only the flavors that had data.
  async function readClipboard() {
    const pre = preflightError();
    if (pre) return pre;

    try {
      const items = await navigator.clipboard.read();
      let text = null;
      let imageMime = null;
      let imageBase64 = null;

      for (const item of items) {
        if (text === null && item.types.includes("text/plain")) {
          const blob = await item.getType("text/plain");
          text = await blob.text();
        }
        if (imageBase64 === null) {
          const imgType = item.types.find((t) => t.startsWith("image/"));
          if (imgType) {
            const blob = await item.getType(imgType);
            const buffer = await blob.arrayBuffer();
            imageMime = imgType;
            imageBase64 = bytesToBase64(new Uint8Array(buffer));
          }
        }
      }

      return { ok: true, text: text, imageMime: imageMime, imageBase64: imageBase64 };
    } catch (e) {
      return { error: e.name || "UNKNOWN", message: e.message || String(e) };
    }
  }

  window.emulClipboard = {
    writeClipboard: writeClipboard,
    readClipboard: readClipboard
  };
})();
