/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Anahide Tchertchian
 */
package org.nuxeo.ecm.platform.query.api;


/**
 * Interface to follow when defining a content view page provider: the service
 * will call {@link #setPageProviderDescriptor(PageProviderDefinition)} so that
 * the descriptor information is available to the {@link PageProvider} when
 * building queries or computing results.
 *
 * @author Anahide Tchertchian
 * @since 5.4
 */
public interface ContentViewPageProvider<T> extends PageProvider<T> {

    void setPageProviderDescriptor(PageProviderDefinition providerDescriptor);

}
