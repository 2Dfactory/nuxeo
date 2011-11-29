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
package org.nuxeo.ecm.platform.task.core;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.NuxeoPrincipal;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.repository.jcr.testing.RepositoryOSGITestCase;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskComment;
import org.nuxeo.ecm.platform.task.TaskService;
import org.nuxeo.ecm.platform.task.test.TaskUTConstants;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.runtime.api.Framework;

/**
 * @author Anahide Tchertchian
 */
public class TaskServiceTest extends RepositoryOSGITestCase {

    protected TaskService taskService;

    protected UserManager userManager;

    protected NuxeoPrincipal administrator;

    protected NuxeoPrincipal user1;

    protected NuxeoPrincipal user2;

    protected NuxeoPrincipal user3;

    protected NuxeoPrincipal user4;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        deployBundle("org.nuxeo.ecm.platform.content.template");
        deployBundle("org.nuxeo.ecm.directory");
        deployBundle("org.nuxeo.ecm.platform.usermanager");
        deployBundle("org.nuxeo.ecm.directory.types.contrib");
        deployBundle("org.nuxeo.ecm.directory.sql");

        deployBundle(TaskUTConstants.CORE_BUNDLE_NAME);
        deployBundle(TaskUTConstants.TESTING_BUNDLE_NAME);

        taskService = Framework.getService(TaskService.class);

        userManager = Framework.getService(UserManager.class);
        assertNotNull(userManager);

        administrator = userManager.getPrincipal(SecurityConstants.ADMINISTRATOR);
        assertNotNull(administrator);

        user1 = userManager.getPrincipal("myuser1");
        assertNotNull(user1);

        user2 = userManager.getPrincipal("myuser2");
        assertNotNull(user2);

        user3 = userManager.getPrincipal("myuser3");
        assertNotNull(user3);

        user4 = userManager.getPrincipal("myuser4");
        assertNotNull(user4);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @SuppressWarnings("unchecked")
    public void testSingleTaskWithAccept() throws Exception {
        DocumentModel document = getDocument();
        assertNotNull(document);

        // create task as admin
        List<String> actors = new ArrayList<String>();
        actors.add(user1.getName());
        actors.add(SecurityConstants.MEMBERS);
        Calendar calendar = Calendar.getInstance();
        calendar.set(2006, 6, 6);

        // create one task for all actors
        taskService.createTask(coreSession, user3, document, "Test Task Name",
                actors, false, "test directive", "test comment",
                calendar.getTime(), null, null);

        List<Task> tasks = taskService.getTaskInstances(document,(NuxeoPrincipal) null, coreSession);
        assertNotNull(tasks);
        assertEquals(1, tasks.size());

        Task task = tasks.get(0);
        assertEquals("Test Task Name", task.getName());

        List<String> pooledActorIds = task.getActors();
        assertEquals(2, pooledActorIds.size());
        assertTrue(pooledActorIds.contains(SecurityConstants.MEMBERS));
        assertTrue(pooledActorIds.contains(user1.getName()));

        List<TaskComment> comments = task.getComments();
        assertEquals(1, comments.size());

        TaskComment comment = comments.get(0);
        assertEquals(user3.getName(), comment.getAuthor());
        assertEquals("test comment", comment.getText());
        assertEquals(calendar.getTime(), task.getDueDate());
        // task status
        assertTrue(task.isOpened());
        assertFalse(task.isCancelled());
        assertFalse(task.hasEnded());

        assertEquals(4, task.getVariables().size());
        assertEquals(
                document.getRepositoryName(),
                task.getVariable(TaskService.VariableName.documentRepositoryName.name()));
        assertEquals(document.getId(),
                task.getVariable(TaskService.VariableName.documentId.name()));
        assertEquals("test directive",
                task.getVariable(TaskService.VariableName.directive.name()));
        assertEquals(
                "true",
                task.getVariable(TaskService.VariableName.createdFromTaskService.name()));

        assertEquals(user3.getName(),
                task.getInitiator());
        // test rights for each user
        // initiator or admin can end a task
        assertTrue(taskService.canEndTask(administrator, task));
        assertTrue(taskService.canEndTask(user3, task));
        // user 1 is in actors
        assertTrue(taskService.canEndTask(user1, task));
        // user 2 is in the members group
        assertTrue(taskService.canEndTask(user2, task));
        // user 4 is not in the members group
        assertFalse(taskService.canEndTask(user4, task));

        // test ending of the task

        try {
            taskService.acceptTask(coreSession, user4, task, "ok i'm in");
            fail("Should have raised an exception: user4 cannot end the task");
        } catch (ClientException e) {
            assertEquals("User with id 'myuser4' cannot end this task",
                    e.getMessage());
        }

        // accept task
        taskService.acceptTask(coreSession, user1, task, "ok i'm in");

        session.save();

        // test task again
        tasks = taskService.getTaskInstances(document,(NuxeoPrincipal) null, coreSession);
        assertNotNull(tasks);
        // ended tasks are filtered
        assertEquals(0, tasks.size());

        // retrieve the task another way
        String taskId = task.getId();
        task = getTask(taskId);
        assertNotNull(task);
        assertEquals("Test Task Name", task.getName());

        pooledActorIds = task.getActors();
        assertEquals(2, pooledActorIds.size());
        assertTrue(pooledActorIds.contains(SecurityConstants.MEMBERS));
        assertTrue(pooledActorIds.contains(user1.getName()));

        comments = task.getComments();
        assertEquals(2, comments.size());

        comment = comments.get(0);
        assertEquals(user3.getName(), comment.getAuthor());
        assertEquals("test comment", comment.getText());
        assertEquals(calendar.getTime(), task.getDueDate());
        // task status
        assertFalse(task.isOpened());
        assertFalse(task.isCancelled());
        assertTrue(task.hasEnded());
        assertEquals(5, task.getVariables().size());
        assertEquals(
                document.getRepositoryName(),
                task.getVariable(TaskService.VariableName.documentRepositoryName.name()));
        assertEquals(document.getId(),
                task.getVariable(TaskService.VariableName.documentId.name()));
         assertEquals("test directive",
                task.getVariable(TaskService.VariableName.directive.name()));
        assertEquals(
                "true",
                task.getVariable(TaskService.VariableName.createdFromTaskService.name()));
        assertEquals("true",
                task.getVariable(TaskService.VariableName.validated.name()));
    }

