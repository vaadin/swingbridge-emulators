/*
 * Copyright 2000-2026 Vaadin Ltd.
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.vaadin.swingbridge.surrogates;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.NativeLabel;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.dom.Style;
import com.vaadin.swingbridge.surrogates.swing.JComponentMixin;
import com.vaadin.swingbridge.surrogates.util.Icons;

import javax.swing.Icon;
import javax.swing.SwingConstants;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Surrogate for {@link javax.swing.JLabel}. Extends Vaadin
 * {@link NativeLabel} ({@code <label>} element) implements
 * {@link JComponentMixin}. Reaches the migrator either through
 * {@code :emulators.JLabel} as peer, or as a richer-than-stock Vaadin
 * component in its own right.
 *
 * <h2>Why {@link NativeLabel}, not {@link Span}</h2>
 *
 * JLabel's {@code labelFor} relationship is the
 * {@code <label for="…">} accessibility wiring — Vaadin's
 * {@link NativeLabel#setFor(Component)} hits that contract directly,
 * whereas {@link Span} doesn't carry the {@code for} attribute. Visual
 * shape (one inline element holding text + optional icon) is identical
 * either way, so the labelFor wire is the deciding factor.
 *
 * <h2>DOM management — manual children, not {@code setText}</h2>
 *
 * Vaadin {@link com.vaadin.flow.component.HasText#setText} writes to the
 * element's text content via {@link com.vaadin.flow.dom.Element#setText},
 * which removes <em>all</em> children (text nodes <em>and</em> elements)
 * before inserting a single text node. That's incompatible with hosting
 * an icon child element alongside the text. This surrogate sidesteps
 * the issue by:
 *
 * <ul>
 * <li>holding an inner {@link Span} ({@link #textSpan}) as a stable
 * text-bearing child — {@code setText} writes through the inner
 * Span, leaving any icon sibling untouched;</li>
 * <li>holding the icon (when present) as a {@link Component} child
 * inserted at index 0 of the host {@code <label>}, so the visual
 * order is icon-then-text matching JDK's
 * {@code horizontalTextPosition == TRAILING} default.</li>
 * </ul>
 *
 * <h2>R_vaadin_first stance — what's wired, what drops</h2>
 *
 * <p>
 * <em>UI-functional / wired:</em>
 * <ul>
 * <li>{@code text} — written to {@link #textSpan}, read back from same.</li>
 * <li>{@code icon} — encoded via {@link Icons#toVaadinIconComponent}
 * (icon-rendering Path 1 / D_icon_rendering); rendered as a Vaadin
 * {@link com.vaadin.flow.component.html.Image} child of the label.
 * Non-{@link javax.swing.ImageIcon} {@code Icon} impls drop with a
 * WARN (Path 2 / Path 3 deferred).</li>
 * <li>{@code labelFor} — delegates to {@link NativeLabel#setFor(Component)}
 * so the rendered {@code <label>} carries the right {@code for}
 * attribute.</li>
 * </ul>
 *
 * <h2>Layout — {@code display: inline-grid} drives alignment + text-position + gap</h2>
 *
 * The host {@code <label>} runs as {@code display: inline-grid}, whose
 * tracks are the JDK's icon-plus-text block; the four alignment /
 * text-position setters and {@code iconTextGap} all map onto grid CSS:
 *
 * <ul>
 * <li>{@code horizontalAlignment} → {@code justify-content} and
 * {@code verticalAlignment} → {@code align-content}: where the block
 * sits in the label. Visible only when the label is larger than its
 * content — a shrink-wrap label lays out flush either way, as in the
 * JDK.</li>
 * <li>{@code verticalTextPosition} picks the shape: {@code CENTER} puts
 * icon and text side by side ({@code grid-auto-flow: column}),
 * {@code TOP} / {@code BOTTOM} stack the text above / below the icon
 * ({@code row}).</li>
 * <li>{@code horizontalTextPosition} orders the pair side by side
 * ({@code TRAILING} / {@code RIGHT} icon first, otherwise text first),
 * and when stacked places the text against the icon: {@code CENTER}
 * centres it underneath, {@code LEADING} hangs it off the icon's
 * leading edge.</li>
 * <li>{@code iconTextGap} → CSS {@code gap}.</li>
 * </ul>
 *
 * <p>A grid rather than a flexbox because its alignment axes are fixed:
 * a flexbox aligns along its main axis, which turns vertical when the
 * label stacks, so {@code horizontalAlignment} would move the block
 * vertically.
 *
 * <p>R_match_swing_errors IAE preserved before any CSS write (bad SwingConstants axis
 * still throws). Getters parse the CSS back per R_vaadin_first, the shape of
 * {@code setBorder} / {@code getBorder} (SD_border_css_lossy); CSS has
 * both the physical ({@code left}) and the writing-direction-relative
 * ({@code start}) value, so every alignment and text position round-trips.
 * The one shape that renders approximately is a {@code CENTER} text position
 * side by side, which the JDK paints over the icon: it renders text first.
 *
 * <p>{@code displayedMnemonic} / {@code displayedMnemonicIndex} stay
 * Bucket B noops — JLabel's mnemonic visually underlines a character,
 * which has no Vaadin counterpart, and the accelerator-binding aspect
 * needs the {@code labelFor} target's focus integration that Vaadin's
 * {@code <label for>} doesn't surface client-side. R_match_swing_errors IAE on
 * out-of-range index still fires.
 *
 * <p>
 * <em>Inherited from {@link JComponentMixin}:</em> client properties,
 * borders (CSS round-trip per SD_border_css_lossy), opaque flag, tooltip, key/focus
 * listeners, plus the standard {@code ComponentMixin} surface.
 */
