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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Set;

import org.apache.sling.installer.api.tasks.ResourceState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;

/**
 * This test tests updating the configuration factory bundle from a pre 1.2.0 version to a 1.2.0+
 * version which is using the support for named factory configurations of Configuration Admin 1.6.0+.
 */
@RunWith(PaxExam.class)
public class ConfigUpdateTest extends OsgiInstallerTestBase {

    private ConfigUpdateTestUtil util;

    private ConfigurationAdmin configAdmin;

    @org.ops4j.pax.exam.Configuration
    public Option[] config() {
        return defaultConfiguration();
    }

    @Before
    public void setUp() throws Exception {
        // instantiate util and downgrade factory config bundle to 1.x
        this.util = new ConfigUpdateTestUtil(this.bundleContext);

        super.setup();
        setupInstaller();
        this.configAdmin = this.waitForConfigAdmin(true);
        this.util.init(this.configAdmin, this.installer, this.infoProvider);
    }

    @Override
    @After
    public void tearDown() {
        this.util.tearDown();
        super.tearDown();
    }

    /**
     * - Install test factory config
     * - verify property installation in configadmin and proper state in installer
     * - update installer config factory (update->convert)
     * - verify updated factory config and proper state in installer
     */
    @Test public void testBundleUpdate() throws Exception {
        this.util.installTestConfigs();

        // check for configuration
        this.util.assertTestConfig(ConfigUpdateTestUtil.NAME_1, false);
        this.util.assertInstallerState(ConfigUpdateTestUtil.NAME_1, false, ResourceState.INSTALLED);

        this.util.updateConfigFactoryBundle();

        this.util.assertTestConfig(ConfigUpdateTestUtil.NAME_1, true, false);
        this.util.assertInstallerState(ConfigUpdateTestUtil.NAME_1, true, ResourceState.INSTALLED);
    }

    /**
     * - Install test factory config
     * - verify property installation in configadmin and proper state in installer
     * - update installer config factory (update->convert)
     * - verify updated factory config and proper state in installer
     * - update factory config with same contents (no changes)
     * - verify property installation in configadmin and proper state in installer
     */
    @Test public void testBundleAndConfigRegisterWithoutChange() throws Exception {
        this.testBundleUpdate();

        // register configuration again (unchanged)
        this.util.installTestConfigs();

        // check for configuration
        this.util.assertTestConfig(ConfigUpdateTestUtil.NAME_1, true, false);
        this.util.assertInstallerState(ConfigUpdateTestUtil.NAME_1, true, ResourceState.INSTALLED);
    }

    /**
     * - Install test factory config
     * - verify property installation in configadmin and proper state in installer
     * - update installer config factory (update->convert)
     * - verify updated factory config and proper state in installer
     * - update factory config with new contents using register method
     * - verify property installation in configadmin and proper state in installer
     */
    @Test public void testBundleAndConfigRegisterWithChange() throws Exception {
        this.testBundleUpdate();

        // register configuration again (changed - using registerResources)
        this.util.installModifiedTestConfigs(true);

        // check for configuration
        this.util.assertTestConfig(ConfigUpdateTestUtil.NAME_1, true);
        this.util.assertInstallerState(ConfigUpdateTestUtil.NAME_1, true, ResourceState.INSTALLED);
    }

    /**
     * - Install test factory config
     * - verify property installation in configadmin and proper state in installer
     * - update installer config factory (update->convert)
     * - verify updated factory config and proper state in installer
     * - update factory config with new contents using update metho
     * - verify property installation in configadmin and proper state in installer
     */
    @Test public void testBundleAndConfigUpdateWithChange() throws Exception {
        this.testBundleUpdate();

        // register configuration again (changed - using updateResources)
        this.util.installModifiedTestConfigs(false);

        // check for configuration
        this.util.assertTestConfig(ConfigUpdateTestUtil.NAME_1, true);
        this.util.assertInstallerState(ConfigUpdateTestUtil.NAME_1, true, ResourceState.INSTALLED);
    }

    /**
     * This test
     * - installs a factory configuration
     * - updates the configuration factory bundle
     * - manual updates of that configuration through config admin
     */
    @Test public void testManualUpdateWithoutConversion() throws Exception {
        this.testBundleUpdate();

        final Configuration[] cfgs = this.configAdmin.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + ConfigUpdateTestUtil.FACTORY_PID + ")" +
                "(id=" + ConfigUpdateTestUtil.NAME_1 + "))");
        // we know it's just one config
        final Configuration c = cfgs[0];
        final Dictionary<String, Object> props = c.getProperties();
        props.put("another", "helloworld");
        c.update(props);

        // the update is processed async, so we should give the installer parts some time to process
        this.sleep(5000); // TODO - Can we wait for an event instead?

