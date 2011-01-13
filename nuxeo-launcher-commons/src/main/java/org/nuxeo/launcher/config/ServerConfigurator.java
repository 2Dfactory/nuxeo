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
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Properties;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.launcher.commons.text.TextTemplate;

/**
 * @author jcarsique
 */
public abstract class ServerConfigurator {

    protected static final Log log = LogFactory.getLog(ServerConfigurator.class);

    protected static final String DEFAULT_LOG_DIR = "log";

    protected final ConfigurationGenerator generator;

    protected File dataDir = null;

    protected File logDir = null;

    private File pidDir = null;

    protected File libDir = null;

    private File tmpDir = null;

    public ServerConfigurator(ConfigurationGenerator configurationGenerator) {
        generator = configurationGenerator;
    }

    /**
     * @return true if server configuration files already exist
     */
    abstract boolean isConfigured();

    /**
     * Generate configuration files from templates and given configuration
     * parameters
     *
     * @param config Properties with configuration parameters for template
     *            replacement
     */
    protected void parseAndCopy(Properties config) throws IOException {
        // FilenameFilter for excluding "nuxeo.defaults" files from copy
        final FilenameFilter filter = new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return !"nuxeo.defaults".equals(name);
            }
        };
        final TextTemplate templateParser = new TextTemplate(config);
        templateParser.setTrim(true);
        templateParser.setParsingExtensions(config.getProperty(
                ConfigurationGenerator.PARAM_TEMPLATES_PARSING_EXTENSIONS,
                "xml,properties"));

        // add included templates directories
        for (File includedTemplate : generator.getIncludedTemplates()) {
            if (includedTemplate.listFiles(filter) != null) {
                // Retrieve optional target directory if defined
                String outputDirectoryStr = config.getProperty(includedTemplate.getName()
                        + ".target");
                File outputDirectory = (outputDirectoryStr != null) ? new File(
                        generator.getNuxeoHome(), outputDirectoryStr)
                        : getOutputDirectory();
                for (File in : includedTemplate.listFiles(filter)) {
                    // copy template(s) directories parsing properties
                    templateParser.processDirectory(in, new File(
                            outputDirectory, in.getName()));
                }
            }
        }
    }

    /**
     * @return output directory for files generation
     */
    protected abstract File getOutputDirectory();

    /**
     * @return Default data directory path relative to Nuxeo Home
     * @since 5.4.1
     */
    protected abstract String getDefaultDataDir();

    /**
     * @return Data directory
     * @since 5.4.1
     */
    public File getDataDir() {
        if (dataDir == null) {
            dataDir = new File(generator.getNuxeoHome(), getDefaultDataDir());
        }
        return dataDir;
    }

    /**
     * @return Log directory
     * @since 5.4.1
     */
    public File getLogDir() {
        if (logDir == null) {
            logDir = new File(generator.getNuxeoHome(), DEFAULT_LOG_DIR);
        }
        return logDir;
    }

    /**
     * @param dataDirStr Data directory path to set
     * @since 5.4.1
     */
    public void setDataDir(String dataDirStr) {
        dataDir = new File(dataDirStr);
        dataDir.mkdirs();
    }

    /**
     * @param logDirStr Log directory path to set
     * @since 5.4.1
     */
    public void setLogDir(String logDirStr) {
        logDir = new File(logDirStr);
        logDir.mkdirs();
    }

    /**
     * Initialize logs
     *
     * @since 5.4.1
     */
    public abstract void initLogs();

    /**
     * @return Pid directory (usually known as "run directory"); Returns log
     *         directory if not set by configuration.
     * @since 5.4.1
     */
    public File getPidDir() {
        if (pidDir == null) {
            pidDir = getLogDir();
        }
        return pidDir;
    }

    /**
     * @param pidDirStr Pid directory path to set
     * @since 5.4.1
     */
    public void setPidDir(String pidDirStr) {
        pidDir = new File(pidDirStr);
        pidDir.mkdirs();
    }

    /**
     * Check server paths; warn if existing deprecated paths
     *
     * @since 5.4.1
     */
    public abstract void checkPaths();

    /**
     * @return Temporary directory
     * @since 5.4.1
     */
    public File getTmpDir() {
        if (tmpDir == null) {
            tmpDir = new File(generator.getNuxeoHome(), getDefaultTmpDir());
        }
        return tmpDir;
    }

    /**
     * @return Default temporary directory path relative to Nuxeo Home
     * @since 5.4.1
     */
    public abstract String getDefaultTmpDir();

    /**
     * @param tmpDirStr Temporary directory path to set
     * @since 5.4.1
     */
    public void setTmpDir(String tmpDirStr) {
        tmpDir = new File(tmpDirStr);
        tmpDir.mkdirs();
    }

    /**
     * @see Environment
     * @param key directory system key
     * @param directory absolute or relative directory path
     * @since 5.4.1
     */
    public void setDirectory(String key, String directory) {
        String absoluteDirectory = setAbsolutePath(key, directory);
        if (Environment.NUXEO_DATA_DIR.equals(key)) {
            setDataDir(absoluteDirectory);
        } else if (Environment.NUXEO_LOG_DIR.equals(key)) {
            setLogDir(absoluteDirectory);
        } else if (Environment.NUXEO_PID_DIR.equals(key)) {
            setPidDir(absoluteDirectory);
        } else if (Environment.NUXEO_TMP_DIR.equals(key)) {
            setTmpDir(absoluteDirectory);
        } else {
            log.error("Unknown directory key: " + key);
        }
    }

    /**
     * Make absolute the directory passed in parameter. If it was relative, then
     * store absolute path in user config instead of relative and return value
     *
     * @param key Directory system key
     * @param directory absolute or relative directory path
     * @return absolute directory path
     * @since 5.4.1
     */
    private String setAbsolutePath(String key, String directory) {
        if (!new File(directory).isAbsolute()) {
            directory = new File(generator.getNuxeoHome(), directory).getPath();
            generator.getUserConfig().setProperty(key, directory);
        }
        return directory;
    }

    /**
     * @see Environment
     * @param key directory system key
     * @return Directory denoted by key
     * @since 5.4.1
     */
    public File getDirectory(String key) {
        if (Environment.NUXEO_DATA_DIR.equals(key)) {
            return getDataDir();
        } else if (Environment.NUXEO_LOG_DIR.equals(key)) {
            return getLogDir();
        } else if (Environment.NUXEO_PID_DIR.equals(key)) {
            return getPidDir();
        } else if (Environment.NUXEO_TMP_DIR.equals(key)) {
            return getTmpDir();
        } else {
            log.error("Unknown directory key: " + key);
            return null;
        }
    }

}
