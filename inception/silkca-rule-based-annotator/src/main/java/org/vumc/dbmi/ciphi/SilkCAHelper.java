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
package org.vumc.dbmi.ciphi;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;

import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;

public class SilkCAHelper
{
    public static String getDocumentTitleFromCAS(CAS cas)
    {
        String documentTitle = null;
        DocumentMetaData documentMetaData;
        try {
            documentMetaData = DocumentMetaData.get(cas.getJCas());
            documentTitle = documentMetaData.getDocumentTitle();
        }
        catch (CASException e) {
            e.printStackTrace();
        }
        return documentTitle;
    }
}
