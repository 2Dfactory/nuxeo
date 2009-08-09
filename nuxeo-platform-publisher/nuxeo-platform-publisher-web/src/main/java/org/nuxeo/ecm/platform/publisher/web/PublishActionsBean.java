/*
 * (C) Copyright 2007-2008 Nuxeo SAS (http://nuxeo.com/) and contributors.
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
 *     Narcis Paslaru
 *     Florent Guillaume
 *     Thierry Martins
 *     Thomas Roger
 */

package org.nuxeo.ecm.platform.publisher.web;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.seam.ScopeType;
import org.jboss.seam.annotations.Create;
import org.jboss.seam.annotations.Destroy;
import org.jboss.seam.annotations.Factory;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Scope;
import org.jboss.seam.core.Events;
import org.jboss.seam.faces.FacesMessages;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.event.DocumentEventCategories;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.api.impl.DocumentLocationImpl;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.schema.FacetNames;
import org.nuxeo.ecm.core.schema.SchemaManager;
import org.nuxeo.ecm.platform.publisher.api.PublicationNode;
import org.nuxeo.ecm.platform.publisher.api.PublicationTree;
import org.nuxeo.ecm.platform.publisher.api.PublishedDocument;
import org.nuxeo.ecm.platform.publisher.api.PublisherService;
import org.nuxeo.ecm.platform.publisher.api.PublishingEvent;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.ecm.webapp.documentsLists.DocumentsListsManager;
import org.nuxeo.ecm.webapp.helpers.EventManager;
import org.nuxeo.ecm.webapp.helpers.ResourcesAccessor;
import org.nuxeo.runtime.api.Framework;

import javax.faces.application.FacesMessage;
import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * This Seam bean manages the publishing tab.
 *
 * @author <a href="mailto:troger@nuxeo.com">Thomas Roger</a>
 */
@Name("publishActions")
@Scope(ScopeType.CONVERSATION)
public class PublishActionsBean implements Serializable {

    private PublisherService publisherService;

    private static final long serialVersionUID = 1L;

    private static final Log log = LogFactory.getLog(PublishActionsBean.class);

    @In(create = true)
    protected transient NavigationContext navigationContext;

    @In(create = true, required = false)
    protected transient CoreSession documentManager;

    @In(create = true)
    protected transient DocumentsListsManager documentsListsManager;

    @In(create = true, required = false)
    protected transient FacesMessages facesMessages;

    @In(create = true)
    protected transient ResourcesAccessor resourcesAccessor;

    protected String currentPublicationTreeNameForPublishing;

    protected PublicationTree currentPublicationTree;

    protected String rejectPublishingComment;

    protected static Set<String> sectionTypes;

    @Create
    public void create() {
        try {
            publisherService = Framework.getService(PublisherService.class);
        } catch (Exception e) {
            throw new IllegalStateException("Publisher service not deployed.",
                    e);
        }
    }

    @Destroy
    public void destroy() {
        if (currentPublicationTree != null) {
            currentPublicationTree.release();
            currentPublicationTree = null;
        }
    }

    @Factory(value = "availablePublicationTrees", scope = ScopeType.EVENT)
    public List<String> getAvailablePublicationTrees() throws ClientException {
        return publisherService.getAvailablePublicationTree();
    }

    public String doPublish(PublicationNode publicationNode)
            throws ClientException {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        PublicationTree tree = getCurrentPublicationTreeForPublishing();
        PublishedDocument publishedDocument = tree.publish(currentDocument,
                publicationNode);
        if (publishedDocument.isPending()) {
            String comment = "Document waiting for publication";
            // Log event on live version
            notifyEvent(PublishingEvent.documentWaitingPublication.name(),
                    null, comment, null, currentDocument);
            facesMessages.add(FacesMessage.SEVERITY_INFO,
                    resourcesAccessor.getMessages().get(
                            "document_submitted_for_publication"),
                    resourcesAccessor.getMessages().get(
                            currentDocument.getType()));
        } else {
            String comment = "Document published";
            // Log event on live version
            notifyEvent(PublishingEvent.documentPublished.name(), null,
                    comment, null, currentDocument);
            facesMessages.add(FacesMessage.SEVERITY_INFO,
                    resourcesAccessor.getMessages().get("document_published"),
                    resourcesAccessor.getMessages().get(
                            currentDocument.getType()));
        }
        return null;
    }

