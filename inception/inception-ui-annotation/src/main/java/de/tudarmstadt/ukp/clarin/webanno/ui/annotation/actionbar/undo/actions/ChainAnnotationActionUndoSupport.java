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
package de.tudarmstadt.ukp.clarin.webanno.ui.annotation.actionbar.undo.actions;

import org.springframework.context.ApplicationEvent;

import de.tudarmstadt.ukp.clarin.webanno.ui.annotation.config.AnnotationUIAutoConfiguration;
import de.tudarmstadt.ukp.inception.annotation.layer.chain.ChainLinkCreatedEvent;
import de.tudarmstadt.ukp.inception.annotation.layer.chain.ChainSpanCreatedEvent;
import de.tudarmstadt.ukp.inception.annotation.layer.chain.ChainSpanEvent;

/**
 * <p>
 * This class is exposed as a Spring Component via
 * {@link AnnotationUIAutoConfiguration#chainAnnotationActionUndoSupport}.
 * </p>
 */
public class ChainAnnotationActionUndoSupport
    implements UndoableAnnotationActionSupport
{
    @Override
    public boolean accepts(ApplicationEvent aContext)
    {
        return aContext instanceof ChainSpanEvent;
    }

    @Override
    public UndoableAnnotationAction actionForEvent(long aRequestId, ApplicationEvent aEvent)
    {
        if (aEvent instanceof ChainSpanCreatedEvent) {
            return new CreateChainSpanAnnotationAction(aRequestId, (ChainSpanCreatedEvent) aEvent);
        }

        if (aEvent instanceof ChainLinkCreatedEvent) {
            return new CreateChainLinkAnnotationAction(aRequestId, (ChainLinkCreatedEvent) aEvent);
        }

        throw new IllegalArgumentException("Not an undoable action: [" + aEvent.getClass() + "]");
    }
}