public class SJLabel extends NativeLabel implements JComponentMixin {

    /**
     * The session this component's writes hop through off the UI thread, read by
     * {@link com.vaadin.swingbridge.surrogates.SHelper#sessionOf}: captured here when one is
     * current, handed down by an emulator built off the UI thread, or taken at first attach.
     * Once set it never changes, since a component never leaves its session.
     */
    private volatile com.vaadin.flow.server.VaadinSession hopSession =
            com.vaadin.flow.server.VaadinSession.getCurrent();

    {
        addAttachListener(e -> hopSession = e.getSession());
    }

  /**
   * Inner {@code <span>} carrying the text content. Stable child of
   * the host {@code <label>} so {@link #setText} writes don't remove
   * any sibling icon, and {@link #getText} reads round-trip what was
   * written. Created eagerly in every constructor.
   */
  private final Span textSpan;

  /**
   * Raw HTML string when the current text was set as an HTML payload
   * (per Swing's {@code BasicHTML.isHTMLString} rule — leading
   * {@code <html>}). {@code null} when the label is in plain-text mode
   * and {@link #textSpan}'s {@code .getText()} is the round-trip
   * source. Storage justified per R_vaadin_first: when HTML is in play, the inner
   * {@code <span>} holds parsed DOM children (not the original markup
   * string) and the original markup has no Vaadin counterpart to read
   * back from — same shape as a Store carve-out.
   */
  private String htmlText;

  /**
   * Optional icon child (Vaadin {@link Component}, typically an
   * {@link com.vaadin.flow.component.html.Image} from
   * {@link Icons#toVaadinIconComponent}). {@code null} when no icon
   * is installed. Inserted at index 0 of the host {@code <label>} so
   * the visual order is icon-then-text.
   */
  private Component iconChild;

  public SJLabel() {
    super();
    _installSwingClass();
    this.textSpan = new Span();
    textSpan.getElement().getStyle().set("white-space", "nowrap");
    getElement().appendChild(textSpan.getElement());
    // Seed JDK JLabel layout defaults (LEADING / CENTER / TRAILING /
    // CENTER + gap 4px) into the CSS so getters return them without
    // setters being called first.
    Style style = getElement().getStyle();
    style.set("display", "inline-grid");
    style.set("align-items", "center");           // icon and text centred against each other
    style.set("justify-content", "start");        // h-align LEADING
    style.set("align-content", "center");         // v-align CENTER
    style.set("gap", "4px");                      // iconTextGap default
    writeTextPosition(SwingConstants.TRAILING, SwingConstants.CENTER);
  }

  public SJLabel(String text) {
    this();
    setText(text);
  }

  /** JDK convention: an icon-only label centers its content. */
  public SJLabel(Icon icon) {
    this();
    // Override the LEADING default seeded above — JLabel's icon-only
    // ctor centers the content. Write CSS directly (no shadow store
    // per R_vaadin_first; setHorizontalAlignment would also work but adds a PCE
    // we don't want from a constructor).
    getElement().getStyle().set("justify-content", "center");
    setIcon(icon);
  }

