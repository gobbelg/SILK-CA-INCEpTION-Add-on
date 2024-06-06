/*
 * Copyright 2019
 * Ubiquitous Knowledge Processing (UKP) Lab
 * Technische Universität Darmstadt
 * 
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.ui.curation.sidebar;

import static de.tudarmstadt.ukp.clarin.webanno.model.PermissionLevel.ANNOTATOR;
import static de.tudarmstadt.ukp.clarin.webanno.model.SourceDocumentState.CURATION_FINISHED;
import static de.tudarmstadt.ukp.inception.support.WebAnnoConst.CURATION_USER;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import javax.persistence.EntityManager;

import org.apache.commons.lang3.Validate;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.uima.cas.CAS;
import org.springframework.context.event.EventListener;
import org.springframework.security.core.session.SessionDestroyedEvent;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.transaction.annotation.Transactional;

import de.tudarmstadt.ukp.clarin.webanno.api.casstorage.CasStorageService;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocument;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationDocumentState;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.security.model.User;
import de.tudarmstadt.ukp.inception.annotation.events.DocumentOpenedEvent;
import de.tudarmstadt.ukp.inception.curation.config.CurationServiceAutoConfiguration;
import de.tudarmstadt.ukp.inception.curation.model.CurationSettings;
import de.tudarmstadt.ukp.inception.curation.model.CurationSettingsId;
import de.tudarmstadt.ukp.inception.documents.api.DocumentService;
import de.tudarmstadt.ukp.inception.project.api.ProjectService;
import de.tudarmstadt.ukp.inception.rendering.editorstate.AnnotatorState;

/**
 * <p>
 * This class is exposed as a Spring Component via
 * {@link CurationServiceAutoConfiguration#curationService}.
 * </p>
 */
