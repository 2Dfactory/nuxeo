/*
 * (C) Copyright 2006-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     matic
 */
package org.nuxeo.runtime.management;

import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XObject;

/**
 * @author matic
 *
 */
@XObject("locator")
public class ServerLocatorDescriptor {

    @XNode("@domain")
    protected String domainName;

    @XNode("@default")
    protected boolean isDefaultServer = true;

    @XNode("@exist")
    protected boolean isExistingServer = true;

    @XNode("@rmiPort")
    protected int rmiPort = 1099;

    public ServerLocatorDescriptor() {
        domainName = "";
    }

    public ServerLocatorDescriptor(String domainName, boolean isDefaultServer) {
        this.domainName = domainName;
        this.isDefaultServer = isDefaultServer;
    }

    public String getDomainName() {
        return domainName;
    }

    public boolean isDefaultServer() {
        return isDefaultServer;
    }

    public boolean isExistingServer() {
        return isExistingServer;
    }

    public int getRmiPort() {
        return rmiPort;
    }

}
