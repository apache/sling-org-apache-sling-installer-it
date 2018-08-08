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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sling.installer.api.InstallableResource;
import org.apache.sling.installer.api.event.InstallationEvent;
import org.apache.sling.installer.api.event.InstallationListener;
import org.apache.sling.installer.api.info.InfoProvider;
import org.apache.sling.installer.api.info.InstallationState;
import org.apache.sling.installer.api.info.Resource;
import org.apache.sling.installer.api.info.ResourceGroup;
import org.apache.sling.installer.api.tasks.ResourceState;
import org.apache.sling.installer.api.tasks.TaskResource;
import static org.junit.Assert.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
 
/**
 * This test tests updating the configuration factory bundle from a pre 1.2.0 version to a 1.2.0+
 * version which is using the support for named factory configurations of Configuration Admin 1.6.0+.
 */
@RunWith(PaxExam.class)
public class ConfigUpdateTest extends OsgiInstallerTestBase {

    static private final String GROUP_ID = "org.apache.sling";
    static private final String ARTIFACT_ID = "org.apache.sling.installer.factory.configuration";
    static private final String OLD_VERSION = "1.1.2";

    private static final String FACTORY_PID = "org.apache.sling.factory.test";
    private static final String NAME_1 = "myname1";

    private static final String SCHEME = "myscheme";

    @org.ops4j.pax.exam.Configuration
    public Option[] config() {
        return defaultConfiguration();
    }

    private Bundle getConfigFactoryBundle() {
        for(final Bundle b : this.bundleContext.getBundles()) {
            if ( ARTIFACT_ID.equals(b.getSymbolicName())) {
                return b;
            }
        }
        throw new IllegalStateException("Config factory bundle not found");
    }

    private void updateConfigFactoryBundle() throws Exception {
        final Bundle b = getConfigFactoryBundle();
        b.stop();
        final String urlString = org.ops4j.pax.exam.CoreOptions.mavenBundle(GROUP_ID, ARTIFACT_ID, OsgiInstallerTestBase.CONFIG_VERSION).getURL();
        final URL url = new URL(urlString);
        try ( final InputStream is = url.openStream()) {
            b.update(is);
        }
        b.start();
    }

    @Before
    public void setUp() throws Exception {
        // we need the old config factory first
        final Bundle b = getConfigFactoryBundle();
        b.stop();
        final String urlString = org.ops4j.pax.exam.CoreOptions.mavenBundle(GROUP_ID, ARTIFACT_ID, OLD_VERSION).getURL();
        final URL url = new URL(urlString);
        try ( final InputStream is = url.openStream()) {
            b.update(is);
        }
        b.start();
        super.setup();
        setupInstaller();
    }

    private InstallableResource[] createTestConfigs() {
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put("key", "value");
        props.put("id", NAME_1);

        // we need to specify a path as config factory < 1.2.0 has a bug in handling the id if a path is missing
        final InstallableResource rsrc = new InstallableResource("configs/" + FACTORY_PID + "-" + NAME_1 + ".cfg",
                null, props, "1", InstallableResource.TYPE_CONFIG, null);

        return new InstallableResource[] {rsrc};
    }

    private Configuration assertConfig(final String name, final boolean checkNew) throws Exception {
        return assertConfig(name, checkNew, checkNew);
    }

    private Configuration assertConfig(final String name, final boolean checkNew, final boolean modifiedExists) throws Exception {
        final ConfigurationAdmin ca = this.getService(ConfigurationAdmin.class);
        final Configuration[] cfgs = ca.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + FACTORY_PID + ")" +
            "(id=" + name + "))");
        assertNotNull(cfgs);
        assertEquals(1, cfgs.length);
        final Configuration c = cfgs[0];
        assertEquals("value", c.getProperties().get("key"));
        assertEquals(name, c.getProperties().get("id"));

