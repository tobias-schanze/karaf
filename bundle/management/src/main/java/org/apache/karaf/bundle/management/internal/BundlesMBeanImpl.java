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
package org.apache.karaf.bundle.management.internal;

import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.List;

import javax.management.NotCompliantMBeanException;
import javax.management.StandardMBean;
import javax.management.openmbean.CompositeData;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularData;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.apache.karaf.bundle.core.BundleSelector;
import org.apache.karaf.bundle.core.BundleStateService;
import org.apache.karaf.bundle.management.BundlesMBean;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.framework.wiring.FrameworkWiring;

/**
 * Bundles MBean implementation.
 */
public class BundlesMBeanImpl extends StandardMBean implements BundlesMBean {

    private BundleContext bundleContext;
    private final BundleSelector bundleSelector;
    private final List<BundleStateService> bundleStateServices;

    public BundlesMBeanImpl(BundleContext bundleContext, BundleSelector bundleSelector, List<BundleStateService> bundleStateServices) throws NotCompliantMBeanException {
        super(BundlesMBean.class);
        this.bundleContext = bundleContext;
        this.bundleSelector = bundleSelector;
        this.bundleStateServices = bundleStateServices;
    }
    
    private List<Bundle> selectBundles(String id) throws Exception {
        List<String> ids = Collections.singletonList(id);
        return this.bundleSelector.selectBundles(ids , false);
    }

    public TabularData getBundles() throws Exception {
        CompositeType bundleType = new CompositeType("Bundle", "OSGi Bundle",
                new String[]{"ID", "Name", "Version", "Start Level", "State"},
                new String[]{"ID of the Bundle", "Name of the Bundle", "Version of the Bundle", "Start Level of the Bundle", "Current State of the Bundle"},
                new OpenType[]{SimpleType.LONG, SimpleType.STRING, SimpleType.STRING, SimpleType.INTEGER, SimpleType.STRING});
        TabularType tableType = new TabularType("Bundles", "Tables of all Bundles", bundleType, new String[]{"ID"});
        TabularData table = new TabularDataSupport(tableType);

        Bundle[] bundles = bundleContext.getBundles();

        for (int i = 0; i < bundles.length; i++) {
            try {
                int bundleStartLevel = getBundleStartLevel(bundles[i]).getStartLevel();
                int bundleState = bundles[i].getState();
                String bundleStateString;
                if (bundleState == Bundle.ACTIVE)
                    bundleStateString = "ACTIVE";
                else if (bundleState == Bundle.INSTALLED)
                    bundleStateString = "INSTALLED";
                else if (bundleState == Bundle.RESOLVED)
                    bundleStateString = "RESOLVED";
                else if (bundleState == Bundle.STARTING)
                    bundleStateString = "STARTING";
                else if (bundleState == Bundle.STOPPING)
                    bundleStateString = "STOPPING";
                else bundleStateString = "UNKNOWN";
                CompositeData data = new CompositeDataSupport(bundleType,
                        new String[]{"ID", "Name", "Version", "Start Level", "State"},
                        new Object[]{bundles[i].getBundleId(), bundles[i].getSymbolicName(), bundles[i].getVersion().toString(), bundleStartLevel, bundleStateString});
                table.put(data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return table;
    }

    public int getStartLevel(String bundleId) throws Exception {
        List<Bundle> bundles = selectBundles(bundleId);

        if (bundles.size() != 1) {
            throw new IllegalArgumentException("Provided bundle Id doesn't return any bundle or more than one bundle selected");
        }

        return getBundleStartLevel(bundles.get(0)).getStartLevel();
    }

    public void setStartLevel(String bundleId, int bundleStartLevel) throws Exception {
        List<Bundle> bundles = selectBundles(bundleId);

        for (Bundle bundle : bundles) {
            getBundleStartLevel(bundle).setStartLevel(bundleStartLevel);
        }
    }

    private BundleStartLevel getBundleStartLevel(Bundle bundle) {
        return bundle.adapt(BundleStartLevel.class);
    }

    public void refresh() throws Exception {
        getFrameworkWiring().refreshBundles(null);
    }

    public void refresh(String bundleId) throws Exception {
        List<Bundle> bundles = selectBundles(bundleId);
        getFrameworkWiring().refreshBundles(bundles);
    }

    public void update(String bundleId) throws Exception {
        update(bundleId, null);
    }

    public void update(String bundleId, String location) throws Exception {
        List<Bundle> bundles = selectBundles(bundleId);

        if (location == null) {
            for (Bundle bundle : bundles) {
                bundle.update();
            }
            return;
        }

        if (bundles.size() != 1) {
            throw new IllegalArgumentException("Provided bundle Id doesn't return any bundle or more than one bundle selected");
        }

        InputStream is = new URL(location).openStream();
        bundles.get(0).update(is);
    }

    public void resolve() throws Exception {
        getFrameworkWiring().resolveBundles(null);
    }

    public void resolve(String bundleId) throws Exception {
        List<Bundle> bundles = selectBundles(bundleId);
        getFrameworkWiring().resolveBundles(bundles);
    }

    private FrameworkWiring getFrameworkWiring() {
        return getBundleContext().getBundle(0).adapt(FrameworkWiring.class);
    }

    public void restart(String bundleId) throws Exception {
        List<Bundle> bundles = selectBundles(bundleId);

        for (Bundle bundle : bundles) {
            bundle.stop();
            bundle.start();
        }

    }

    public long install(String url) throws Exception {
        return install(url, false);
    }

    public long install(String url, boolean start) throws Exception {
        Bundle bundle = bundleContext.installBundle(url, null);
        if (start) {
            bundle.start();
        }
        return bundle.getBundleId();
    }

    public void start(String bundleId) throws Exception {
        List<Bundle> bundles = selectBundles(bundleId);

        for (Bundle bundle : bundles) {
            bundle.start();
        }
    }

    public void stop(String bundleId) throws Exception {
        List<Bundle> bundles = selectBundles(bundleId);

        for (Bundle bundle : bundles) {
            bundle.stop();
        }
    }

    public void uninstall(String bundleId) throws Exception {
        List<Bundle> bundles = selectBundles(bundleId);

        for (Bundle bundle : bundles) {
            bundle.uninstall();
        }
    }

    public BundleContext getBundleContext() {
        return this.bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    @Override
    public String getDiag(long bundleId) {
        Bundle bundle = bundleContext.getBundle(bundleId);
        if (bundle == null) {
            throw new RuntimeException("Bundle with id " + bundleId + " not found");
        }
        StringBuilder message = new StringBuilder();
        for (BundleStateService bundleStateService : bundleStateServices) {
            String part = bundleStateService.getDiag(bundle);
            if (part != null) {
                message.append(bundleStateService.getName() + part);
            }
        }
        return message.toString();
    }

}
