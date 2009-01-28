package org.nuxeo.ecm.core.event.impl;

import java.io.Serializable;
import java.rmi.dgc.VMID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.repository.Repository;
import org.nuxeo.ecm.core.api.repository.RepositoryManager;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventBundle;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.ReconnectedEventBundle;
import org.nuxeo.runtime.api.Framework;

/**
 * Default implementation for an {@link EventBundle} that need to be reconnected
 * to a usable Session
 *
 * @author tiry
 *
 */
public class ReconnectedEventBundleImpl implements ReconnectedEventBundle {

    private static final long serialVersionUID = 1L;

    protected EventBundle sourceEventBundle;

    protected List<Event> reconnectedEvents;

    protected LoginContext loginCtx;

    protected CoreSession reconnectedCoreSession;

    private static final Log log = LogFactory
            .getLog(ReconnectedEventBundleImpl.class);

    public ReconnectedEventBundleImpl() {

    }

    public ReconnectedEventBundleImpl(EventBundle sourceEventBundle) {
        this.sourceEventBundle = sourceEventBundle;
    }

    protected CoreSession getReconnectedCoreSession(String repoName) {
        if (reconnectedCoreSession == null) {
            try {
                loginCtx = Framework.login();
            } catch (LoginException e) {
                log.error("Can not connect", e);
                return null;
            }

            try {
                RepositoryManager mgr = Framework
                        .getService(RepositoryManager.class);
                Repository repo;
                if (repoName != null) {
                    repo = mgr.getRepository(repoName);
                } else {
                    repo = mgr.getDefaultRepository();
                }

                reconnectedCoreSession = repo.open();
            } catch (Exception e) {
                log.error("Error while openning core session", e);
                return null;
            }
        } else {
            // Sanity Check
            if (!reconnectedCoreSession.getRepositoryName().equals(repoName)) {
                if (repoName!=null) {
                    throw new IllegalStateException(
                            "Can no reconnected a Bundle tied to several Core instances !");
                }
            }
        }
        return reconnectedCoreSession;
    }

    protected List<Event> getReconnectedEvents() {
        if (reconnectedEvents == null) {
            reconnectedEvents = new ArrayList<Event>();
            for (Event event : sourceEventBundle.getEvents()) {

                EventContext ctx = event.getContext();
                EventContext newCtx = null;
                CoreSession session = null;

                if (ctx.getCoreSession()!=null) {
                 session = getReconnectedCoreSession(ctx
                        .getCoreSession().getRepositoryName());
                }

                List<Object> newArgs = new ArrayList<Object>();
                for (Object arg : ctx.getArguments()) {
                    Object newArg = arg;
                    if ((arg instanceof DocumentModel) && (session!=null)) {
                        DocumentModel oldDoc = (DocumentModel) arg;
                        DocumentRef ref = oldDoc.getRef();
                        if (ref != null) {
                            try {
                                if (session.exists(oldDoc.getRef())) {
                                    newArg = session.getDocument(oldDoc.getRef());
                                } else {
                                    // probably deleted doc
                                    newArg = oldDoc;
                                }
                            } catch (ClientException e) {
                                log.error("Can not refetch Doc with ref "
                                        + ref.toString(), e);
                            }
                        }
                    }
                    // XXX treat here other cases !!!!
                    newArgs.add(newArg);
                }

                if (ctx instanceof DocumentEventContext) {
                    newCtx = new DocumentEventContext(session, ctx
                            .getPrincipal(), (DocumentModel) newArgs.get(0),
                            (DocumentRef) newArgs.get(1));
                } else {
                    newCtx = new EventContextImpl(session, ctx.getPrincipal());
                    ((EventContextImpl) newCtx).setArgs(newArgs.toArray());
                }

                Map<String, Serializable> newProps = new HashMap<String, Serializable>();
                for (Entry<String, Serializable> prop : ctx.getProperties()
                        .entrySet()) {
                    Serializable propValue = prop.getValue();
                    if ((propValue instanceof DocumentModel) && (session!=null)) {
                        DocumentModel oldDoc = (DocumentModel) propValue;
                        try {
                            propValue = session.getDocument(oldDoc.getRef());
                        } catch (ClientException e) {
                            log.error("Can not refetch Doc with ref "
                                    + oldDoc.getRef().toString(), e);
                        }
                    }
                    // XXX treat here other cases !!!!
                    newProps.put(prop.getKey(), propValue);
                }
                newCtx.setProperties(newProps);
                Event newEvt = new EventImpl(event.getName(), newCtx, event
                        .getFlags(), event.getTime());
                reconnectedEvents.add(newEvt);
            }
        }
        return reconnectedEvents;
    }

    public String[] getEventNames() {
        return sourceEventBundle.getEventNames();
    }

    public Event[] getEvents() {
        return getReconnectedEvents().toArray(
                new Event[getReconnectedEvents().size()]);
    }

    public String getName() {
        return sourceEventBundle.getName();
    }

    public VMID getSourceVMID() {
        return sourceEventBundle.getSourceVMID();
    }

    public boolean hasRemoteSource() {
        return sourceEventBundle.hasRemoteSource();
    }

    public boolean isEmpty() {
        return sourceEventBundle.isEmpty();
    }

    public Event peek() {
        return getReconnectedEvents().get(0);
    }

    public void push(Event event) {
        throw new UnsupportedOperationException();
    }

    public int size() {
        return sourceEventBundle.size();
    }

    public Iterator<Event> iterator() {
        return getReconnectedEvents().iterator();
    }

    public void disconnect() {
        if (reconnectedCoreSession != null) {
            CoreInstance.getInstance().close(reconnectedCoreSession);
        }
        if (loginCtx != null) {
            try {
                loginCtx.logout();
            } catch (LoginException e) {
                log.error("Error while logging out", e);
            }
        }
    }

    public boolean comesFromJMS() {
        return false;
    }
}
