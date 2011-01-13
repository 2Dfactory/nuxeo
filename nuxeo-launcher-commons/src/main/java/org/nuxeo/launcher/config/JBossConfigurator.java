/*
 * (C) Copyright 2010 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Julien Carsique
 *
 * $Id$
 */

package org.nuxeo.launcher.config;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Properties;

import org.apache.log4j.xml.DOMConfigurator;

/**
 * @author jcarsique
 */
public class JBossConfigurator extends ServerConfigurator {

    /**
     * @deprecated Use {@link #getJBossConfig()}
     */
    @Deprecated
    public static final String JBOSS_CONFIG = "server/default/deploy/nuxeo.ear/config";

    public static final String DEFAULT_CONFIGURATION = "default";

    /**
     * @since 5.4.1
     */
    public static final String STARTUP_CLASS = "org.jboss.Main";

    private String configuration = null;

    public String getConfiguration() {
        if (configuration == null) {
            Properties userConfig = generator.getUserConfig();
            if (userConfig != null) {
                configuration = generator.getUserConfig().getProperty(
                        "org.nuxeo.ecm.jboss.configuration",
                        DEFAULT_CONFIGURATION);
            } else {
                configuration = System.getProperty(
                        "org.nuxeo.ecm.jboss.configuration",
                        DEFAULT_CONFIGURATION);
            }
        }
        return configuration;
    }

    public JBossConfigurator(ConfigurationGenerator configurationGenerator) {
        super(configurationGenerator);
    }

    /**
     * @return true if "config" files directory already exists
     */
    @Override
    public boolean isConfigured() {
        log.info("Detected JBoss server.");
        return new File(generator.getNuxeoHome(), getJBossConfig()).exists();
    }

    @Override
    protected File getOutputDirectory() {
        return new File(generator.getNuxeoHome(),
                new File(getJBossConfig()).getParent());
    }

    public String getJBossConfig() {
        return "server" + File.separator + getConfiguration() + File.separator
                + "deploy" + File.separator + "nuxeo.ear" + File.separator
                + "config";
    }

    @Override
    public String getDefaultDataDir() {
        final String defaultDataDir = "server" + File.separator
                + getConfiguration() + File.separator + "data" + File.separator
                + "NXRuntime" + File.separator + "data";
        return defaultDataDir;
    }

    @Override
    public void initLogs() {
        File logFile = new File(generator.getNuxeoHome(), "server"
                + File.separator + getConfiguration() + File.separator + "conf"
                + File.separator + "jboss-log4j.xml");
        try {
            System.out.println("Try to configure logs with " + logFile);
            DOMConfigurator.configure(logFile.toURI().toURL());
            log.info("Logs succesfully configured.");
        } catch (MalformedURLException e) {
            log.error("Could not initialize logs with " + logFile, e);
        }
    }

    @Override
    public void checkPaths() throws ConfigurationException {
        super.checkPaths();
        checkPaths(configuration);
        if (!configuration.equals(DEFAULT_CONFIGURATION)) {
            checkPaths(DEFAULT_CONFIGURATION);
        }

        File oldPath = new File(generator.getDataDir(), "NXRuntime"
                + File.separator + "binaries");
        String message = "Please move 'binaries' directory from"
                + oldPath.getParent() + "to " + generator.getDataDir();
        checkPath(oldPath, message);
    }

    private void checkPaths(String jbossConfig) throws ConfigurationException {
        File oldPath = new File(generator.getNuxeoHome(), "server"
                + File.separator + jbossConfig + File.separator + "data"
                + File.separator + "h2");
        String message = "Please move 'h2' and 'derby' directories from"
                + oldPath.getParent() + "to " + generator.getDataDir();
        checkPath(oldPath, message);

        oldPath = new File(generator.getNuxeoHome(), "server" + File.separator
                + jbossConfig + File.separator + "data" + File.separator + "derby");
        checkPath(oldPath, message);

        oldPath = new File(generator.getNuxeoHome(), "server" + File.separator
                + jbossConfig + File.separator + "data" + File.separator
                + "NXRuntime" + File.separator + "binaries");
        message = "Please move 'binaries' directory from" + oldPath.getParent()
                + "to " + generator.getDataDir();
        checkPath(oldPath, message);
    }

    @Override
    public String getDefaultTmpDir() {
        final String defaultTmpDir = "server" + File.separator
                + getConfiguration() + File.separator + "tmp";
        return defaultTmpDir;
    }

}
