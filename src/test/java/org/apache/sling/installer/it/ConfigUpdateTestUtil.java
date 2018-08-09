/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.installer.it;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sling.installer.api.InstallableResource;
import org.apache.sling.installer.api.OsgiInstaller;
import org.apache.sling.installer.api.event.InstallationEvent;
import org.apache.sling.installer.api.event.InstallationListener;
import org.apache.sling.installer.api.info.InfoProvider;
import org.apache.sling.installer.api.info.InstallationState;
import org.apache.sling.installer.api.info.Resource;
import org.apache.sling.installer.api.info.ResourceGroup;
import org.apache.sling.installer.api.tasks.ResourceState;
import org.apache.sling.installer.api.tasks.TaskResource;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * Utility methods for {@link ConfigUpdateTest}.
 */
public class ConfigUpdateTestUtil {

    static private final String GROUP_ID = "org.apache.sling";
    static private final String ARTIFACT_ID = "org.apache.sling.installer.factory.configuration";
    static private final String OLD_VERSION = "1.1.2";

    public static final String FACTORY_PID = "org.apache.sling.factory.test.installer";
    public static final String MANUAL_FACTORY_PID = "org.apache.sling.factory.test.manual";

    public static final String NAME_1 = "myname1";

    public static final String SCHEME = "myscheme";

    private final BundleContext bundleContext;

    private ConfigurationAdmin configAdmin;

    private InfoProvider infoProvider;

    private OsgiInstaller installer;
    
    public ConfigUpdateTestUtil(final BundleContext ctx) throws Exception {
        this.bundleContext = ctx;
        // we need the old config factory first
        final Bundle b = getConfigFactoryBundle();
        b.stop();
        final String urlString = org.ops4j.pax.exam.CoreOptions.mavenBundle(GROUP_ID, ARTIFACT_ID, OLD_VERSION).getURL();
        final URL url = new URL(urlString);
        try ( final InputStream is = url.openStream()) {
            b.update(is);
        }
        b.start();
    }

    public void init(final ConfigurationAdmin configAdmin, 
            final OsgiInstaller installer, 
            final InfoProvider infoProvider) {
        this.configAdmin = configAdmin;
        this.installer = installer;
        this.infoProvider = infoProvider;
    }

    /**
     * Helper method for sleeping.
     */
    private void sleep(long msec) {
        try {
            Thread.sleep(msec);
        } catch(InterruptedException ignored) {
        }
    }

    private Bundle getConfigFactoryBundle() {
        for(final Bundle b : this.bundleContext.getBundles()) {
            if ( ARTIFACT_ID.equals(b.getSymbolicName())) {
                return b;
            }
        }
        throw new IllegalStateException("Config factory bundle not found");
    }

    public void updateConfigFactoryBundle() throws Exception {
        final Bundle b = getConfigFactoryBundle();
        b.stop();
        final String urlString = org.ops4j.pax.exam.CoreOptions.mavenBundle(GROUP_ID, ARTIFACT_ID, OsgiInstallerTestBase.CONFIG_VERSION).getURL();
        final URL url = new URL(urlString);
        try ( final InputStream is = url.openStream()) {
            b.update(is);
        }
        b.start();
    }

    public InstallableResource[] createTestConfigResources() {
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put("key", "value");
        props.put("id", NAME_1);

        // we need to specify a path as config factory < 1.2.0 has a bug in handling the id if a path is missing
        final InstallableResource rsrc = new InstallableResource("configs/" + FACTORY_PID + "-" + NAME_1 + ".cfg",
                null, props, "1", InstallableResource.TYPE_CONFIG, null);

        return new InstallableResource[] {rsrc};
    }

    public void installTestConfigs() {
        final InstallableResource[] resources = createTestConfigResources();
        final ResourceInstallationListener listener = new ResourceInstallationListener(resources.length);
        final ServiceRegistration<InstallationListener> reg = this.bundleContext.registerService(InstallationListener.class, listener, null);
        try {
            installer.registerResources(SCHEME, resources);

            listener.waitForInstall();
        } finally {
            reg.unregister();
        }
    }

    public void installModifiedTestConfigs(final boolean useRegister) {
        final InstallableResource[] resources = createTestConfigResources();
        for(final InstallableResource rsrc : resources) {
            rsrc.getDictionary().put("modified", Boolean.TRUE);
        }
        final ResourceInstallationListener listener = new ResourceInstallationListener(resources.length);
        final ServiceRegistration<InstallationListener> reg = this.bundleContext.registerService(InstallationListener.class, listener, null);
        try {
            if ( useRegister) {
                installer.registerResources(SCHEME, resources);
            } else {
                installer.updateResources(SCHEME, resources, null);
            }
            listener.waitForInstall();
        } finally {
            reg.unregister();
        }        
    }
    
    public Configuration assertTestConfig(final String name, final boolean checkNew) throws Exception {
        return assertTestConfig(name, checkNew, checkNew);
    }

    public Configuration assertTestConfig(final String name, final boolean checkNew, final boolean modifiedExists) throws Exception {
        final Configuration[] cfgs = this.configAdmin.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + FACTORY_PID + ")" +
            "(id=" + name + "))");
        assertNotNull(cfgs);
        assertEquals(1, cfgs.length);
        final Configuration c = cfgs[0];
        assertEquals("value", c.getProperties().get("key"));
        assertEquals(name, c.getProperties().get("id"));

