package com.jdimension.jlawyer.client.settings;

import java.util.Properties;

/**
 * Mock of JLawyer's {@code ClientSettings} singleton. The real class holds the
 * client's configuration and JNDI lookup properties; the exit gate needs only
 * {@code getInstance()} + {@code getLookupProperties()} (fed to the mocked
 * {@link com.jdimension.jlawyer.services.JLawyerServiceLocator}).
 */
public class ClientSettings {

    private static final ClientSettings INSTANCE = new ClientSettings();

    public static ClientSettings getInstance() {
        return INSTANCE;
    }

    public Properties getLookupProperties() {
        return new Properties();
    }
}