  public SJLabel(String text, Icon icon) {
    this();
    if (text != null)
      setText(text);
    if (icon != null)
      setIcon(icon);
  }

  // --- Text (written through the inner Span so the icon child survives) --

  /**
   * Reads back from the inner {@link #textSpan} (plain-text mode) or
   * from {@link #htmlText} when an HTML payload was set. Returns
   * {@code ""} (not {@code null}) on a freshly-constructed label
   * whose text was never set — Vaadin {@code Span.getText()} returns
   * {@code ""} for an empty element. Migrated code that null-checks
   * JLabel text needs to {@code ""}-check on this layer; the emulator
   * preserves null at its own field shadow.
   */
  @Override
  public String getText() {
    return htmlText != null ? htmlText : textSpan.getText();
  }

  /**
   * Writes to the inner {@link #textSpan} (preserving any icon
   * sibling) and fires the {@code "text"} PCE. Mirrors Swing
   * JLabel's two paths: when the string starts with {@code <html>}
   * (Swing's {@code BasicHTML.isHTMLString} rule, case-insensitive),
   * the inner span receives parsed DOM via {@code innerHTML}; outer
   * {@code <html>}/{@code <body>} wrappers are stripped so the
   * rendered subtree is well-formed. Plain text uses
   * {@link Span#setText}. Transitioning HTML → plain clears DOM
   * children before reseeding the text node. {@code null} writes
   * land as {@code ""}.
   *
   * <p>Plain text renders on one line ({@code white-space: nowrap}), as a
   * JDK JLabel paints it — a {@code \n} included, which the JDK does not
   * break on either. HTML text wraps, as the JDK's HTML view does.
   */
  @Override
  public void setText(String text) {
    String old = htmlText != null ? htmlText : textSpan.getText();
    String next = text == null ? "" : text;
    if (old.equals(next))
      return;
    if (isHtmlString(next)) {
      textSpan.getElement().setProperty("innerHTML", stripHtmlBodyWrapper(next));
      if (htmlText == null) {
        textSpan.getElement().getStyle().remove("white-space");
      }
      htmlText = next;
    } else {
      if (htmlText != null) {
        // Transition out of HTML mode: drop the parsed children
        // before setText can reseed a single text node.
        textSpan.getElement().setProperty("innerHTML", "");
        textSpan.getElement().getStyle().set("white-space", "nowrap");
        htmlText = null;
      }
      textSpan.setText(next);
    }
    firePropertyChange("text", old, next);
  }

  /**
   * Mirrors {@code javax.swing.text.html.BasicHTML.isHTMLString}: the
   * string starts with a literal {@code <html>} tag (case-insensitive
   * on the tag name). Anything else — including a leading whitespace
   * before the tag — is treated as plain text, matching Swing.
   */
  private static boolean isHtmlString(String s) {
    return s != null
        && s.length() >= 6
        && s.charAt(0) == '<'
        && s.charAt(5) == '>'
        && s.regionMatches(true, 1, "html", 0, 4);
  }

  /**
   * Strip the outer {@code <html>…</html>} pair and an optional
   * inner {@code <body[ …attrs]>…</body>} pair. Browsers tolerate
   * nesting them inside a {@code <span>} but ignore the tags
   * silently; stripping ourselves keeps the DOM tidy and lets any
   * style attributes on the original {@code <body>} get dropped
   * (e.g. the {@code width:230px} that Swing's HTML renderer would
   * honour internally but a plain span ignores anyway).
   */
  private static String stripHtmlBodyWrapper(String s) {
    String body = s.trim();
    if (body.length() >= 6 && body.regionMatches(true, 0, "<html>", 0, 6)) {
      body = body.substring(6).trim();
    }
    if (body.length() >= 7 && body.regionMatches(true, body.length() - 7, "</html>", 0, 7)) {
      body = body.substring(0, body.length() - 7).trim();
    }
    if (body.length() >= 5 && body.regionMatches(true, 0, "<body", 0, 5)) {
      int close = body.indexOf('>');
      if (close >= 0) {
        body = body.substring(close + 1).trim();
      }
    }
    if (body.length() >= 7 && body.regionMatches(true, body.length() - 7, "</body>", 0, 7)) {
      body = body.substring(0, body.length() - 7).trim();
    }
    return body;
  }