    public void testMultipleTaskWithReject() throws Exception {
        DocumentModel document = getDocument();
        assertNotNull(document);

        // create task as admin
        List<String> actors = new ArrayList<String>();
        actors.add(user1.getName());
        actors.add(SecurityConstants.MEMBERS);
        Calendar calendar = Calendar.getInstance();
        calendar.set(2006, 6, 6);

        // create one task per actor
        taskService.createTask(coreSession, user3, document, "Test Task Name",
                actors, true, "test directive", "test comment",
                calendar.getTime(), null, null);

        List<Task> tasks = taskService.getTaskInstances(document,(NuxeoPrincipal) null, coreSession);
        Collections.sort(tasks, new Comparator<Task>() {

            @Override
            public int compare(Task o1, Task o2) {
                try {
                    return o1.getCreated().compareTo(o2.getCreated());
                } catch (ClientException e) {
                    throw new RuntimeException();
                }
            }

        });
        assertNotNull(tasks);
        assertEquals(2, tasks.size());

        Task task1 = tasks.get(0);
        assertEquals("Test Task Name", task1.getName());

        List<String> pooledActorIds = task1.getActors();
        assertEquals(1, pooledActorIds.size());
        assertEquals(user1.getName(),
                pooledActorIds.get(0));

        List<TaskComment> comments = task1.getComments();
        assertEquals(1, comments.size());

        TaskComment comment = comments.get(0);
        assertEquals(user3.getName(), comment.getAuthor());
        assertEquals("test comment", comment.getText());
        assertEquals(calendar.getTime(), task1.getDueDate());
        // task status
        assertTrue(task1.isOpened());
        assertFalse(task1.isCancelled());
        assertFalse(task1.hasEnded());
        assertEquals(4, task1.getVariables().size());
        assertEquals(
                document.getRepositoryName(),
                task1.getVariable(TaskService.VariableName.documentRepositoryName.name()));
        assertEquals(document.getId(),
                task1.getVariable(TaskService.VariableName.documentId.name()));
        assertEquals(
                "test directive",
                task1.getVariable(TaskService.VariableName.directive.name()));
        assertEquals(
                "true",
                task1.getVariable(TaskService.VariableName.createdFromTaskService.name()));
        assertEquals(user3.getName(),
                task1.getInitiator());

        // test rights for each user
        // initiator or admin can end a task
        assertTrue(taskService.canEndTask(administrator, task1));
        assertTrue(taskService.canEndTask(user3, task1));
        // user 1 is in actors
        assertTrue(taskService.canEndTask(user1, task1));
        assertFalse(taskService.canEndTask(user2, task1));
        assertFalse(taskService.canEndTask(user4, task1));

        // test ending of the task
        try {
            taskService.rejectTask(coreSession, user2, task1, "i don't agree");
            fail("Should have raised an exception: user2 cannot end the task");
        } catch (ClientException e) {
            assertEquals("User with id 'myuser2' cannot end this task",
                    e.getMessage());
        }

        // reject task as user1
        taskService.rejectTask(coreSession, user1, task1, "i don't agree");
        session.save();
        // test task again
        tasks = taskService.getTaskInstances(document,(NuxeoPrincipal) null, coreSession);
        assertNotNull(tasks);
        // ended tasks are filtered
        assertEquals(1, tasks.size());

        // retrieve the task another way
        final String taskId = task1.getId();
        task1 = getTask(taskId);
        assertNotNull(task1);
        assertEquals("Test Task Name", task1.getName());

        pooledActorIds = task1.getActors();
        assertEquals(1, pooledActorIds.size());
        assertEquals(user1.getName(),
                pooledActorIds.get(0));

        comments = task1.getComments();
        assertEquals(2, comments.size());

        comment = comments.get(0);
        assertEquals(user3.getName(), comment.getAuthor());
        assertEquals("test comment", comment.getText());
        assertEquals(calendar.getTime(), task1.getDueDate());
        // task status
        assertFalse(task1.isOpened());
        assertFalse(task1.isCancelled());
        assertTrue(task1.hasEnded());
        assertEquals(5, task1.getVariables().size());
        assertEquals(
                document.getRepositoryName(),
                task1.getVariable(TaskService.VariableName.documentRepositoryName.name()));
        assertEquals(document.getId(),
                task1.getVariable(TaskService.VariableName.documentId.name()));
        assertEquals(
                "test directive",
                task1.getVariable(TaskService.VariableName.directive.name()));
        assertEquals(
                "true",
                task1.getVariable(TaskService.VariableName.createdFromTaskService.name()));
        assertEquals(
                "false",
                task1.getVariable(TaskService.VariableName.validated.name()));
        assertEquals(user3.getName(),
                task1.getInitiator());

        // check second task
        Task task2 = tasks.get(0);
        assertEquals("Test Task Name", task2.getName());

        pooledActorIds = task2.getActors();
        assertEquals(1, pooledActorIds.size());
        assertEquals(SecurityConstants.MEMBERS,
                pooledActorIds.get(0));

        comments = task2.getComments();
        assertEquals(1, comments.size());

        comment = comments.get(0);
        assertEquals(user3.getName(), comment.getAuthor());
        assertEquals("test comment", comment.getText());
        assertEquals(calendar.getTime(), task2.getDueDate());
        // task status
        assertTrue(task2.isOpened());
        assertFalse(task2.isCancelled());
        assertFalse(task2.hasEnded());
        assertEquals(4, task2.getVariables().size());
        assertEquals(
                document.getRepositoryName(),
                task2.getVariable(TaskService.VariableName.documentRepositoryName.name()));
        assertEquals(document.getId(),
                task2.getVariable(TaskService.VariableName.documentId.name()));
        assertEquals(
                "test directive",
                task2.getVariable(TaskService.VariableName.directive.name()));
        assertEquals(
                "true",
                task2.getVariable(TaskService.VariableName.createdFromTaskService.name()));
        assertEquals(user3.getName(),
                task2.getInitiator());

        // test rights for each user
        // initiator or admin can end a task
        assertTrue(taskService.canEndTask(administrator, task2));
        assertTrue(taskService.canEndTask(user3, task2));
        // user 1 is in actors
        assertTrue(taskService.canEndTask(user1, task2));
        // user 2 is in the members group
        assertTrue(taskService.canEndTask(user2, task2));
        // user 4 is not in the members group
        assertFalse(taskService.canEndTask(user4, task2));

        // test ending of the task
        try {
            taskService.acceptTask(coreSession, user4, task2, "i don't agree");
            fail("Should have raised an exception: user4 cannot end the task");
        } catch (ClientException e) {
            assertEquals("User with id 'myuser4' cannot end this task",
                    e.getMessage());
        }

        // accept task as user1
        taskService.acceptTask(coreSession, user1, task2, "i don't agree");
        session.save();
        tasks = taskService.getTaskInstances(document,(NuxeoPrincipal) null, coreSession);
        assertNotNull(tasks);
        assertEquals(0, tasks.size());
    }

    protected Task getTask(final String taskId) throws ClientException {
        DocumentModel taskDoc = coreSession.getDocument(new IdRef(taskId));
        if (taskDoc != null) {
            return taskDoc.getAdapter(Task.class);
        }
        return null;
    }

    protected DocumentModel getDocument() throws Exception {
        openRepository();
        CoreSession session = getCoreSession();
        DocumentModel model = session.createDocumentModel(
                session.getRootDocument().getPathAsString(), "1", "File");
        DocumentModel doc = session.createDocument(model);
        assertNotNull(doc);

        session.saveDocument(doc);
        session.save();
        return doc;
    }

}