public class CurationSidebarServiceImpl
    implements CurationSidebarService
{
    // stores info on which users are selected and which doc is the curation-doc
    private ConcurrentMap<CurationSessionKey, CurationSession> sessions;

    private final EntityManager entityManager;
    private final DocumentService documentService;
    private final SessionRegistry sessionRegistry;
    private final ProjectService projectService;
    private final UserDao userRegistry;
    private final CasStorageService casStorageService;

    public CurationSidebarServiceImpl(EntityManager aEntityManager,
            DocumentService aDocumentService, SessionRegistry aSessionRegistry,
            ProjectService aProjectService, UserDao aUserRegistry,
            CasStorageService aCasStorageService)
    {
        sessions = new ConcurrentHashMap<>();
        entityManager = aEntityManager;
        documentService = aDocumentService;
        sessionRegistry = aSessionRegistry;
        projectService = aProjectService;
        userRegistry = aUserRegistry;
        casStorageService = aCasStorageService;
    }

    /**
     * Key to identify curation session for a specific user and project
     */
    private class CurationSessionKey
    {
        private String username;
        private long projectId;

        public CurationSessionKey(String aUser, long aProject)
        {
            username = aUser;
            projectId = aProject;
        }

        @Override
        public int hashCode()
        {
            return new HashCodeBuilder().append(username).append(projectId).toHashCode();
        }

        @Override
        public boolean equals(Object aOther)
        {
            if (!(aOther instanceof CurationSessionKey)) {
                return false;
            }
            CurationSessionKey castOther = (CurationSessionKey) aOther;
            return new EqualsBuilder().append(username, castOther.username)
                    .append(projectId, castOther.projectId).isEquals();
        }
    }

    private CurationSession getSession(String aSessionOwner, long aProjectId)
    {
        if (sessions.containsKey(new CurationSessionKey(aSessionOwner, aProjectId))) {
            return sessions.get(new CurationSessionKey(aSessionOwner, aProjectId));
        }
        else {
            return readSession(aSessionOwner, aProjectId);
        }
    }

    private CurationSession readSession(String aSessionOwner, long aProjectId)
    {
        List<CurationSettings> settings = queryDBForSetting(aSessionOwner, aProjectId);

        CurationSession state;
        if (settings.isEmpty()) {
            state = new CurationSession(aSessionOwner);
        }
        else {
            CurationSettings setting = settings.get(0);
            Project project = projectService.getProject(aProjectId);
            List<User> users = new ArrayList<>();
            if (!setting.getSelectedUserNames().isEmpty()) {
                users = setting.getSelectedUserNames().stream()
                        .map(username -> userRegistry.get(username))
                        .filter(user -> projectService.hasAnyRole(user, project)) //
                        .collect(toList());
            }
            state = new CurationSession(setting.getCurationUserName(), users);
        }

        sessions.put(new CurationSessionKey(aSessionOwner, aProjectId), state);
        return state;
    }

    private List<CurationSettings> queryDBForSetting(String aSessionOwner, long aProjectId)
    {
        Validate.notBlank(aSessionOwner, "User must be specified");
        Validate.notNull(aProjectId, "project must be specified");

        String query = "FROM " + CurationSettings.class.getName() //
                + " o WHERE o.username = :username " //
                + "AND o.projectId = :projectId";

        List<CurationSettings> settings = entityManager //
                .createQuery(query, CurationSettings.class) //
                .setParameter("username", aSessionOwner) //
                .setParameter("projectId", aProjectId) //
                .setMaxResults(1) //
                .getResultList();
        return settings;
    }

    @Transactional
    @Override
    public void setSelectedUsers(String aSessionOwner, long aProjectId,
            Collection<User> aSelectedUsers)
    {
        synchronized (sessions) {
            var session = sessions.get(new CurationSessionKey(aSessionOwner, aProjectId));
            if (session == null) {
                return;
            }

            session.setSelectedUsers(aSelectedUsers);
        }
    }

    @Transactional
    @Override
    public List<User> getSelectedUsers(String aSessionOwner, long aProjectId)
    {
        synchronized (sessions) {
            var session = sessions.get(new CurationSessionKey(aSessionOwner, aProjectId));
            if (session == null) {
                return Collections.emptyList();
            }

            var selectedUsers = session.getSelectedUsers();
            return selectedUsers == null ? emptyList() : unmodifiableList(selectedUsers);
        }
    }

    @Transactional
    @Override
    public List<User> listUsersReadyForCuration(String aSessionOwner, Project aProject,
            SourceDocument aDocument)
    {
        synchronized (sessions) {
            var session = sessions.get(new CurationSessionKey(aSessionOwner, aProject.getId()));
            if (session == null) {
                return Collections.emptyList();
            }

            var selectedUsers = session.getSelectedUsers();

            if (selectedUsers == null || selectedUsers.isEmpty()) {
                return new ArrayList<>();
            }

            List<User> finishedUsers = listCuratableUsers(aDocument);
            finishedUsers.retainAll(selectedUsers);
            return finishedUsers;
        }
    }

    @Override
    @Transactional
    public List<User> listCuratableUsers(String aSessionOwner, SourceDocument aDocument)
    {
        String curationTarget = getCurationTarget(aSessionOwner, aDocument.getProject().getId());
        return listCuratableUsers(aDocument).stream()
                .filter(user -> !user.getUsername().equals(aSessionOwner)
                        || curationTarget.equals(CURATION_USER))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public List<User> listCuratableUsers(SourceDocument aSourceDocument)
    {
        Validate.notNull(aSourceDocument, "Document must be specified");

        String query = String.join("\n", //
                "SELECT u FROM User u, AnnotationDocument d", //
                "WHERE u.username = d.user", //
                "AND d.document   = :document", //
                "AND (d.state = :state or d.annotatorState = :ignore)", //
                "ORDER BY u.username ASC");

        List<User> finishedUsers = new ArrayList<>(entityManager //
                .createQuery(query, User.class) //
                .setParameter("document", aSourceDocument) //
                .setParameter("state", AnnotationDocumentState.FINISHED) //
                .setParameter("ignore", AnnotationDocumentState.IGNORE) //
                .getResultList());

        return finishedUsers;
    }

    @Transactional
    @Override
    public Optional<CAS> retrieveCurationCAS(String aSessionOwner, long aProjectId,
            SourceDocument aDoc)
        throws IOException
    {
        String curationUser = getSession(aSessionOwner, aProjectId).getCurationTarget();
        if (curationUser == null) {
            return Optional.empty();
        }

        return Optional.of(documentService.readAnnotationCas(aDoc, curationUser));
    }

    // REC: Do we really needs this? Why not save via the AnnotationPage facilities? Or at least
    // the curation target should already be set in the annotator state, so why not rely on that?
    @Transactional
    @Override
    public void writeCurationCas(CAS aTargetCas, AnnotatorState aState, long aProjectId)
        throws IOException
    {
        User curator;
        synchronized (sessions) {
            String curatorName = getSession(aState.getUser().getUsername(), aProjectId)
                    .getCurationTarget();

            curator = userRegistry.getUserOrCurationUser(curatorName);
        }

        SourceDocument doc = aState.getDocument();
        AnnotationDocument annoDoc = documentService.createOrGetAnnotationDocument(doc, curator);
        documentService.writeAnnotationCas(aTargetCas, annoDoc, true);
        casStorageService.getCasTimestamp(doc, curator.getUsername())
                .ifPresent(aState::setAnnotationDocumentTimestamp);
    }

    @Transactional
    @Override
    public void setCurationTarget(String aSessionOwner, Project aProject, boolean aOwnDocument)
    {
        synchronized (sessions) {
            var session = sessions.get(new CurationSessionKey(aSessionOwner, aProject.getId()));
            if (session == null) {
                return;
            }

            session.curationTarget = aOwnDocument
                    && projectService.hasRole(aSessionOwner, aProject, ANNOTATOR) ? aSessionOwner
                            : CURATION_USER;

        }
    }

    @Override
    public boolean existsSession(String aSessionOwner, long aProjectId)
    {
        synchronized (sessions) {
            return sessions.containsKey(new CurationSessionKey(aSessionOwner, aProjectId));
        }
    }

    @Override
    public void startSession(String aSessionOwner, Project aProject, boolean aOwnDocument)
    {
        synchronized (sessions) {
            getSession(aSessionOwner, aProject.getId());
            setCurationTarget(aSessionOwner, aProject, aOwnDocument);
        }
    }

    @Override
    public void closeSession(String aSessionOwner, long aProjectId)
    {
        synchronized (sessions) {
            sessions.remove(new CurationSessionKey(aSessionOwner, aProjectId));
        }
    }

    @EventListener
    @Transactional
    public void onDocumentOpened(DocumentOpenedEvent aEvent)
    {
        setDefaultSelectedUsersForDocument(aEvent.getSessionOwner(), aEvent.getDocument());
    }

    @Override
    public void setDefaultSelectedUsersForDocument(String aSessionOwner, SourceDocument aDocument)
    {
        var project = aDocument.getProject();

        if (!existsSession(aSessionOwner, project.getId())) {
            return;
        }

        // The set of curatable annotators can change from document to document, so we reset the
        // selected users every time the document is switched
        setSelectedUsers(aSessionOwner, project.getId(),
                listCuratableUsers(aSessionOwner, aDocument));
    }

    @EventListener
    @Transactional
    public void onSessionDestroyed(SessionDestroyedEvent event)
    {
        SessionInformation info = sessionRegistry.getSessionInformation(event.getId());

        if (info == null) {
            return;
        }

        User user = null;
        if (info.getPrincipal() instanceof String) {
            user = userRegistry.get((String) info.getPrincipal());
        }

        if (info.getPrincipal() instanceof User) {
            user = (User) info.getPrincipal();
        }

        if (user == null) {
            // This happens e.g. when a session for "anonymousUser" is destroyed or if (for some
            // reason), the user owning the session no longer exists in the system.
            return;
        }

        // FIXME: Seems kind of pointless to first store everything and then delete it...
        storeCurationSettings(user);
        closeAllSessions(user);
    }

    /**
     * Write settings for all projects of this user to the data base
     */
    private void storeCurationSettings(User aSessionOwner)
    {
        String aUsername = aSessionOwner.getUsername();

        for (Project project : projectService.listAccessibleProjects(aSessionOwner)) {
            Long projectId = project.getId();
            Set<String> usernames = null;
            if (sessions.containsKey(new CurationSessionKey(aUsername, projectId))) {

                CurationSession state = sessions.get(new CurationSessionKey(aUsername, projectId));
                // user does not exist anymore or is anonymous authentication
                if (state == null) {
                    continue;
                }

                if (state.getSelectedUsers() != null) {
                    usernames = state.getSelectedUsers().stream() //
                            .map(User::getUsername) //
                            .collect(toSet());
                }

                // get setting from context and update values if it exists, else save new setting
                // to db
                CurationSettings setting = entityManager.find(CurationSettings.class,
                        new CurationSettingsId(projectId, aUsername));

                if (setting != null) {
                    setting.setSelectedUserNames(usernames);
                    setting.setCurationUserName(state.getCurationTarget());
                }
                else {
                    setting = new CurationSettings(aUsername, projectId, state.getCurationTarget(),
                            usernames);
                    entityManager.persist(setting);
                }
            }
        }
    }

    private void closeAllSessions(User aSessionOwner)
    {
        projectService.listAccessibleProjects(aSessionOwner).stream() //
                .map(Project::getId) //
                .forEach(pId -> closeSession(aSessionOwner.getUsername(), pId));
    }

    @Transactional
    @Override
    public boolean isShowAll(String aSessionOwner, Long aProjectId)
    {
        synchronized (sessions) {
            return getSession(aSessionOwner, aProjectId).isShowAll();
        }
    }

    @Transactional
    @Override
    public void setShowAll(String aSessionOwner, Long aProjectId, boolean aValue)
    {
        synchronized (sessions) {
            getSession(aSessionOwner, aProjectId).setShowAll(aValue);
        }
    }

    @Transactional
    @Override
    public String getCurationTarget(String aSessionOwner, long aProjectId)
    {
        String curationUser;
        synchronized (sessions) {
            curationUser = getSession(aSessionOwner, aProjectId).getCurationTarget();
        }

        if (curationUser == null) {
            // default to user as curation target
            return aSessionOwner;
        }

        return curationUser;
    }

    @Transactional
    @Override
    public User getCurationTargetUser(String aSessionOwner, long aProjectId)
    {
        String curationUser;
        synchronized (sessions) {
            curationUser = getSession(aSessionOwner, aProjectId).getCurationTarget();
        }

        if (curationUser == null) {
            // default to user as curation target
            return userRegistry.get(aSessionOwner);
        }

        if (CURATION_USER.equals(curationUser)) {
            return userRegistry.getCurationUser();
        }

        return userRegistry.get(curationUser);
    }

    @Override
    public boolean isCurationFinished(AnnotatorState aState, String aSessionOwner)
    {
        String username = aState.getUser().getUsername();
        SourceDocument sourceDoc = aState.getDocument();
        return (username.equals(aSessionOwner)
                && documentService.isAnnotationFinished(sourceDoc, aState.getUser()))
                || (username.equals(CURATION_USER)
                        && sourceDoc.getState().equals(CURATION_FINISHED));
    }

    private class CurationSession
    {
        private List<User> selectedUsers;
        // to find source document of the curated document
        // the curationdoc can be retrieved from user (CURATION or current) and projectId
        private String curationTarget;
        private boolean showAll;

        public CurationSession(String aUser)
        {
            curationTarget = aUser;
        }

        public CurationSession(String aCurationTarget, List<User> aSelectedUsers)
        {
            curationTarget = aCurationTarget;
            selectedUsers = new ArrayList<>(aSelectedUsers);
        }

        public List<User> getSelectedUsers()
        {
            return selectedUsers;
        }

        public void setSelectedUsers(Collection<User> aSelectedUsers)
        {
            selectedUsers = new ArrayList<>(aSelectedUsers);
        }

        public String getCurationTarget()
        {
            return curationTarget;
        }

        public void setCurationTarget(String aCurationTarget)
        {
            curationTarget = aCurationTarget;
        }

        public boolean isShowAll()
        {
            return showAll;
        }

        public void setShowAll(boolean aShowAll)
        {
            showAll = aShowAll;
        }
    }
}