    public void setCurrentPublicationTreeNameForPublishing(
            String currentPublicationTreeNameForPublishing)
            throws ClientException {
        this.currentPublicationTreeNameForPublishing = currentPublicationTreeNameForPublishing;
        if (currentPublicationTree != null) {
            currentPublicationTree.release();
            currentPublicationTree = null;
        }
        currentPublicationTree = getCurrentPublicationTreeForPublishing();
    }

    public String getCurrentPublicationTreeNameForPublishing()
            throws ClientException {
        if (currentPublicationTreeNameForPublishing == null) {
            List<String> publicationTrees = getAvailablePublicationTrees();
            if (!publicationTrees.isEmpty()) {
                currentPublicationTreeNameForPublishing = publicationTrees.get(0);
            }
        }
        return currentPublicationTreeNameForPublishing;
    }

    public PublicationTree getCurrentPublicationTreeForPublishing()
            throws ClientException {
        if (currentPublicationTree == null) {
            currentPublicationTree = publisherService.getPublicationTree(
                    getCurrentPublicationTreeNameForPublishing(),
                    documentManager, null,
                    navigationContext.getCurrentDocument());
        }
        return currentPublicationTree;
    }

    public String getCurrentPublicationTreeIconExpanded()
            throws ClientException {
        return getCurrentPublicationTreeForPublishing().getIconExpanded();
    }

    public String getCurrentPublicationTreeIconCollapsed()
            throws ClientException {
        return getCurrentPublicationTreeForPublishing().getIconCollapsed();
    }

    public List<PublishedDocument> getPublishedDocuments()
            throws ClientException {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        return getCurrentPublicationTreeForPublishing().getExistingPublishedDocument(
                new DocumentLocationImpl(currentDocument));
    }

    public List<PublishedDocument> getPublishedDocumentsFor(String treeName)
            throws ClientException {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        PublicationTree tree = publisherService.getPublicationTree(treeName,
                documentManager, null);
        return tree.getExistingPublishedDocument(new DocumentLocationImpl(
                currentDocument));
    }

    public String unPublish(PublishedDocument publishedDocument)
            throws ClientException {
        getCurrentPublicationTreeForPublishing().unpublish(publishedDocument);
        return null;
    }

    public boolean canPublishTo(PublicationNode publicationNode)
            throws ClientException {
        return getCurrentPublicationTreeForPublishing().canPublishTo(
                publicationNode);
    }

    public boolean canUnpublish(PublishedDocument publishedDocument)
            throws ClientException {
        return getCurrentPublicationTreeForPublishing().canUnpublish(
                publishedDocument);
    }

    public boolean isPublishedDocument() {
        return publisherService.isPublishedDocument(navigationContext.getCurrentDocument());
    }

    public boolean canManagePublishing() throws ClientException {
        PublicationTree tree = publisherService.getPublicationTreeFor(
                navigationContext.getCurrentDocument(), documentManager);
        PublishedDocument publishedDocument = tree.wrapToPublishedDocument(navigationContext.getCurrentDocument());
        return tree.canManagePublishing(publishedDocument);
    }

    public boolean hasValidationTask() throws ClientException {
        PublicationTree tree = publisherService.getPublicationTreeFor(
                navigationContext.getCurrentDocument(), documentManager);
        PublishedDocument publishedDocument = tree.wrapToPublishedDocument(navigationContext.getCurrentDocument());
        return tree.hasValidationTask(publishedDocument);
    }

    public boolean isPending() throws ClientException {
        PublicationTree tree = publisherService.getPublicationTreeFor(
                navigationContext.getCurrentDocument(), documentManager);
        PublishedDocument publishedDocument = tree.wrapToPublishedDocument(navigationContext.getCurrentDocument());
        return publishedDocument.isPending();
    }