        if ( !checkNew) {
            assertFalse(c.getPid().equals(FACTORY_PID + "~" + name));
            final Configuration[] cfgs1 = this.configAdmin.listConfigurations("(" + Constants.SERVICE_PID + "=" + FACTORY_PID + "~" + name + ")");
            assertTrue(cfgs1 == null || cfgs1.length == 0);
        }
        if ( checkNew) {
            final Configuration[] cfgs1 = this.configAdmin.listConfigurations("(" + Constants.SERVICE_PID + "=" + FACTORY_PID + "~" + name + ")");
            assertNotNull(cfgs1);
            assertEquals(1, cfgs1.length);
            final Configuration c1 = cfgs1[0];
            assertEquals("value", c1.getProperties().get("key"));
            assertEquals(name, c1.getProperties().get("id"));
            assertEquals(FACTORY_PID, c1.getFactoryPid());
            assertEquals(c.getPid(), c1.getPid());
        }
        if ( modifiedExists ) {
            assertEquals(Boolean.TRUE, c.getProperties().get("modified"));
        } else {
            assertNull(c.getProperties().get("modified"));
        }
        return c;
    }

    public void assertInstallerState(final String name, final boolean checkNew, final ResourceState expectedState) throws Exception {
        // make sure there is only one state in the OSGi installer
        ResourceGroup found = null;
        final InstallationState state = this.infoProvider.getInstallationState();
        for(final ResourceGroup group : state.getInstalledResources()) {
            for(final Resource rsrc : group.getResources()) {
                if ( rsrc.getScheme().equals(SCHEME) && rsrc.getURL().equals(SCHEME + ":" + "configs/" + FACTORY_PID + "-" + name + ".cfg")) {
                    found = group;
                    break;
                }
            }
            if ( found != null ) {
                break;
            }
        }
        assertNotNull(found);
        assertEquals(1, found.getResources().size());
        final Resource r = found.getResources().get(0);
        if ( checkNew ) {
            assertEquals("config:" + FACTORY_PID + "~" + name, r.getEntityId());
        } else {
            assertEquals("config:" + FACTORY_PID + "." + name, r.getEntityId());
        }
        assertEquals(expectedState, r.getState());
    }

    public void assertInstallerState(final String factoryPID, final int count) throws Exception {
        ResourceGroup found = null;
        int c = 0;
        final InstallationState state = this.infoProvider.getInstallationState();
        for(final ResourceGroup group : state.getInstalledResources()) {
            for(final Resource rsrc : group.getResources()) {
                // contains is not precise but should be could enough
                if (rsrc.getEntityId().startsWith("config:") && rsrc.getEntityId().contains(factoryPID) ) {
                    c++;
                }
            }
        }
        assertEquals(count, c);
    }

    public void tearDown() {
        try {
            // stop configuration factory
            getConfigFactoryBundle().stop();

            // remove all configurations
            final Configuration[] cfgs = this.configAdmin.listConfigurations(null);
            if ( cfgs != null ) {
                for(final Configuration c : cfgs) {
                    c.delete();
                }
            }
        } catch ( final IOException | BundleException | InvalidSyntaxException ignore) {
            // ignore
        }
    }


    private class ResourceInstallationListener implements InstallationListener {

        private final AtomicInteger processedBundles = new AtomicInteger(0);
        private final AtomicBoolean doneProcessing = new AtomicBoolean(false);

        private final int count;

        public ResourceInstallationListener(final int count) {
            this.count = count;
        }

        @Override
        public void onEvent(final InstallationEvent event) {
            if ( event.getType() == InstallationEvent.TYPE.PROCESSED ) {
                final TaskResource rsrc = (TaskResource) event.getSource();
                if ( rsrc.getScheme().equals(SCHEME) ) {
                    if ( rsrc.getState() == ResourceState.IGNORED || rsrc.getState() == ResourceState.INSTALLED ) {
                        processedBundles.incrementAndGet();
                    }
                }
            } else if ( event.getType() == InstallationEvent.TYPE.SUSPENDED && processedBundles.get() > 0 ) {
                doneProcessing.set(true);
            }

        }

        public void waitForInstall() {
            final long startTime = System.currentTimeMillis();
            while ( !doneProcessing.get() && startTime + 10000 > System.currentTimeMillis() ) {
                sleep(200);
            }
            if ( processedBundles.get() < count ) {
                int bundlesCount = 0;
                while ( bundlesCount < count ) {
                    bundlesCount = 0;
                    final InstallationState state = infoProvider.getInstallationState();
                    for(final ResourceGroup group : state.getInstalledResources()) {
                        for(final Resource rsrc : group.getResources()) {
                            if ( rsrc.getScheme().equals(SCHEME) ) {
                                bundlesCount++;
                            }
                        }
                    }
                    for(final ResourceGroup group : state.getActiveResources()) {
                        for(final Resource rsrc : group.getResources()) {
                            if ( rsrc.getScheme().equals(SCHEME) ) {
                                bundlesCount++;
                            }
                        }
                    }
                    sleep(200);
                }
            }
        }
    }
}
