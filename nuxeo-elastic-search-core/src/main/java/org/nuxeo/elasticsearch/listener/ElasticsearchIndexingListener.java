/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and contributors.
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
 *     Nuxeo
 */

package org.nuxeo.elasticsearch.listener;

import static org.nuxeo.ecm.core.api.LifeCycleConstants.TRANSITION_EVENT;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_CREATED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_PUBLISHED;
import static org.nuxeo.ecm.core.api.event.DocumentEventTypes.DOCUMENT_UPDATED;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.elasticsearch.ElasticSearchService;
import org.nuxeo.runtime.api.Framework;

/**
 * Synchronous Event listener used to schedule a reindexing
 *
 * @author <a href="mailto:tdelprat@nuxeo.com">Tiry</a>
 *
 */
public class ElasticsearchIndexingListener implements EventListener {

    private static final Log log = LogFactory.getLog(ElasticsearchIndexingListener.class);

    public static final String DISABLE_AUTO_INDEXING = "disableAutoIndexing";

    @Override
    public void handleEvent(Event event) throws ClientException {
        DocumentEventContext docCtx;
        if (event.getContext() instanceof DocumentEventContext) {
            docCtx = (DocumentEventContext) event.getContext();
        } else {
            return;
        }
        String eventId = event.getName();

        if (!eventId.equals(DOCUMENT_UPDATED)
                && !eventId.equals(DOCUMENT_CREATED)
                && !eventId.equals(TRANSITION_EVENT)
                && !eventId.equals(DOCUMENT_PUBLISHED)) {
            return;
        }
        Boolean block = (Boolean) event.getContext().getProperty(
                DISABLE_AUTO_INDEXING);
        if (block != null && block) {
            // ignore the event - we are blocked by the caller
            log.debug("Skip indexing for doc " + docCtx.getSourceDocument());
            return;
        }

        ElasticSearchService ess = Framework.getLocalService(ElasticSearchService.class);
        ess.index(docCtx.getSourceDocument(), false);
    }

}
