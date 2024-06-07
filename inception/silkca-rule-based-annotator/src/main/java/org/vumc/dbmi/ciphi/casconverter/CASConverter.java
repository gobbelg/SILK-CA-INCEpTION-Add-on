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
import java.util.List;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;

import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.IndexedObject;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.IndexedTree;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.AnnotatedPhrase;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.AnnotationGroup;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.RaptatToken;
import src.main.gov.va.vha09.grecc.raptat.gg.textanalysis.RaptatDocument;

public class CASConverter
{   
    
    public static List<AnnotationGroup> convertToAnnotationGroups(List<CAS> documentCasObjects,
            String feature, String layer)
    {
        List<AnnotationGroup> resultList = new ArrayList<>();
        for (CAS documentCas : documentCasObjects) {
            List<RaptatToken> tokenList = CASTokenConverter
                    .convertCASToRaptatTokenList(documentCas);
            IndexedTree<IndexedObject<RaptatToken>> indexedTree = IndexedTree
                    .generateIndexedTree(tokenList);

            List<AnnotatedPhrase> annotatedPhrases = CASAnnotatedPhraseConverter
                    .convertCASToAnnotatedPhraseList(documentCas, feature, layer, indexedTree);

            RaptatDocument raptatDocument = CASRaptatDocumentGenerator
                    .generateRaptatDocument(documentCas, indexedTree);

            AnnotationGroup annotationGroup = new AnnotationGroup(raptatDocument, annotatedPhrases);
            resultList.add(annotationGroup);
        }
        return resultList;
    }
    
    
    public static List<List<RaptatToken>> getSentencesAsTokenLists(CAS aCas, Type tokenType,
            Type sentenceType)
    {
        List<List<RaptatToken>> tokenLists = new ArrayList<>();

        Collection<AnnotationFS> casSentences = CasUtil.select(aCas, sentenceType);
        for (AnnotationFS casSentence : casSentences) {
            List<RaptatToken> sentenceTokens = CASSentenceConverter
                    .annotationFsSentenceToRaptatTokenList(tokenType, casSentence, aCas);
            tokenLists.add(sentenceTokens);
        }

        return tokenLists;
    }

}
