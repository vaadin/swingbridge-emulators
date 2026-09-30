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

package vaadinx.swing.app.spring;

import com.vaadin.flow.function.DeploymentConfiguration;
import com.vaadin.flow.server.ServiceException;
import com.vaadin.flow.server.VaadinServletService;
import com.vaadin.flow.server.WrappedSession;
import com.vaadin.flow.spring.SpringServlet;
import com.vaadin.flow.spring.SpringVaadinServletService;
import com.github.mvysny.blockingdialogs.uifiber.loom.VirtualThreadAwareLock;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.locks.Lock;

/**
 * The Spring Boot twin of {@code vaadinx.swing.app.SwingBridgeVaadinServlet}: a
 * {@link SpringServlet} whose session lock is virtual-thread-aware, so SB-Emulators' modal
 * dialogs can park (D_vt_aware_session_lock, R_match_swing_errors case (9)).
 *
 * <p><b>A migrated app does not normally name this class.</b>
 * {@link SwingBridgeAutoConfiguration} registers it, so the Spring lane's host app writes no
 * servlet at all. Name it only to subclass it.
 *
 * <p><b>Why not the Vaadin Boot class.</b> On Spring the servlet has to extend
 * {@link SpringServlet} so Vaadin's bean-based route and instantiator discovery keeps working,
 * and Java has one superclass — so the wrap is reproduced here rather than inherited. The two
 * classes are deliberately the same shape: {@code createServletService} is {@code final} and
 * {@link #createSwingBridgeService} is the seam, so neither the wrap nor {@code service.init()}
 * can be lost by a subclass.
 *
 * <p>{@code SpringServlet.context} is {@code private final}, so this class keeps its own
 * copy — {@link SpringVaadinServletService} needs one and the supertype will not hand it over.
 */
public class SwingBridgeSpringServlet extends SpringServlet {

    private final ApplicationContext context;

    public SwingBridgeSpringServlet(ApplicationContext context, boolean rootMapping) {
        super(context, rootMapping);
        this.context = context;
    }

    /**
     * {@code final} for the same reason as the Vaadin Boot twin's: this is the seam through which
     * the session-lock wrap could be lost silently, and losing it surfaces later and elsewhere as
     * a {@code StackOverflowError} in Vaadin internals. Override {@link #createSwingBridgeService}.
     */
    @Override
    protected final VaadinServletService createServletService(DeploymentConfiguration configuration)
            throws ServiceException {
        VaadinServletService service = createSwingBridgeService(configuration);
        service.init();
        return service;
    }

    /**
     * The service this servlet runs on, before {@code init()}. Override to add your own service
     * behaviour; the return type is what keeps the session-lock wrap in place whatever you return.
     *
     * @return a new instance, uninitialized — {@link #createServletService} calls {@code init()}
     */
    protected SwingBridgeSpringVaadinServletService createSwingBridgeService(DeploymentConfiguration configuration)
            throws ServiceException {
        return new SwingBridgeSpringVaadinServletService(this, configuration, context);
    }

    /**
     * A {@link SpringVaadinServletService} whose session lock is virtual-thread-aware. Public
     * because an app with its own service behaviour has to extend it rather than replace it.
     */
    public static class SwingBridgeSpringVaadinServletService extends SpringVaadinServletService {

        public SwingBridgeSpringVaadinServletService(SpringServlet servlet,
                                                     DeploymentConfiguration configuration,
                                                     ApplicationContext context) {
            super(servlet, configuration, context);
        }

        @Override
        protected final Lock getSessionLock(WrappedSession wrappedSession) {
            return VirtualThreadAwareLock.wrap(this, wrappedSession, super.getSessionLock(wrappedSession));
        }
    }
}
