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
package de.tudarmstadt.ukp.inception.ui.scheduling;

import static de.tudarmstadt.ukp.inception.websocket.config.WebsocketConfig.WS_ENDPOINT;
import static java.lang.String.format;

import java.util.Map;

import javax.servlet.ServletContext;

import org.apache.wicket.authorization.Action;
import org.apache.wicket.authroles.authorization.strategies.role.annotations.AuthorizeAction;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.Url;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.spring.injection.annot.SpringBean;

import de.tudarmstadt.ukp.inception.scheduling.controller.SchedulerController;
import de.tudarmstadt.ukp.inception.support.svelte.SvelteBehavior;

@AuthorizeAction(action = Action.RENDER, roles = "ROLE_USER")
public class TaskMonitorPanel
    extends WebMarkupContainer
{
    private static final long serialVersionUID = -9006607500867612027L;

    private @SpringBean ServletContext servletContext;

    public TaskMonitorPanel(String aId)
    {
        super(aId);
        setOutputMarkupPlaceholderTag(true);
    }

    @Override
    protected void onConfigure()
    {
        super.onConfigure();

        setDefaultModel(Model.ofMap(Map.of( //
                "wsEndpointUrl", constructEndpointUrl(), //
                "topicChannel", SchedulerController.BASE_TOPIC)));

        add(new SvelteBehavior());
    }

    private String constructEndpointUrl()
    {
        Url endPointUrl = Url.parse(format("%s%s", servletContext.getContextPath(), WS_ENDPOINT));
        endPointUrl.setProtocol("ws");
        String fullUrl = RequestCycle.get().getUrlRenderer().renderFullUrl(endPointUrl);
        return fullUrl;
    }
}
