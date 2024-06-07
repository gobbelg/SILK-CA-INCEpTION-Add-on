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

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.IndexedObject;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.IndexedTree;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.AnnotatedPhrase;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.AnnotatedPhraseBuilder;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.RaptatToken;

public class CASSentenceConverter
{
    public static List<AnnotatedPhrase> convertCASToSentenceList(CAS aCas,
            IndexedTree<IndexedObject<RaptatToken>> indexedTree)
    {
        List<AnnotatedPhrase> annotatedPhraseList = new ArrayList<>();
        Type type = CasUtil.getAnnotationType(aCas, Sentence.class);

        Collection<AnnotationFS> annotatedPhraseCollection = CasUtil.select(aCas, type);
        for (AnnotationFS annotatedPhraseMember : annotatedPhraseCollection) {
            int beginOffset = annotatedPhraseMember.getBegin();
            int endOffset = annotatedPhraseMember.getEnd();
            List<RaptatToken> annotatedPhraseTokens = RaptatToken.getTokensWithinRange(indexedTree,
                    beginOffset, endOffset);
            AnnotatedPhrase annotatedPhrase = convertAnnotationFSToSentence(annotatedPhraseMember,
                    annotatedPhraseTokens);
            annotatedPhraseList.add(annotatedPhrase);
        }
        return annotatedPhraseList;
    }

    /**
     * Takes asentence provided in the form of a UIMA AnnotationFS and the type representing tokens
     * within that sentence and returns a sorted list of RaptatToken objects that would represent
     * that sentence in Raptat form.
     * 
     * @param tokenType
     * @param sentence
     * @param documentCas
     * @return
     */
    public static List<RaptatToken> annotationFsSentenceToRaptatTokenList(Type tokenType,
            AnnotationFS sentence, CAS documentCas)
    {
        List<RaptatToken> raptatTokenList = new ArrayList<>();
        List<AnnotationFS> tokens = CasUtil.selectCovered(documentCas, tokenType,
                sentence.getBegin(), sentence.getEnd());
        for (AnnotationFS token : tokens) {
            RaptatToken raptatToken = CASTokenConverter.annotationFSToRaptatToken(token);
            raptatTokenList.add(raptatToken);
        }
        Collections.sort(raptatTokenList);

        return raptatTokenList;
    }

    private static AnnotatedPhrase convertAnnotationFSToSentence(AnnotationFS annotationFS,
            List<RaptatToken> listRaptatTokens)
    {
        AnnotatedPhraseBuilder builder = new AnnotatedPhraseBuilder();
        builder.rawTokensStartOffset(annotationFS.getBegin());
        builder.rawTokenEndOffset(annotationFS.getEnd());
        builder.processedTokensStartOffset(annotationFS.getBegin());
        builder.processedTokensEndOffset(annotationFS.getEnd());
        builder.annotatorName("Inception_Annotation");
        builder.rawTokens(listRaptatTokens);
        builder.processedTokens(listRaptatTokens);
        return builder.build();
    }
}