  // --- Icon (Vaadin-first per SD_vaadin_first_binding / D_icon_rendering) ---------------------------

  /**
   * Returns the Vaadin {@link Component} currently installed as the
   * icon child, or {@code null} when no icon is set. Vaadin-typed per
   * R_vaadin_first — pure-surrogate users access the icon via this Vaadin handle;
   * migrated code that wants the JDK {@link Icon} ref reads it from
   * the emulator layer.
   */
  public Component getIcon() {
    return iconChild;
  }

  /**
   * Encode the icon via {@link Icons#toVaadinIconComponent} (Path 1)
   * and install as a child of the host {@code <label>}. Replaces any
   * previous icon child; {@code null} clears the slot. Fires
   * {@code "icon"} PCE with Vaadin-typed values per R_vaadin_first (the JDK
   * {@link Icon} ref isn't shadow-stored).
   */
  public void setIcon(Icon icon) {
    Component newIcon = Icons.toVaadinIconComponent(this, icon);
    Component old = iconChild;
    if (old == newIcon)
      return;
    if (old != null) {
      getElement().removeChild(old.getElement());
    }
    iconChild = newIcon;
    if (newIcon != null) {
      getElement().insertChild(0, newIcon.getElement());
    }
    firePropertyChange("icon", old, newIcon);
  }

  // --- labelFor (Vaadin-first via NativeLabel.setFor) --------------

  /**
   * Tracks the {@link Component} passed to {@link #setLabelFor} for
   * round-trip — Vaadin {@link NativeLabel#getFor()} returns the
   * {@code for} attribute value (a string ID), not the target
   * {@link Component}, so without this shadow {@code getLabelFor}
   * couldn't return what was set. Storage is justified per R_vaadin_first: the
   * Component identity is the actual user-facing API; the attribute
   * string is downstream.
   */
  private Component labelForTarget;

  public Component getLabelFor() {
    return labelForTarget;
  }

  /**
   * Routes to {@link NativeLabel#setFor(String)} after ensuring the
   * target has a server-side id (Vaadin requires one for the
   * {@code for=} attribute write; JDK {@code JLabel.setLabelFor}
   * does not). When the target has no id, mint a stable
   * {@code "emul-labelfor-N"} suffix and set it before delegating.
   * {@code null} clears the slot. Fires {@code "labelFor"} PCE.
   */
  public void setLabelFor(Component target) {
    Component old = this.labelForTarget;
    if (old == target)
      return;
    this.labelForTarget = target;
    if (target == null) {
      // Vaadin's setFor(String) rejects null; clear the for=
      // attribute directly. setFor("") would write an empty
      // attribute which differs subtly (presence vs absence).
      getElement().removeAttribute("for");
    } else {
      String id = target.getId().orElseGet(() -> {
        String generated = "emul-labelfor-" + LABEL_FOR_ID_COUNTER.incrementAndGet();
        target.setId(generated);
        return generated;
      });
      setFor(id);
    }
    firePropertyChange("labelFor", old, target);
  }

  /**
   * Counter for auto-generated {@code labelFor} ids. Class-static so
   * minted ids are unique across labels in a session; counter monotonic
   * within a JVM run, which is enough — collisions across JVM restarts
   * don't matter (clients re-render from scratch).
   */
  private static final AtomicLong LABEL_FOR_ID_COUNTER = new AtomicLong();

