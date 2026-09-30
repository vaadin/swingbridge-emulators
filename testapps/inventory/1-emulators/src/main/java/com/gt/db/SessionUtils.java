package com.gt.db;

import com.vaadin.swingbridge.migration.IntentionallyStatic;
import org.apache.log4j.Logger;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;

public class SessionUtils {
    static final Logger logger = Logger.getLogger(SessionUtils.class);
    @IntentionallyStatic(IntentionallyStatic.Reason.JVM_INFRASTRUCTURE)
    private static final SessionFactory sessionFactory;

    static {
        // A SessionFactory is set up once for an application!
        final StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .configure() // configures settings from hibernate.cfg.xml
                .build();
        try {
            sessionFactory = new MetadataSources(registry).buildMetadata().buildSessionFactory();
        } catch (Exception e) {
            // The registry would be destroyed by the SessionFactory, but we had trouble building the SessionFactory
            // so destroy it manually.
            StandardServiceRegistryBuilder.destroy(registry);
            // SB-Emulators change, not upstream's: upstream printed the trace here and left sessionFactory
            // null, so the failure surfaced later as an NPE inside getSession() with nothing left
            // pointing at the real cause. Re-thrown so a broken Hibernate config fails at
            // class-load, where the stack trace still names it. See ../PROVENANCE.md.
            throw new IllegalStateException("Failed to build the Hibernate SessionFactory", e);
        }
    }

    /**
     * Builds a SessionFactory, if it hasn't been already.
     */
    public static SessionFactory get() {
        return sessionFactory;
    }

    public static Session getSession() {
        return sessionFactory.openSession();
    }

    public static void close(Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (HibernateException ignored) {
                ignored.printStackTrace();
                logger.error("Couldn't close Session" + ignored.getMessage());
            }
        }
    }

    public static void rollback(Transaction tx) {
        try {
            if (tx != null) {
                tx.rollback();
            }
        } catch (HibernateException ignored) {
            logger.error("Couldn't rollback Transaction" + ignored.getMessage());
        }
    }


}