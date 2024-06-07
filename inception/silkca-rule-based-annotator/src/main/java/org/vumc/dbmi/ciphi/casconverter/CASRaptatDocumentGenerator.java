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

import java.util.List;
import java.util.Optional;

import org.apache.uima.cas.CAS;
import org.vumc.dbmi.ciphi.SilkCAHelper;

import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.IndexedObject;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.IndexedTree;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.AnnotatedPhrase;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.RaptatToken;
import src.main.gov.va.vha09.grecc.raptat.gg.textanalysis.RaptatDocument;
import src.main.gov.va.vha09.grecc.raptat.rn.textanalysis.RaptatDocumentBuilder;

public class CASRaptatDocumentGenerator
{

        public static RaptatDocument generateRaptatDocument(CAS documentCas,
                IndexedTree<IndexedObject<RaptatToken>> raptatTokens)
        {
            RaptatDocumentBuilder raptatDocumentBuilder = new RaptatDocumentBuilder();
            List<AnnotatedPhrase> convertedSentenceList = CASSentenceConverter
                    .convertCASToSentenceList(documentCas, raptatTokens);
            String documentText = documentCas.getDocumentText();
            String documentTitle = SilkCAHelper.getDocumentTitleFromCAS(documentCas);
            return raptatDocumentBuilder.activeSentences(convertedSentenceList)
                    .baseSentences(convertedSentenceList).processedTokens(raptatTokens)
                    .rawTokens(raptatTokens).rawInputText(documentText).textSource(documentTitle)
                    .textSourcePath(Optional.empty()).build();

        }
        
        public static RaptatDocument generateRaptatDocument(CAS documentCas)
        {
            List<RaptatToken> tokenList = CASTokenConverter
                    .convertCASToRaptatTokenList(documentCas);
            IndexedTree<IndexedObject<RaptatToken>> indexedTree = IndexedTree
                    .generateIndexedTree(tokenList);
            return generateRaptatDocument(documentCas, indexedTree);
        }

}