  // --- Layout setters (drive inline-grid CSS directly per R_vaadin_first) ------
  //
  // Setters write CSS; getters parse it back, so no value is stored twice.
  // Validation that JDK throws on (SwingConstants axis check, mnemonic index
  // bounds) is preserved per R_match_swing_errors — the throw fires before any
  // state mutation. PCE old/new values are the CSS readback, so they match what
  // the getter returns.
  //
  // A grid rather than a flexbox because grid alignment is tied to the physical
  // axes: justify-* is always horizontal and align-* always vertical, whichever
  // way the icon and text are arranged. In a flexbox both pairs follow the main
  // axis, so a stacked (column) label would align its block on the wrong axis,
  // and `flex-start` flips sides under a reversed direction.
  //
  //   * The block of icon + text — the JDK's union of iconR and textR — is the
  //     grid's tracks; justify-content places it horizontally (horizontalAlignment)
  //     and align-content vertically (verticalAlignment).
  //   * grid-auto-flow is the shape: `column` puts icon and text side by side
  //     (verticalTextPosition CENTER), `row` stacks them (TOP / BOTTOM). The text
  //     span's `order: -1` puts the text first: above the icon for TOP, before it
  //     for LEADING / LEFT / CENTER side by side.
  //   * horizontalTextPosition is also each child's place in a stacked column —
  //     the icon's via justify-items, the text's via its own justify-self — so
  //     CENTER centres the text under the icon and LEADING hangs it off the
  //     icon's leading edge. Side by side each child has its own track, so the
  //     two are invisible there and serve only as the getter's source.
  //   * gap: iconTextGap px.
  //
  // Lossless except for one shape: side by side, a CENTER text position (the
  // JDK paints the text over the icon) renders as LEADING (text before icon).

  public int getHorizontalAlignment() {
    return cssToHorizontal(getElement().getStyle().get("justify-content"));
  }

  public void setHorizontalAlignment(int alignment) {
    int validated = checkHorizontalKey(alignment, "horizontalAlignment");
    int old = getHorizontalAlignment();
    if (old == validated) return;
    getElement().getStyle().set("justify-content", horizontalToCss(validated));
    firePropertyChange("horizontalAlignment", old, validated);
  }

  public int getVerticalAlignment() {
    return cssToVertical(getElement().getStyle().get("align-content"));
  }

  public void setVerticalAlignment(int alignment) {
    int validated = checkVerticalKey(alignment, "verticalAlignment");
    int old = getVerticalAlignment();
    if (old == validated) return;
    getElement().getStyle().set("align-content", verticalToCss(validated));
    firePropertyChange("verticalAlignment", old, validated);
  }

  public int getHorizontalTextPosition() {
    return cssToHorizontal(textSpan.getElement().getStyle().get("justify-self"));
  }

  public void setHorizontalTextPosition(int textPosition) {
    int validated = checkHorizontalKey(textPosition, "horizontalTextPosition");
    int old = getHorizontalTextPosition();
    if (old == validated) return;
    writeTextPosition(validated, getVerticalTextPosition());
    firePropertyChange("horizontalTextPosition", old, validated);
  }

  public int getVerticalTextPosition() {
    if (!"row".equals(getElement().getStyle().get("grid-auto-flow"))) return SwingConstants.CENTER;
    return textSpan.getElement().getStyle().get("order") != null
        ? SwingConstants.TOP : SwingConstants.BOTTOM;
  }

  public void setVerticalTextPosition(int textPosition) {
    int validated = checkVerticalKey(textPosition, "verticalTextPosition");
    int old = getVerticalTextPosition();
    if (old == validated) return;
    writeTextPosition(getHorizontalTextPosition(), validated);
    firePropertyChange("verticalTextPosition", old, validated);
  }

  public int getIconTextGap() {
    return parseGapPx(getElement().getStyle().get("gap"));
  }

  public void setIconTextGap(int iconTextGap) {
    Style style = getElement().getStyle();
    String oldCss = style.get("gap");
    String newCss = iconTextGap + "px";
    if (newCss.equals(oldCss)) return;
    int oldGap = parseGapPx(oldCss);
    style.set("gap", newCss);
    firePropertyChange("iconTextGap", oldGap, iconTextGap);
  }

  /** Writes the shape, the text's order and both children's place in a stacked column. */
  private void writeTextPosition(int hTextPos, int vTextPos) {
    Style host = getElement().getStyle();
    Style text = textSpan.getElement().getStyle();
    boolean stacked = vTextPos != SwingConstants.CENTER;
    boolean textFirst = stacked
        ? vTextPos == SwingConstants.TOP
        : hTextPos != SwingConstants.TRAILING && hTextPos != SwingConstants.RIGHT;
    host.set("grid-auto-flow", stacked ? "row" : "column");
    if (textFirst) {
      text.set("order", "-1");
    } else {
      text.remove("order");
    }
    String textSide = horizontalToCss(hTextPos);
    text.set("justify-self", textSide);
    host.set("justify-items", oppositeSide(textSide));   // the icon's, which has no justify-self
  }

