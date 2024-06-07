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

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.RaptatToken;

public class CASTokenConverter
{
    public static List<RaptatToken> convertCASToRaptatTokenList(CAS aCas) 
    {
        List<RaptatToken> raptatTokenList = new ArrayList<>();

        Type type = CasUtil.getAnnotationType(aCas, Token.class);
        Collection<AnnotationFS> tokens = CasUtil.select(aCas, type);
        for (AnnotationFS token: tokens) {
            RaptatToken raptatToken = annotationFSToRaptatToken(token);
            raptatTokenList.add(raptatToken);
        }
        return raptatTokenList;
    }


    
    
    public static RaptatToken annotationFSToRaptatToken(AnnotationFS token) {
        int beginOffset = token.getBegin();
        int endOffset = token.getEnd();
        String text = token.getCoveredText();
        RaptatToken raptatToken = new RaptatToken(text, beginOffset, endOffset);
        raptatToken.setTokenStringUnprocessed(text);
        raptatToken.setTokenStringPreprocessed(text.toLowerCase());
        return raptatToken;
    }

}
