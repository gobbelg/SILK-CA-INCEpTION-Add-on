/*
 * Licensed to the Vanderbilt University Medical Center under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Vanderbilt University Medical Center
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
package org.vumc.dbmi.ciphi.casconverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;

import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.IndexedObject;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.IndexedTree;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.AnnotatedPhrase;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.AnnotatedPhraseBuilder;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.RaptatToken;

public class CASAnnotatedPhraseConverter
{

    public static List<AnnotatedPhrase> convertCASToAnnotatedPhraseList(CAS aCas, String feature,
            String layer, IndexedTree<IndexedObject<RaptatToken>> indexedTree)
    {
        List<AnnotatedPhrase> annotatedPhraseList = new ArrayList<>();
        Type type = CasUtil.getAnnotationType(aCas, layer);
        Feature predictedFeature = type.getFeatureByBaseName(feature);

        Collection<AnnotationFS> annotatedPhraseCollection = CasUtil.select(aCas, type);
        List<RaptatToken> listRaptatTokens = new ArrayList<RaptatToken>();
        for (AnnotationFS annotatedPhraseMember : annotatedPhraseCollection) {
            int beginOffset = annotatedPhraseMember.getBegin();
            int endOffset = annotatedPhraseMember.getEnd();
            Set<IndexedObject<RaptatToken>> setIndexedTokens = indexedTree
                    .getObjectsCoveringRange(beginOffset, endOffset);
            List<IndexedObject<RaptatToken>> listIndexedTokens = new ArrayList<>(setIndexedTokens);
            Collections.sort(listIndexedTokens);
            RaptatToken token;
            for (IndexedObject<RaptatToken> indexedToken : listIndexedTokens) {
                token = indexedToken.getIndexedObject();
                listRaptatTokens.add(token);
            }
            AnnotatedPhrase annotatedPhrase = convertAnnotationFSToAnnotatedPhrase(
                    annotatedPhraseMember, predictedFeature, listRaptatTokens);
            annotatedPhraseList.add(annotatedPhrase);
        }
        return annotatedPhraseList;
    }

    private static AnnotatedPhrase convertAnnotationFSToAnnotatedPhrase(AnnotationFS annotationFS,
            Feature predictedFeature, List<RaptatToken> listRaptatTokens)
    {
        String label = annotationFS.getFeatureValueAsString(predictedFeature);
        AnnotatedPhraseBuilder builder = new AnnotatedPhraseBuilder();
        builder.rawTokensStartOffset(annotationFS.getBegin());
        builder.rawTokenEndOffset(annotationFS.getEnd());
        builder.processedTokensStartOffset(annotationFS.getBegin());
        builder.processedTokensEndOffset(annotationFS.getEnd());
        builder.annotatorName("Inception_Annotation");
        builder.conceptName(label);
        builder.rawTokens(listRaptatTokens);
        builder.processedTokens(listRaptatTokens);
        return builder.build();
    }

}
