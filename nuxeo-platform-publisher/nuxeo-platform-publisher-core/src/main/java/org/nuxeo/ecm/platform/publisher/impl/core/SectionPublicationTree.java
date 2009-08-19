package org.nuxeo.ecm.platform.publisher.impl.core;

import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.platform.publisher.api.*;
import org.nuxeo.ecm.platform.publisher.helper.PublicationRelationHelper;
import org.nuxeo.ecm.platform.relations.api.util.RelationHelper;
import org.nuxeo.ecm.platform.relations.api.util.RelationConstants;
import org.nuxeo.ecm.platform.relations.api.Resource;
import org.nuxeo.ecm.platform.relations.api.QNameResource;
import org.nuxeo.ecm.platform.relations.api.Statement;
import org.nuxeo.ecm.platform.relations.api.RelationManager;
import org.nuxeo.ecm.platform.relations.api.impl.ResourceImpl;
import org.nuxeo.ecm.platform.relations.api.impl.StatementImpl;
import org.nuxeo.ecm.platform.relations.api.impl.LiteralImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * Simple implementation of a {@link PublicationTree} using the Core Sections.
 *
 * @author tiry
 */
public class SectionPublicationTree extends AbstractBasePublicationTree
        implements PublicationTree {

    private static final long serialVersionUID = 1L;

    protected static final String CAN_ASK_FOR_PUBLISHING = "CanAskForPublishing";

    protected static final String DEFAULT_ROOT_PATH = "/default-domain/sections";

    protected DocumentModel treeRoot;

    @Override
    public void initTree(String sid, CoreSession coreSession,
            Map<String, String> parameters, PublishedDocumentFactory factory,
            String configName) throws ClientException {
        super.initTree(sid, coreSession, parameters, factory, configName);
        treeRoot = coreSession.getDocument(new PathRef(rootPath));
        rootNode = new CoreFolderPublicationNode(treeRoot, getConfigName(),
                sid, factory);
    }

    protected CoreSession getCoreSession() {
        return CoreInstance.getInstance().getSession(treeRoot.getSessionId());
    }

    public List<PublishedDocument> getExistingPublishedDocument(
            DocumentLocation docLoc) throws ClientException {
        DocumentModel livedoc = getCoreSession().getDocument(docLoc.getDocRef());
        if (livedoc.isProxy()) {
            livedoc = getCoreSession().getDocument(new IdRef(livedoc.getSourceId()));
        }

        List<DocumentModel> possibleDocsToCheck = new ArrayList<DocumentModel>();
        if (!livedoc.isVersion()) {
            possibleDocsToCheck = getCoreSession().getVersions(
                    docLoc.getDocRef());
        } else {
            possibleDocsToCheck.add(livedoc);
        }

        List<PublishedDocument> publishedDocs = new ArrayList<PublishedDocument>();
        for (DocumentModel doc : possibleDocsToCheck) {
            DocumentModelList proxies = getCoreSession().getProxies(
                    doc.getRef(), null);
            for (DocumentModel proxy : proxies) {
                if (proxy.getPathAsString().startsWith(
                        treeRoot.getPathAsString())) {
                    publishedDocs.add(factory.wrapDocumentModel(proxy));
                }
            }
        }
        return publishedDocs;
    }

    @Override
    public PublishedDocument publish(DocumentModel doc, PublicationNode targetNode) throws ClientException {
        SimpleCorePublishedDocument publishedDocument = (SimpleCorePublishedDocument) super.publish(doc, targetNode);
        PublicationRelationHelper.addPublicationRelation(publishedDocument.getProxy(), this);
        return publishedDocument;
    }

    @Override
    public PublishedDocument publish(DocumentModel doc, PublicationNode targetNode, Map<String, String> params) throws ClientException {
        SimpleCorePublishedDocument publishedDocument = (SimpleCorePublishedDocument) super.publish(doc, targetNode, params);
        PublicationRelationHelper.addPublicationRelation(publishedDocument.getProxy(), this);
        return publishedDocument;
    }

    public void unpublish(DocumentModel doc, PublicationNode targetNode)
            throws ClientException {
        List<PublishedDocument> publishedDocs = getPublishedDocumentInNode(targetNode);
        for (PublishedDocument pubDoc : publishedDocs) {
            if (pubDoc.getSourceDocumentRef().equals(doc.getRef())) {
                unpublish(pubDoc);
            }
        }
    }

    public void unpublish(PublishedDocument publishedDocument)
            throws ClientException {
        DocumentModel proxy = ((SimpleCorePublishedDocument) publishedDocument).getProxy();
        PublicationRelationHelper.removePublicationRelation(proxy);
        getCoreSession().removeDocument(proxy.getRef());
    }

    public PublicationNode getNodeByPath(String path) throws ClientException {
        return new CoreFolderPublicationNode(
                coreSession.getDocument(new PathRef(path)), getConfigName(),
                getSessionId(), factory);
    }

    public void release() {
        // TODO Auto-generated method stub
    }

    @Override
    protected String getDefaultRootPath() {
        return DEFAULT_ROOT_PATH;
    }

    @Override
    protected PublishedDocumentFactory getDefaultFactory() {
        return new CoreProxyFactory();
    }

    @Override
    public boolean canPublishTo(PublicationNode publicationNode) throws ClientException {
        if (publicationNode.getParent() == null) {
            // we can't publish in the root node
            return false;
        }
        DocumentRef docRef = new PathRef(publicationNode.getPath());
        return coreSession.hasPermission(docRef, CAN_ASK_FOR_PUBLISHING);
    }

    @Override
    public boolean canUnpublish(PublishedDocument publishedDocument) throws ClientException {
        DocumentRef docRef = new PathRef(publishedDocument.getParentPath());
        return coreSession.hasPermission(docRef, SecurityConstants.WRITE);
    }

    public PublishedDocument wrapToPublishedDocument(DocumentModel documentModel) throws ClientException {
        if (PublicationRelationHelper.isPublished(documentModel)) {
            return factory.wrapDocumentModel(documentModel);
        } else {
            throw new ClientException("Document " + documentModel.getPathAsString() + " is not a published document.");
        }
    }

    @Override
    public boolean isPublicationNode(DocumentModel documentModel) throws ClientException {
        return documentModel.getPathAsString().startsWith(rootPath);
    }

    @Override
    public PublicationNode wrapToPublicationNode(DocumentModel documentModel) throws ClientException {
        if (!isPublicationNode(documentModel)) {
            throw new ClientException("Document " + documentModel.getPathAsString() + " is not a valid publication node.");
        }
        return new CoreFolderPublicationNode(documentModel, getConfigName(),
                sid, factory);
    }

}