    public String getRejectPublishingComment() {
        return rejectPublishingComment;
    }

    public void setRejectPublishingComment(String rejectPublishingComment) {
        this.rejectPublishingComment = rejectPublishingComment;
    }

    public String approveDocument() throws ClientException {
        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        PublicationTree tree = publisherService.getPublicationTreeFor(
                currentDocument, documentManager);
        PublishedDocument publishedDocument = tree.wrapToPublishedDocument(currentDocument);
        tree.validatorPublishDocument(publishedDocument);
        DocumentModel sourceDocument = documentManager.getDocument(publishedDocument.getSourceDocumentRef());
        notifyEvent(PublishingEvent.documentPublicationApproved.name(), null,
                "Document approved", null, sourceDocument);

        DocumentModel liveVersion = documentManager.getDocument(new IdRef(sourceDocument.getSourceId()));
        if (!sourceDocument.getRef().equals(liveVersion.getRef())) {
            notifyEvent(PublishingEvent.documentPublicationApproved.name(), null,
                "Document approved", null, liveVersion);
        }

        Events.instance().raiseEvent(PublishingEvent.documentPublished.name());
        return null;
    }

    public String rejectDocument() throws ClientException {
        if (rejectPublishingComment == null
                || "".equals(rejectPublishingComment)) {
            facesMessages.addToControl("rejectPublishingComment",
                    FacesMessage.SEVERITY_ERROR,
                    resourcesAccessor.getMessages().get(
                            "label.publishing.reject.user.comment.mandatory"));
            return null;
        }

        DocumentModel currentDocument = navigationContext.getCurrentDocument();
        PublicationTree tree = publisherService.getPublicationTreeFor(
                currentDocument, documentManager);
        PublishedDocument publishedDocument = tree.wrapToPublishedDocument(currentDocument);
        tree.validatorRejectPublication(publishedDocument,
                rejectPublishingComment);
        DocumentModel sourceDocument = documentManager.getDocument(publishedDocument.getSourceDocumentRef());
        notifyEvent(PublishingEvent.documentPublicationRejected.name(), null,
                "Document rejected: " + rejectPublishingComment, null,
                sourceDocument);

        DocumentModel liveVersion = documentManager.getDocument(new IdRef(sourceDocument.getSourceId()));
        if (!sourceDocument.getRef().equals(liveVersion.getRef())) {
            notifyEvent(PublishingEvent.documentPublicationApproved.name(), null,
                "Document approved", null, liveVersion);
        }
        Events.instance().raiseEvent(
                PublishingEvent.documentPublicationRejected.name());

        return navigationContext.navigateToRef(navigationContext.getCurrentDocument().getParentRef());
    }

    public void unPublishDocumentsFromCurrentSelection() throws ClientException {
        if (!documentsListsManager.isWorkingListEmpty(DocumentsListsManager.CURRENT_DOCUMENT_SECTION_SELECTION)) {
            unpublish(documentsListsManager.getWorkingList(DocumentsListsManager.CURRENT_DOCUMENT_SECTION_SELECTION));
        } else {
            log.debug("No selectable Documents in context to process unpublish on...");
        }
        log.debug("Unpublish the selected document(s) ...");
    }

    protected void unpublish(List<DocumentModel> documentModels)
            throws ClientException {
        for (DocumentModel documentModel : documentModels) {
            PublicationTree tree = publisherService.getPublicationTreeFor(
                    documentModel, documentManager);
            PublishedDocument publishedDocument = tree.wrapToPublishedDocument(documentModel);
            tree.unpublish(publishedDocument);
        }

        Object[] params = { documentModels.size() };
        // remove from the current selection list
        documentsListsManager.resetWorkingList(DocumentsListsManager.CURRENT_DOCUMENT_SECTION_SELECTION);
        facesMessages.add(FacesMessage.SEVERITY_INFO,
                resourcesAccessor.getMessages().get("n_unpublished_docs"),
                params);
    }

