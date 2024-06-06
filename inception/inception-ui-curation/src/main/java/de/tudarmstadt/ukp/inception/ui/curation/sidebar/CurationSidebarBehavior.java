/*
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

import static de.tudarmstadt.ukp.clarin.webanno.model.PermissionLevel.CURATOR;
import static de.tudarmstadt.ukp.clarin.webanno.ui.core.page.ProjectPageBase.setProjectPageParameter;
import static de.tudarmstadt.ukp.inception.support.WebAnnoConst.CURATION_USER;
import static java.lang.invoke.MethodHandles.lookup;
import static java.util.Arrays.asList;
import static org.slf4j.LoggerFactory.getLogger;

import org.apache.wicket.Component;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.behavior.Behavior;
import org.apache.wicket.event.IEvent;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.apache.wicket.spring.injection.annot.SpringBean;
import org.slf4j.Logger;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.page.AnnotationPageBase;
import de.tudarmstadt.ukp.clarin.webanno.model.Project;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.AnnotationPage;
import de.tudarmstadt.ukp.inception.annotation.events.PreparingToOpenDocumentEvent;
import de.tudarmstadt.ukp.inception.project.api.ProjectService;

public class CurationSidebarBehavior
    extends Behavior
{
    private static final long serialVersionUID = -6224298395673360592L;

    private static final Logger LOG = getLogger(lookup().lookupClass());

    private static final String STAY = "stay";
    private static final String OFF = "off";
    private static final String ON = "on";

    private static final String PARAM_CURATION_SESSION = "curationSession";
    private static final String PARAM_CURATION_TARGET_OWN = "curationTargetOwn";

    private @SpringBean CurationSidebarService curationSidebarService;
    private @SpringBean UserDao userService;
    private @SpringBean ProjectService projectService;

    @Override
    public void onEvent(Component aComponent, IEvent<?> aEvent)
    {
        if (!(aEvent.getPayload() instanceof PreparingToOpenDocumentEvent)) {
            if (aEvent.getPayload() != null) {
                LOG.trace("Event not relevant to curation sidebar: {} / {}", aEvent.getClass(),
                        aEvent.getPayload().getClass());
            }
            else {
                LOG.trace("Event not relevant to curation sidebar: {}", aEvent.getClass());
            }
            return;
        }

        var event = (PreparingToOpenDocumentEvent) aEvent.getPayload();

        var page = event.getSource();

        if (!(page instanceof AnnotationPage)) {
            // Only applies to the AnnotationPage - not to the CurationPage!
            LOG.trace(
                    "Curation sidebar is not deployed on AnnotationPage but rather [{}] - ignoring event [{}]",
                    page.getClass(), event.getClass());
            return;
        }

        var params = page.getPageParameters();

        var sessionOwner = userService.getCurrentUsername();
        var doc = event.getDocument();
        var project = doc.getProject();
        var dataOwner = event.getDocumentOwner();

        if (!projectService.hasRole(sessionOwner, project, CURATOR)) {
            LOG.trace(
                    "Session owner [{}] is not a curator and can therefore not manage curation mode using URL parameters",
                    sessionOwner);
            return;
        }

        LOG.trace("Curation sidebar reacting to [{}]@{} being opened by [{}]", dataOwner, doc,
                sessionOwner);

        handleSessionActivation(page, params, doc, sessionOwner);

        ensureDataOwnerMatchesCurationTarget(page, project, sessionOwner, dataOwner);
    }

    private void ensureDataOwnerMatchesCurationTarget(AnnotationPageBase aPage, Project aProject,
            String aSessionOwner, String aDataOwner)
    {
        if (!isSessionActive(aProject)) {
            LOG.trace(
                    "No curation session active - no need to adjust data owner to curation target");
            return;
        }

        if (!isViewingPotentialCurationTarget(aDataOwner)) {
            return;
        }

        // If the curation target user is different from the data owner set in the annotation
        // state, then we need to update the state and reload.
        var curationTarget = curationSidebarService.getCurationTargetUser(aSessionOwner,
                aProject.getId());

        if (!aDataOwner.equals(curationTarget.getUsername())) {
            LOG.trace("Data owner [{}] should match curation target {} - changing to {}",
                    curationTarget, aDataOwner, curationTarget);
            aPage.getModelObject().setUser(curationTarget);
        }
        else {
            LOG.trace("Data owner [{}] alredy matches curation target {}", curationTarget,
                    aDataOwner);
        }
    }

    private void handleSessionActivation(AnnotationPageBase aPage, PageParameters aParams,
            SourceDocument aDoc, String aSessionOwner)
    {
        var project = aDoc.getProject();

        var curationSessionParameterValue = aParams.get(PARAM_CURATION_SESSION);
        if (curationSessionParameterValue.isEmpty()) {
            return;
        }

        switch (curationSessionParameterValue.toString(STAY)) {
        case ON:
            LOG.trace("Checking if to start curation session");
            // Start a new session or switch to new curation target
            var curationTargetOwnParameterValue = aParams.get(PARAM_CURATION_TARGET_OWN);
            if (!isSessionActive(project) || !curationTargetOwnParameterValue.isEmpty()) {
                curationSidebarService.startSession(aSessionOwner, project,
                        curationTargetOwnParameterValue.toBoolean(false));
            }
            break;
        case OFF:
            LOG.trace("Checking if to stop curation session");
            if (isSessionActive(project)) {
                curationSidebarService.closeSession(aSessionOwner, project.getId());
            }
            break;
        default:
            // Ignore
            LOG.trace("No change in curation session state requested [{}]",
                    curationSessionParameterValue);
        }

        LOG.trace("Removing session control parameters and reloading (redirect)");
        aParams.remove(PARAM_CURATION_TARGET_OWN);
        aParams.remove(PARAM_CURATION_SESSION);
        setProjectPageParameter(aParams, project);
        aParams.set(AnnotationPage.PAGE_PARAM_DOCUMENT, aDoc.getId());
        // We need to do a redirect here to discard the arguments from the URL.
        // This also discards the page state.
        throw new RestartResponseException(aPage.getClass(), aParams);
    }

    private boolean isViewingPotentialCurationTarget(String aDataOwner)
    {
        // Curation sidebar is not allowed when viewing another users annotations
        var sessionOwner = userService.getCurrentUsername();
        var candidates = asList(CURATION_USER, sessionOwner);
        var result = candidates.contains(aDataOwner);
        if (!result) {
            LOG.trace("Data ownwer [{}] is not in curation candidates {}", aDataOwner, candidates);
        }
        return result;
    }

    private boolean isSessionActive(Project aProject)
    {
        var sessionOwner = userService.getCurrentUsername();
        return curationSidebarService.existsSession(sessionOwner, aProject.getId());
    }
}
