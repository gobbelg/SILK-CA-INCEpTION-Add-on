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
package de.tudarmstadt.ukp.inception.annotation.layer.span;

import org.apache.uima.cas.text.AnnotationFS;

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.clarin.webanno.model.SourceDocument;
import de.tudarmstadt.ukp.inception.annotation.events.AnnotationDeletedEvent;

public class SpanDeletedEvent
    extends SpanEvent
    implements AnnotationDeletedEvent
{
    private static final long serialVersionUID = 5206262614840209407L;

    public SpanDeletedEvent(Object aSource, SourceDocument aDocument, String aDocumentOwner,
            AnnotationLayer aLayer, AnnotationFS aAnnotation)
    {
        super(aSource, aDocument, aDocumentOwner, aLayer, aAnnotation);
        
        /*
         * Logging added by Glenn Gobbel on 6/10/24
         */
        String docName = aDocument == null ? "Null" : aDocument.getName();
        String coveredText = aAnnotation == null ? "Null" : aAnnotation.getCoveredText();
        String layerName = aLayer == null ? "Null" : aLayer.getName();
        int begin = aAnnotation == null ? -1 : aAnnotation.getBegin();
        int end = aAnnotation == null ? -1 : aAnnotation.getEnd();

        LOG.info("SILKCA LOG - Annotation deleted - DOCUMENT:{}\tUSER:{}\tTEXT:{}\tLAYER:{}\tBEGIN:{}\tEND:{}",
                docName, aDocumentOwner, coveredText, layerName, begin, end);
        // End addition
    }
}