  /** LEADING / TRAILING are writing-direction-relative, LEFT / RIGHT physical — CSS has both. */
  private static String horizontalToCss(int alignment) {
    return switch (alignment) {
      case SwingConstants.LEFT -> "left";
      case SwingConstants.RIGHT -> "right";
      case SwingConstants.LEADING -> "start";
      case SwingConstants.TRAILING -> "end";
      default -> "center";
    };
  }

  private static int cssToHorizontal(String css) {
    if (css == null) return SwingConstants.CENTER;
    return switch (css) {
      case "left" -> SwingConstants.LEFT;
      case "right" -> SwingConstants.RIGHT;
      case "start" -> SwingConstants.LEADING;
      case "end" -> SwingConstants.TRAILING;
      default -> SwingConstants.CENTER;
    };
  }

  private static String oppositeSide(String css) {
    return switch (css) {
      case "left" -> "right";
      case "right" -> "left";
      case "start" -> "end";
      case "end" -> "start";
      default -> css;
    };
  }

  private static String verticalToCss(int alignment) {
    return switch (alignment) {
      case SwingConstants.TOP -> "start";
      case SwingConstants.BOTTOM -> "end";
      default -> "center";
    };
  }

  private static int cssToVertical(String css) {
    if (css == null) return SwingConstants.CENTER;
    return switch (css) {
      case "start" -> SwingConstants.TOP;
      case "end" -> SwingConstants.BOTTOM;
      default -> SwingConstants.CENTER;
    };
  }

  /** Parse {@code "Npx"} → {@code N}; non-numeric / null falls back to JDK default 4. */
  private static int parseGapPx(String css) {
    if (css == null) return 4;
    String trimmed = css.endsWith("px") ? css.substring(0, css.length() - 2) : css;
    try { return Integer.parseInt(trimmed.trim()); }
    catch (NumberFormatException nfe) { return 4; }
  }

  // --- Bucket B / deliberate-noop setters (mnemonic only) ----------
  //
  // displayedMnemonic / displayedMnemonicIndex stay Bucket B — the
  // visual underline has no Vaadin counterpart, and the
  // labelFor-targeted Alt+VK accelerator needs focus integration
  // Vaadin's <label for> doesn't surface. R_match_swing_errors IAE preserved.

  public int getDisplayedMnemonic() {
    return 0;
  }

  public void setDisplayedMnemonic(int key) {
    SHelper.onNoop(this, "setDisplayedMnemonic");
  }

  public void setDisplayedMnemonic(char aChar) {
    // Cast lifts to the int overload — without it, char→int promotion
    // would resolve back to this same (char) overload (recursive).
    setDisplayedMnemonic((int) Character.toUpperCase(aChar));
  }

  public int getDisplayedMnemonicIndex() {
    return -1;
  }

  public void setDisplayedMnemonicIndex(int index) {
    if (index != -1) {
      String t = getText();
      int len = t == null ? 0 : t.length();
      if (index < -1 || index >= len) {
        throw new IllegalArgumentException("Invalid mnemonic index: " + index);
      }
    }
    SHelper.onNoop(this, "setDisplayedMnemonicIndex");
  }

  /** JDK validator — vertical SwingConstants only; IAE otherwise (R_match_swing_errors). */
  private static int checkVerticalKey(int key, String exception) {
    if (key == SwingConstants.TOP || key == SwingConstants.CENTER || key == SwingConstants.BOTTOM)
      return key;
    throw new IllegalArgumentException(exception);
  }

  /** JDK validator — horizontal SwingConstants only; IAE otherwise. */
  private static int checkHorizontalKey(int key, String exception) {
    if (key == SwingConstants.LEFT || key == SwingConstants.CENTER || key == SwingConstants.RIGHT
        || key == SwingConstants.LEADING || key == SwingConstants.TRAILING)
      return key;
    throw new IllegalArgumentException(exception);
  }

  @Override
  public String getUIClassID() {
    return "LabelUI";
  }

  /**
   * L&amp;F dispatch isn't modeled; matches {@link JComponentMixin}'s no-op stance.
   */
  public void updateUI() {
    // No-op: Vaadin owns the DOM, no pluggable UI.
    SHelper.onNoop(this, "updateUI");
  }
}