        final Configuration cUp = this.util.assertTestConfig(ConfigUpdateTestUtil.NAME_1, true, false);
        assertEquals("helloworld", cUp.getProperties().get("another"));
        this.util.assertInstallerState(ConfigUpdateTestUtil.NAME_1, true, ResourceState.IGNORED);
    }

    /**
     * This test does
     * - install a factory configuration
     * - update the configuration factory bundle
     * - manual delete of that configuration through config admin
     */
    @Test public void testManualDeleteWithoutConversion() throws Exception {
        testBundleUpdate();

        final Configuration[] cfgs = this.configAdmin.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + ConfigUpdateTestUtil.FACTORY_PID + ")" +
                "(id=" + ConfigUpdateTestUtil.NAME_1 + "))");
        // we know it's just one config
        final Configuration c = cfgs[0];
        c.delete();

        // the update is processed async, so we should give the installer parts some time to process
        this.sleep(5000); // TODO - Can we wait for an event instead?

        // config should be deleted
        final Configuration[] cfgs2 = this.configAdmin.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + ConfigUpdateTestUtil.FACTORY_PID + ")" +
                "(id=" + ConfigUpdateTestUtil.NAME_1 + "))");
        assertTrue(cfgs2 == null || cfgs2.length == 0);
        // state should be ignored
        this.util.assertInstallerState(ConfigUpdateTestUtil.NAME_1, true, ResourceState.IGNORED);
    }

    /**
     * This test does
     * - install a factory configuration
     * - update the configuration factory bundle
     * - manual update of that configuration through config admin
     * - manual delete of that configuration through config admin
     */
    @Test public void testManualUpdateAndDeleteWithoutConversion() throws Exception {
        testManualUpdateWithoutConversion();

        final Configuration[] cfgs = this.configAdmin.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + ConfigUpdateTestUtil.FACTORY_PID + ")" +
                "(id=" + ConfigUpdateTestUtil.NAME_1 + "))");
        // we know it's just one config
        final Configuration c = cfgs[0];
        c.delete();

        // the update is processed async, so we should give the installer parts some time to process
        this.sleep(5000); // TODO - Can we wait for an event instead?

        // config should be reverted
        this.util.assertTestConfig(ConfigUpdateTestUtil.NAME_1, true, false);
        this.util.assertInstallerState(ConfigUpdateTestUtil.NAME_1, true, ResourceState.INSTALLED);
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

        final Configuration[] cfgs = this.configAdmin.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + ConfigUpdateTestUtil.FACTORY_PID + ")" +
                "(id=" + ConfigUpdateTestUtil.NAME_1 + "))");
        // we know it's just one config
        final Configuration c = cfgs[0];
        final Dictionary<String, Object> props = c.getProperties();
        props.put("another", "helloworld");
        c.update(props);

        // the update is processed async, so we should give the installer parts some time to process
        this.sleep(5000); // TODO - Can we wait for an event instead?

        final Configuration cUp = this.util.assertTestConfig(ConfigUpdateTestUtil.NAME_1, true);
        assertEquals("helloworld", cUp.getProperties().get("another"));
        this.util.assertInstallerState(ConfigUpdateTestUtil.NAME_1, true, ResourceState.IGNORED);
    }

    /**
     * This test does
     * - install a factory configuration
     * - update the configuration factory bundle
     * - update the configuration through the installer (convert)
     * - manual delete of that configuration through config admin
     */
    @Test public void testManualDeleteAfterConversion() throws Exception {
        this.testBundleAndConfigUpdateWithChange();

        final Configuration[] cfgs = this.configAdmin.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + ConfigUpdateTestUtil.FACTORY_PID + ")" +
                "(id=" + ConfigUpdateTestUtil.NAME_1 + "))");
        // we know it's just one config
        final Configuration c = cfgs[0];
        c.delete();

        // the update is processed async, so we should give the installer parts some time to process
        this.sleep(5000); // TODO - Can we wait for an event instead?

        // config should be deleted
        final Configuration[] cfgs2 = this.configAdmin.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + ConfigUpdateTestUtil.FACTORY_PID + ")" +
                "(id=" + ConfigUpdateTestUtil.NAME_1 + "))");
        assertTrue(cfgs2 == null || cfgs2.length == 0);
        // state should be ignored
        this.util.assertInstallerState(ConfigUpdateTestUtil.NAME_1, true, ResourceState.IGNORED);
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

        final Configuration[] cfgs = this.configAdmin.listConfigurations("(&(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + ConfigUpdateTestUtil.FACTORY_PID + ")" +
                "(id=" + ConfigUpdateTestUtil.NAME_1 + "))");
        // we know it's just one config
        final Configuration c = cfgs[0];
        c.delete();

        // the update is processed async, so we should give the installer parts some time to process
        this.sleep(5000); // TODO - Can we wait for an event instead?

        // config should be reverted
        this.util.assertTestConfig(ConfigUpdateTestUtil.NAME_1, true);
        this.util.assertInstallerState(ConfigUpdateTestUtil.NAME_1, true, ResourceState.INSTALLED);
    }

    /**
     * Create a factory configuration before the update
     */
    @Test public void testManualConfigurations() throws Exception {
        this.util.installTestConfigs();

        final Set<String> expectedPids = new HashSet<>();

        // create two factory configurations, one with R6 and one with R7 API
        final Configuration c1 = this.configAdmin.getFactoryConfiguration(ConfigUpdateTestUtil.MANUAL_FACTORY_PID, "c1", null);
        expectedPids.add(c1.getPid());
        final Dictionary<String, Object> props = new Hashtable<>();
        props.put("id", "c1");
        c1.update(props);
        final Configuration c2 = this.configAdmin.createFactoryConfiguration(ConfigUpdateTestUtil.MANUAL_FACTORY_PID, null);
        expectedPids.add(c2.getPid());
        props.put("id", "c2");
        c2.update(props);

        this.util.updateConfigFactoryBundle();

        // there should still be exactly two factory configs
        final Configuration[] cfgs = this.configAdmin.listConfigurations("(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + ConfigUpdateTestUtil.MANUAL_FACTORY_PID + ")");
        assertEquals(2, cfgs.length);
        for(final Configuration c : cfgs) {
            assertTrue(expectedPids.contains(c.getPid()));
        }
        // and no installer state
        this.util.assertInstallerState(ConfigUpdateTestUtil.MANUAL_FACTORY_PID, 0);

        // updating the configs should not change the state
        for(final Configuration c : cfgs) {
            final Dictionary<String, Object> p = c.getProperties();
            p.put("modified", "true");
            c.update(p);
        }
        // still two configurations
        final Configuration[] cfgs2 = this.configAdmin.listConfigurations("(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + ConfigUpdateTestUtil.MANUAL_FACTORY_PID + ")");
        assertEquals(2, cfgs2.length);
        for(final Configuration c : cfgs2) {
            assertTrue(expectedPids.contains(c.getPid()));
        }
        // and no installer state
        this.util.assertInstallerState(ConfigUpdateTestUtil.MANUAL_FACTORY_PID, 0);

        // delete should not change installer state
        for(final Configuration c : cfgs2) {
            c.delete();
        }
        final Configuration[] cfgs3 = this.configAdmin.listConfigurations("(" + ConfigurationAdmin.SERVICE_FACTORYPID + "=" + ConfigUpdateTestUtil.MANUAL_FACTORY_PID + ")");
        assertTrue(cfgs3 == null || cfgs3.length == 0);
        // and no installer state
        this.util.assertInstallerState(ConfigUpdateTestUtil.MANUAL_FACTORY_PID, 0);
    }

    /**
     * This test does
     * - install a factory configuration and an overlay
     * - update the configuration factory bundle
     * - update the factory configuration (no change)
     * - update the overlay factory configuration (change)
     * - delete the overlay factory configuration (change)
     */
    @Test public void testConfigurationOverlays() throws Exception {
        // install factory configuration and overlay
        this.util.installTestConfigs();
        this.util.installOverlayTestConfigs();

        // check for overlay configuration being active
        final Configuration c1 = this.util.assertTestConfig(ConfigUpdateTestUtil.NAME_1, false);
        assertEquals(Boolean.TRUE, c1.getProperties().get("overlay"));
        this.util.assertOverlayInstallerState(ConfigUpdateTestUtil.NAME_1, false, ResourceState.INSTALLED);

        // update factory configuration bundle
        this.util.updateConfigFactoryBundle();

        // check for overlay configuration being active - using named factories
        final Configuration c2 = this.util.assertTestConfig(ConfigUpdateTestUtil.NAME_1, true, false);
        assertEquals(Boolean.TRUE, c2.getProperties().get("overlay"));
        this.util.assertOverlayInstallerState(ConfigUpdateTestUtil.NAME_1, true, ResourceState.INSTALLED);

        // update the factory configuration (no change!)
        this.util.installModifiedTestConfigs(false);

        // check for overlay configuration being active - using named factories
        final Configuration c3 = this.util.assertTestConfig(ConfigUpdateTestUtil.NAME_1, true, false);
        assertEquals(Boolean.TRUE, c3.getProperties().get("overlay"));
        this.util.assertOverlayInstallerState(ConfigUpdateTestUtil.NAME_1, true, ResourceState.INSTALLED);
    }
}
