package vaadinx.jgoodies.forms.layout;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The drift gate for this add-on's migration addendum: a migrator (or a migrating agent) reaches
 * {@code MIGRATION.md} either from the module tree or from {@code META-INF/emul/} inside the jar, and
 * finds {@code addons.md} first in order to know the module exists at all. All three links rot
 * silently, so each is asserted here.
 *
 * <p>The dependency direction is deliberately inverted: the <b>add-on</b> checks that core's index
 * names it. Core stays ignorant of add-ons, and forgetting the index row fails this module's build
 * instead of shipping an add-on no migrator can discover.
 *
 * <p>The {@code LICENSE} assertion is this module's alone — the 3-clause BSD licence it carries asks
 * a binary redistribution to reproduce the notice in the materials shipped with it, and a jar
 * travelling by itself carries no other materials.
 *
 * @see vaadinx.toedter.calendar.MigrationDocTest the sibling gate — duplicated rather than shared,
 *      since a common helper would have to live in core (wrong direction) or in a module of its own
 *      (more machinery than the assertions it holds)
 */
class MigrationDocTest {

    /** The headings every add-on addendum carries, in the convention's order. */
    private static final List<String> REQUIRED_HEADINGS = List.of(
            "## Dependency swap",
            "## Version match",
            "## Import rewrite",
            "## Not ported",
            "## Beyond the import swap",
            "## Known divergence",
            "## Verify");

    /**
     * Asserts the pom's {@code <resource>} wiring by reading this module's own build output — the
     * same copy the jar is packed from. Deliberately not a classpath lookup: {@code META-INF}
     * entries collide across dependency jars, and the first hit wins. It cannot assert the jar
     * itself either, since at test phase the jar does not exist yet.
     */
    @Test
    @DisplayName("addendum ships as a jar resource")
    void addendumShipsAsAJarResource() {
        File copied = moduleFile("target/classes/META-INF/emul/MIGRATION.md");
        assertTrue(copied.isFile(), "MIGRATION.md was not copied to META-INF/emul — check the pom's <resources>");
        assertEquals(readText(moduleFile("MIGRATION.md")), readText(copied), "the copied addendum differs from the source");
    }

    @Test
    @DisplayName("the upstream licence ships in the jar")
    void theUpstreamLicenceShipsInTheJar() {
        File copied = moduleFile("target/classes/META-INF/LICENSE");
        assertTrue(copied.isFile(), "LICENSE was not copied to META-INF — check the pom's <resources>");
        assertTrue(readText(copied).contains("JGoodies"), "META-INF/LICENSE is not the JGoodies BSD text");
    }

    @Test
    @DisplayName("addendum carries every required heading")
    void addendumCarriesEveryRequiredHeading() {
        String text = readText(moduleFile("MIGRATION.md"));
        for (String heading : REQUIRED_HEADINGS) {
            assertTrue(text.contains(heading), "missing heading: " + heading);
        }
    }

    /**
     * "Not ported" and "Beyond the import swap" state their emptiness explicitly rather than being
     * left out, so a reader can tell "nothing to do here" from "nobody wrote this section".
     */
    @Test
    @DisplayName("sections that must not be silent carry content")
    void sectionsThatMustNotBeSilentCarryContent() {
        String text = readText(moduleFile("MIGRATION.md"));
        for (String heading : List.of("## Not ported", "## Beyond the import swap")) {
            String afterHeading = text.substring(text.indexOf(heading) + heading.length());
            int end = afterHeading.indexOf("\n## ");
            String body = end < 0 ? afterHeading : afterHeading.substring(0, end);
            assertTrue(body.trim().length() > 40, heading + " is empty or a stub");
        }
    }

    /**
     * The blanket {@code com.jgoodies.} rewrite is wrong for this module — it would rewrite JGoodies
     * Looks imports into a package no jar supplies — and it is the mistake the addendum exists to
     * prevent, so the corrected rule is asserted rather than trusted to survive editing.
     */
    @Test
    @DisplayName("addendum states the forms-scoped rewrite and the looks deletion")
    void addendumStatesTheFormsScopedRewriteAndTheLooksDeletion() {
        String text = readText(moduleFile("MIGRATION.md"));
        assertTrue(text.contains("com.jgoodies.forms.  ->  vaadinx.jgoodies.forms."));
        assertTrue(text.contains("com.jgoodies.looks."));
        assertFalse(text.contains("com.jgoodies.  ->  vaadinx.jgoodies."),
                "the addendum offers a blanket com.jgoodies. rewrite, which mangles JGoodies Looks imports");
    }

    /**
     * The upstream coordinate is the load-bearing half: a migrating agent looks the index up by what
     * it found in the app's own pom, not by SB-Emulators' artifactId.
     */
    @Test
    @DisplayName("the add-on index names this module")
    void theAddOnIndexNamesThisModule() {
        File index = moduleFile("../../guides/1-swing-to-emulators/addons.md");
        assertTrue(index.isFile(), "addons.md not found at " + index.getAbsolutePath() + " — run from the SB-Emulators reactor");
        String text = readText(index);
        assertTrue(text.contains("swingbridge-emulators-jgoodies-forms-1.2.1"), "addons.md has no row for this module");
        assertTrue(text.contains("com.jgoodies:forms"), "addons.md does not name the upstream coordinate");
    }

    /**
     * Surefire runs with the module directory as its working directory, and the module's own parent
     * {@code <relativePath>../../pom.xml</relativePath>} already requires the repo layout — so
     * resolving repo paths from here adds no constraint the build did not already have. Absence is a
     * failure rather than a skip: a gate that disables itself when a path moves is the drift it
     * exists to catch.
     */
    private static File moduleFile(String path) {
        return new File(path);
    }

    /** Unchecked so the assertions above read as assertions, not as IO plumbing. */
    private static String readText(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