    public String publishWorkList() throws ClientException {
        return publishDocumentList(DocumentsListsManager.DEFAULT_WORKING_LIST);
    }

    public String publishDocumentList(String listName) throws ClientException {
        List<DocumentModel> docs2Publish = documentsListsManager.getWorkingList(listName);
        DocumentModel target = navigationContext.getCurrentDocument();

        if (!getSectionTypes().contains(target.getType())) {
            return null;
        }

        PublicationNode targetNode = publisherService.wrapToPublicationNode(
                target, documentManager);
        if (targetNode == null) {
            return null;
        }

        int nbPublishedDocs = 0;
        for (DocumentModel doc : docs2Publish) {
            if (!documentManager.hasPermission(doc.getRef(),
                    SecurityConstants.READ_PROPERTIES)) {
                continue;
            }

            if (doc.isProxy()) {
                // TODO copy also copies security. just recreate a proxy.
                documentManager.copy(doc.getRef(), target.getRef(),
                        doc.getName());
                nbPublishedDocs++;
            } else {
                if (doc.hasFacet(FacetNames.PUBLISHABLE)) {
                    publisherService.publish(doc, targetNode);
                    nbPublishedDocs++;
                } else {
                    log.info("Attempted to publish non-publishable document "
                            + doc.getTitle());
                }
            }
        }

        Object[] params = { nbPublishedDocs };
        facesMessages.add(FacesMessage.SEVERITY_INFO, "#0 "
                + resourcesAccessor.getMessages().get("n_published_docs"),
                params);

        if (nbPublishedDocs < docs2Publish.size()) {
            facesMessages.add(FacesMessage.SEVERITY_WARN,
                    resourcesAccessor.getMessages().get(
                            "selection_contains_non_publishable_docs"));
        }

        EventManager.raiseEventsOnDocumentChildrenChange(navigationContext.getCurrentDocument());
        return null;
    }

    public Set<String> getSectionTypes() {
        if (sectionTypes == null) {
            sectionTypes = getTypeNamesForFacet("PublishSpace");
            if (sectionTypes == null) {
                sectionTypes = new HashSet<String>();
                sectionTypes.add("Section");
            }
        }
        return sectionTypes;
    }

    protected static Set<String> getTypeNamesForFacet(String facetName) {
        SchemaManager schemaManager;
        try {
            schemaManager = Framework.getService(SchemaManager.class);
        } catch (Exception e) {
            log.error("Exception in retrieving publish spaces : ", e);
            return null;
        }

        Set<String> publishRoots = schemaManager.getDocumentTypeNamesForFacet(facetName);
        if (publishRoots == null || publishRoots.isEmpty()) {
            return null;
        }
        return publishRoots;
    }

    public void notifyEvent(String eventId,
            Map<String, Serializable> properties, String comment,
            String category, DocumentModel dm) throws ClientException {

        // Default category
        if (category == null) {
            category = DocumentEventCategories.EVENT_DOCUMENT_CATEGORY;
        }

        if (properties == null) {
            properties = new HashMap<String, Serializable>();
        }

        properties.put(CoreEventConstants.REPOSITORY_NAME,
                documentManager.getRepositoryName());
        properties.put(CoreEventConstants.SESSION_ID,
                documentManager.getSessionId());
        properties.put(CoreEventConstants.DOC_LIFE_CYCLE,
                dm.getCurrentLifeCycleState());

        DocumentEventContext ctx = new DocumentEventContext(documentManager,
                documentManager.getPrincipal(), dm);

        ctx.setProperties(properties);
        ctx.setComment(comment);
        ctx.setCategory(category);

        EventProducer evtProducer = null;

        try {
            evtProducer = Framework.getService(EventProducer.class);
        } catch (Exception e) {
            log.error("Unable to access EventProducer", e);
            return;
        }

        Event event = ctx.newEvent(eventId);

        try {
            evtProducer.fireEvent(event);
        } catch (Exception e) {
            log.error("Error while sending event", e);
        }
    }

}