        if ( !checkNew) {
            assertFalse(c.getPid().equals(FACTORY_PID + "~" + name));
            final Configuration[] cfgs1 = ca.listConfigurations("(" + Constants.SERVICE_PID + "=" + FACTORY_PID + "~" + name + ")");
            assertTrue(cfgs1 == null || cfgs1.length == 0);
        }
        if ( checkNew) {
            final Configuration[] cfgs1 = ca.listConfigurations("(" + Constants.SERVICE_PID + "=" + FACTORY_PID + "~" + name + ")");
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

    private void assertInstallerState(final String name, final boolean checkNew, final ResourceState expectedState) throws Exception {
        // make sure there is only one state in the OSGi installer
        final InfoProvider infoProvider = getService(InfoProvider.class);
        ResourceGroup found = null;
        final InstallationState state = infoProvider.getInstallationState();
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

    @Override
    @After
    public void tearDown() {
        try {
            final Configuration[] cfgs = this.getService(ConfigurationAdmin.class).listConfigurations(null);
            if ( cfgs != null ) {
                for(final Configuration c : cfgs) {
                    c.delete();
                }
            }
        } catch ( final IOException | InvalidSyntaxException ignore) {
            // ignore
        }
        super.tearDown();
    }

    /**
     * Simply updating the bundle should not change anything
     */
    @Test public void testBundleUpdate() throws Exception {
        final InstallableResource[] resources = createTestConfigs();
        final ResourceInstallationListener listener = new ResourceInstallationListener(resources.length);
        final ServiceRegistration<InstallationListener> reg = this.bundleContext.registerService(InstallationListener.class, listener, null);
        try {
            installer.registerResources(SCHEME, resources);

            listener.waitForInstall();
        } finally {
            reg.unregister();
        }
        // check for configuration
        assertConfig(NAME_1, false);
        assertInstallerState(NAME_1, false, ResourceState.INSTALLED);

        updateConfigFactoryBundle();

        assertConfig(NAME_1, false);
        assertInstallerState(NAME_1, false, ResourceState.INSTALLED);
    }

    /**
     * Simply updating the bundle and then updating the config with the same contents should not change anything
     */
    @Test public void testBundleAndConfigRegisterWithoutChange() throws Exception {
        testBundleUpdate();

        final InstallableResource[] resources = createTestConfigs();
        final ResourceInstallationListener listener = new ResourceInstallationListener(resources.length);
        final ServiceRegistration<InstallationListener> reg = this.bundleContext.registerService(InstallationListener.class, listener, null);
        try {
            installer.registerResources(SCHEME, resources);

            listener.waitForInstall();
        } finally {
            reg.unregister();
        }
        // check for configuration
        assertConfig(NAME_1, false);
        assertInstallerState(NAME_1, false, ResourceState.INSTALLED);
    }

    /**
     * Updating the bundle and then updating the config with a new config should convert the configurations
     */
    @Test public void testBundleAndConfigRegisterWithChange() throws Exception {
        testBundleUpdate();

        final InstallableResource[] resources = createTestConfigs();
        for(final InstallableResource rsrc : resources) {
            rsrc.getDictionary().put("modified", Boolean.TRUE);
        }
        final ResourceInstallationListener listener = new ResourceInstallationListener(resources.length);
        final ServiceRegistration<InstallationListener> reg = this.bundleContext.registerService(InstallationListener.class, listener, null);
        try {
            installer.registerResources(SCHEME, resources);

            listener.waitForInstall();
        } finally {
            reg.unregister();
        }
        // check for configuration
        assertConfig(NAME_1, true);
        assertInstallerState(NAME_1, true, ResourceState.INSTALLED);
    }

    /**
     * Updating the bundle and then updating the config with a new config should convert the configurations
     */
    @Test public void testBundleAndConfigUpdateWithChange() throws Exception {
        testBundleUpdate();

        final InstallableResource[] resources = createTestConfigs();
        for(final InstallableResource rsrc : resources) {
            rsrc.getDictionary().put("modified", Boolean.TRUE);
        }
        final ResourceInstallationListener listener = new ResourceInstallationListener(resources.length);
        final ServiceRegistration<InstallationListener> reg = this.bundleContext.registerService(InstallationListener.class, listener, null);
        try {
            installer.updateResources(SCHEME, resources, null);

            listener.waitForInstall();
        } finally {
            reg.unregister();
        }
        // check for configuration
        assertConfig(NAME_1, true);
        assertInstallerState(NAME_1, true, ResourceState.INSTALLED);
    }

    /**
     * This test does
     * - install a factory configuration
     * - update the configuration factory bundle
     * - manual update of that configuration through config admin
     */
    @Test public void testManualUpdateWithoutConversion() throws Exception {
        testBundleUpdate();

        final ConfigurationAdmin ca = this.waitForConfigAdmin(true);
        final Configuration[] cfgs = ca.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + FACTORY_PID + ")" +
                "(id=" + NAME_1 + "))");
        // we know it's just one config
        final Configuration c = cfgs[0];
        final Dictionary<String, Object> props = c.getProperties();
        props.put("another", "helloworld");
        c.update(props);

        // the update is processed async, so we should give the installer parts some time to process
        this.sleep(5000); // TODO - Can we wait for an event instead?

        final Configuration cUp = assertConfig(NAME_1, true, false);
        assertEquals("helloworld", cUp.getProperties().get("another"));
        assertInstallerState(NAME_1, true, ResourceState.IGNORED);
    }

    /**
     * This test does
     * - install a factory configuration
     * - update the configuration factory bundle
     * - manual delete of that configuration through config admin
     */
    /*@Test*/ public void testManualDeleteWithoutConversion() throws Exception {
        testBundleUpdate();

        final ConfigurationAdmin ca = this.waitForConfigAdmin(true);
        final Configuration[] cfgs = ca.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + FACTORY_PID + ")" +
                "(id=" + NAME_1 + "))");
        // we know it's just one config
        final Configuration c = cfgs[0];
        c.delete();

        // the update is processed async, so we should give the installer parts some time to process
        this.sleep(5000); // TODO - Can we wait for an event instead?

        // config should be deleted
        final Configuration[] cfgs2 = ca.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + FACTORY_PID + ")" +
                "(id=" + NAME_1 + "))");
        assertTrue(cfgs2 == null || cfgs2.length == 0);
        // state should be ignored
        assertInstallerState(NAME_1, true, ResourceState.IGNORED);
    }

    /**
     * This test does
     * - install a factory configuration
     * - update the configuration factory bundle
     * - manual update of that configuration through config admin
     * - manual delete of that configuration through config admin
     */
    /*@Test*/ public void testManualUpdateAndDeleteWithoutConversion() throws Exception {
        testManualUpdateWithoutConversion();

        final ConfigurationAdmin ca = this.waitForConfigAdmin(true);
        final Configuration[] cfgs = ca.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + FACTORY_PID + ")" +
                "(id=" + NAME_1 + "))");
        // we know it's just one config
        final Configuration c = cfgs[0];
        c.delete();

        // the update is processed async, so we should give the installer parts some time to process
        this.sleep(5000); // TODO - Can we wait for an event instead?

        // config should be reverted
        assertConfig(NAME_1, true);
        assertInstallerState(NAME_1, true, ResourceState.INSTALLED);
    }
    
    /**
     * This test does
     * - install a factory configuration
     * - update the configuration factory bundle
     * - update the configuration through the installer (convert)
     * - manual update of that configuration through config admin
     */
    @Test public void testManualUpdateAfterConversion() throws Exception {
        testBundleAndConfigUpdateWithChange();

        final ConfigurationAdmin ca = this.waitForConfigAdmin(true);
        final Configuration[] cfgs = ca.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + FACTORY_PID + ")" +
                "(id=" + NAME_1 + "))");
        // we know it's just one config
        final Configuration c = cfgs[0];
        final Dictionary<String, Object> props = c.getProperties();
        props.put("another", "helloworld");
        c.update(props);

        // the update is processed async, so we should give the installer parts some time to process
        this.sleep(5000); // TODO - Can we wait for an event instead?

        final Configuration cUp = assertConfig(NAME_1, true);
        assertEquals("helloworld", cUp.getProperties().get("another"));
        assertInstallerState(NAME_1, true, ResourceState.IGNORED);
    }

    /**
     * This test does
     * - install a factory configuration
     * - update the configuration factory bundle
     * - update the configuration through the installer (convert)
     * - manual delete of that configuration through config admin
     */
    @Test public void testManualDeleteAfterConversion() throws Exception {
        testBundleAndConfigUpdateWithChange();

        final ConfigurationAdmin ca = this.waitForConfigAdmin(true);
        final Configuration[] cfgs = ca.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + FACTORY_PID + ")" +
                "(id=" + NAME_1 + "))");
        // we know it's just one config
        final Configuration c = cfgs[0];
        c.delete();

        // the update is processed async, so we should give the installer parts some time to process
        this.sleep(5000); // TODO - Can we wait for an event instead?

        // config should be deleted
        final Configuration[] cfgs2 = ca.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + FACTORY_PID + ")" +
                "(id=" + NAME_1 + "))");
        assertTrue(cfgs2 == null || cfgs2.length == 0);
        // state should be ignored
        assertInstallerState(NAME_1, true, ResourceState.IGNORED);
    }

    /**
     * This test does
     * - install a factory configuration
     * - update the configuration factory bundle
     * - update the configuration through the installer (convert)
     * - manual update of that configuration through config admin
     * - manual delete of that configuration through config admin
     */
    @Test public void testManualUpdateAndDeleteAfterConversion() throws Exception {
        testManualUpdateAfterConversion();

        final ConfigurationAdmin ca = this.waitForConfigAdmin(true);
        final Configuration[] cfgs = ca.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + FACTORY_PID + ")" +
                "(id=" + NAME_1 + "))");
        // we know it's just one config
        final Configuration c = cfgs[0];
        c.delete();

        // the update is processed async, so we should give the installer parts some time to process
        this.sleep(5000); // TODO - Can we wait for an event instead?

        // config should be reverted
        assertConfig(NAME_1, true);
        assertInstallerState(NAME_1, true, ResourceState.INSTALLED);
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
                final InfoProvider infoProvider = getService(InfoProvider.class);
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
