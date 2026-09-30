package com.jdimension.jlawyer.client.settings;

import com.vaadin.swingbridge.migration.IntentionallyStatic;
import static com.vaadin.swingbridge.migration.IntentionallyStatic.Reason.WORLD_GLOBAL_READ_MOSTLY;
import java.util.Properties;

/**
 * Mock of JLawyer's {@code ClientSettings} singleton. The real class holds the
 * client's configuration and JNDI lookup properties; the exit gate needs only
 * {@code getInstance()} + {@code getLookupProperties()} (fed to the mocked
 * {@link com.jdimension.jlawyer.services.JLawyerServiceLocator}).
 */
public class ClientSettings {

    @IntentionallyStatic(value = WORLD_GLOBAL_READ_MOSTLY,
            note = "this mock holds no state; the real ClientSettings holds per-user settings and must be re-swept")
    private static final ClientSettings INSTANCE = new ClientSettings();

    public static ClientSettings getInstance() {
        return INSTANCE;
    }

    public Properties getLookupProperties() {
        return new Properties();
    }
}
