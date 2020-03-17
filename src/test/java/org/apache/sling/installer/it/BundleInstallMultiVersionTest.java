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
import static org.junit.Assert.assertNull;
import static org.ops4j.pax.exam.CoreOptions.frameworkProperty;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.sling.installer.api.InstallableResource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.osgi.framework.Bundle;

@RunWith(PaxExam.class)
public class BundleInstallMultiVersionTest extends OsgiInstallerTestBase {

    private static final String PROPERTY_MULTIVERSION = "sling.installer.multiversion";

    @org.ops4j.pax.exam.Configuration
    public Option[] config() {
        final Option[] parentOpts = defaultConfiguration();
        final Option[] options = new Option[parentOpts.length + 1];
        System.arraycopy(parentOpts, 0, options, 0, parentOpts.length);
        options[parentOpts.length] = frameworkProperty(PROPERTY_MULTIVERSION).value("true");
        return options;
    }
    @Before
    public void setUp() {
        setupInstaller();
    }

    @Override
    @After
    public void tearDown() {
        super.tearDown();
    }

    @Test
    public void testInstallUninstallMultiVersion() throws Exception {
        final String symbolicName = "osgi-installer-testbundle";

        assertNull("Test bundle must not be present before test",
            findBundle(symbolicName));

        // Install first test bundle and check version
        Map<String,Long> bundleVersionIDs = new HashMap<>();
        {
            assertNull("Test bundle must be absent before installing",
                findBundle(symbolicName));
            final Object listener = this.startObservingBundleEvents();
            installer.updateResources(URL_SCHEME,
                getInstallableResource(
                    getTestBundle(BUNDLE_BASE_NAME + "-testbundle-1.1.jar")),
                null);
            this.waitForBundleEvents(symbolicName + " must be installed",
                listener,
                new BundleEvent(symbolicName, "1.1",
                    org.osgi.framework.BundleEvent.STARTED),
                new BundleEvent(symbolicName, "1.1",
                    org.osgi.framework.BundleEvent.STARTED));
            final Bundle b = assertBundleInVersion("After installing", symbolicName,
                "1.1", Bundle.ACTIVE);
            bundleVersionIDs.put("1.1", b.getBundleId());
        }

        // Install later version (side-by-side), verify
        {
            final Object listener = this.startObservingBundleEvents();
            installer.updateResources(URL_SCHEME,
                getInstallableResource(
                    getTestBundle(BUNDLE_BASE_NAME + "-testbundle-1.2.jar"),
                    "digestA"),
                null);
            this.waitForBundleEvents(symbolicName + " must be installed",
                listener,
                new BundleEvent(symbolicName, "1.2",
                    org.osgi.framework.BundleEvent.STARTED));
            final Bundle b11 = assertBundleInVersion("1.1 not active after adding new version", symbolicName,
                "1.1", Bundle.ACTIVE);
            assertEquals("Bundle ID of installed version must not change", bundleVersionIDs.get("1.1"),
                Long.valueOf(b11.getBundleId()));
            final Bundle b12 = assertBundleInVersion("1.2 not after installing", symbolicName,
                "1.2", Bundle.ACTIVE);
            bundleVersionIDs.put("1.2", b12.getBundleId());
        }

        // Install lower version side-by-side, installed bundles must not change
        {
            final Object listener = this.startObservingBundleEvents();
            installer.updateResources(URL_SCHEME,
                getInstallableResource(
                    getTestBundle(BUNDLE_BASE_NAME + "-testbundle-1.0.jar"),
                    "digestA"),
                null);
            this.waitForBundleEvents(symbolicName + " must be installed",
                listener,
                new BundleEvent(symbolicName, "1.0",
                    org.osgi.framework.BundleEvent.STARTED));
            
            final Bundle b11 = assertBundleInVersion("1.1 not active after adding new version", symbolicName,
                "1.1", Bundle.ACTIVE);
            assertEquals("Bundle ID of installed version must not change", bundleVersionIDs.get("1.1"),
                Long.valueOf(b11.getBundleId()));
            final Bundle b12 = assertBundleInVersion("1.2 not active after adding new version", symbolicName,
                "1.2", Bundle.ACTIVE);
            assertEquals("Bundle ID of installed version must not change", bundleVersionIDs.get("1.2"),
                Long.valueOf(b12.getBundleId()));
            final Bundle b10 = assertBundleInVersion("1.0 not active after installing", symbolicName,
                "1.0", Bundle.ACTIVE);
            bundleVersionIDs.put("1.0", b10.getBundleId());
        }

        // Update to same version with different digest must be ignored
        {
            final Object listener = this.startObservingBundleEvents();
            installer.updateResources(URL_SCHEME,
                getInstallableResource(
                    getTestBundle(BUNDLE_BASE_NAME + "-testbundle-1.0.jar"),
                    "digestB"),
                null);
            sleep(150);
            this.assertNoBundleEvents(
                "Update to same version should generate no OSGi tasks.",
                listener, symbolicName);
        }

        // Uninstall
        {
            final Object listener = this.startObservingBundleEvents();
            installer.updateResources(URL_SCHEME, null,
                getNonInstallableResourceUrl(
                    getTestBundle(BUNDLE_BASE_NAME + "-testbundle-1.0.jar")));
            this.waitForBundleEvents(symbolicName + " must be installed",
                listener,
                new BundleEvent(symbolicName, "1.0",
                    org.osgi.framework.BundleEvent.STOPPED),
                new BundleEvent(symbolicName, "1.0",
                    org.osgi.framework.BundleEvent.UNINSTALLED));
        }
        {
            final Object listener = this.startObservingBundleEvents();
            installer.updateResources(URL_SCHEME, null,
                getNonInstallableResourceUrl(
                    getTestBundle(BUNDLE_BASE_NAME + "-testbundle-1.1.jar")));
            this.waitForBundleEvents(symbolicName + " must be installed",
                listener,
                new BundleEvent(symbolicName, "1.1",
                    org.osgi.framework.BundleEvent.STOPPED),
                new BundleEvent(symbolicName, "1.1",
                    org.osgi.framework.BundleEvent.UNINSTALLED));
        }
        {
            final Object listener = this.startObservingBundleEvents();
            installer.updateResources(URL_SCHEME, null,
                getNonInstallableResourceUrl(
                    getTestBundle(BUNDLE_BASE_NAME + "-testbundle-1.2.jar")));
            this.waitForBundleEvents(symbolicName + " must be installed",
                listener,
                new BundleEvent(symbolicName, "1.2",
                    org.osgi.framework.BundleEvent.STOPPED),
                new BundleEvent(symbolicName, "1.2",
                    org.osgi.framework.BundleEvent.UNINSTALLED));

            final Bundle b = findBundle(symbolicName);
            assertNull("Testbundle must be gone", b);
        }

        // Reinstall lower version, must work
        {
            final Object listener = this.startObservingBundleEvents();
            installer.updateResources(URL_SCHEME,
                getInstallableResource(
                    getTestBundle(BUNDLE_BASE_NAME + "-testbundle-1.1.jar")),
                null);
            this.waitForBundleEvents(
                symbolicName + " reinstall with lower version", listener,
                new BundleEvent(symbolicName, "1.1",
                    org.osgi.framework.BundleEvent.INSTALLED),
                new BundleEvent(symbolicName, "1.1",
                    org.osgi.framework.BundleEvent.STARTED));
            assertBundle("After reinstalling 1.1", symbolicName, "1.1",
                Bundle.ACTIVE);
        }
    }
}