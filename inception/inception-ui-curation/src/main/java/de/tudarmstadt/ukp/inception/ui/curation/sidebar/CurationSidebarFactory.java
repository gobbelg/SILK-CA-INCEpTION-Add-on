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
import static java.lang.invoke.MethodHandles.lookup;
import static org.slf4j.LoggerFactory.getLogger;

import org.apache.wicket.Component;
import org.apache.wicket.model.IModel;
import org.slf4j.Logger;

import de.tudarmstadt.ukp.clarin.webanno.api.casstorage.CasProvider;
import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.AnnotationPage;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.sidebar.AnnotationSidebarFactory_ImplBase;
import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.sidebar.AnnotationSidebar_ImplBase;
import de.tudarmstadt.ukp.inception.editor.action.AnnotationActionHandler;
import de.tudarmstadt.ukp.inception.project.api.ProjectService;
import de.tudarmstadt.ukp.inception.rendering.editorstate.AnnotatorState;
import de.tudarmstadt.ukp.inception.ui.curation.sidebar.config.CurationSidebarAutoConfiguration;

/**
 * <p>
 * This class is exposed as a Spring Component via
 * {@link CurationSidebarAutoConfiguration#curationSidebarFactory}.
 * </p>
 */
public class CurationSidebarFactory
    extends AnnotationSidebarFactory_ImplBase
{
    private static final Logger LOG = getLogger(lookup().lookupClass());

    private final ProjectService projectService;
    private final UserDao userService;

    public CurationSidebarFactory(ProjectService aProjectService, UserDao aUserService)
    {
        projectService = aProjectService;
        userService = aUserService;
    }

    @Override
    public String getDisplayName()
    {
        return "Curation";
    }

    @Override
    public String getDescription()
    {
        return "Allows curation via the annotation page. Only available to curators.";
    }

    @Override
    public Component createIcon(String aId, IModel<AnnotatorState> aState)
    {
        return new CurationSidebarIcon(aId, aState);
    }

    @Override
    public AnnotationSidebar_ImplBase create(String aId, IModel<AnnotatorState> aModel,
            AnnotationActionHandler aActionHandler, CasProvider aCasProvider,
            AnnotationPage aAnnotationPage)
    {
        return new CurationSidebar(aId, aModel, aActionHandler, aCasProvider, aAnnotationPage);
    }

    @Override
    public boolean applies(AnnotatorState aState)
    {
        String currentUser = userService.getCurrentUsername();
        boolean isCurator = projectService.hasRole(aState.getUser(), aState.getProject(), CURATOR);
        return isCurator && aState.getUser().getUsername().equals(currentUser);
    }
}
