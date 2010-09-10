/*
 * (C) Copyright 2010 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the GNU Lesser General Public License (LGPL)
 * version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 * Contributors: Sun Seng David TAN (a.k.a. sunix) <stan@nuxeo.com>
 */
package org.nuxeo.ecm.platform.queue.api;

import java.io.Serializable;
import java.net.URI;
import java.util.List;

/**
 * Handle contents that needs long processing such as OCRing a document, images
 * manipulation, and eventually sequencing.
 * <p>
 * Handle only content of same type. Delegate the content saving to a dedicated
 * persister .
 *
 * @author Stephane Lacoin <slacoin@nuxeo.com> (aka matic)
 * @see QueuePersister
 */
public interface QueueManager<C extends Serializable> {

    /**
     * Names the managed queue
     *
     * @return the name
     */
    URI getName();

    /**
     * Return the context type of handled contents
     */
    Class<C> getContentType();


    /**
     * List infos about content being handled.
     *
     * @return the list
     */
    List<QueueInfo<C>> listHandledContent();

    /**
     * List infos owned by the provided owner
     *
     * @param owner
     * @return the list
     */
    List<QueueInfo<C>> listOwnedContent(URI owner);

    /**
     * List infos about content waiting for recovery.
     *
     * @return the list
     */
    List<QueueInfo<C>> listOrphanedContent();


    /**
     * Check for the existence of a content on persistence back-end.
     *
     * @param content the content
     * @return true if content is already present on persistence back-end
     * @throws QueueException
     */
    boolean knowsContent(URI name);

    /**
     * Retry processing of an orphaned content by invoking it's processor
     * @param contentName
     */
    void retry(URI contentName);

    /**
     * Cancel processing of an orphaned content by removing it from persistence back-end.
     *
     * @param content the content
     */
    void cancel(URI name);

    /**
     * Update additional informations on persistence back-end.
     *
     * @param content the content
     */
    void updateInfos(URI name, C content);

    /**
     * Remove all content from persistence back-end associated to this owner
     *
     * @param owner
     * @return
     */
    int removeOwned(URI owner);

    /**
     * Generate a name referencing an unique content
     *
     * @param queueName
     * @param contentName
     * @return
     */
    URI newName(String contentName);

}
