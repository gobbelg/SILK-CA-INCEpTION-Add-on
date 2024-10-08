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
package de.tudarmstadt.ukp.inception.io.bioc.xml;

import static de.tudarmstadt.ukp.inception.io.bioc.BioCComponent.E_LOCATION;
import static de.tudarmstadt.ukp.inception.io.bioc.BioCComponent.E_TEXT;

import java.util.Optional;

import org.dkpro.core.api.xml.type.XmlElement;

public class BioCXmlUtils
{
    public static Optional<XmlElement> getChildTextElement(XmlElement aContainer)
    {
        return aContainer.getChildren().select(XmlElement.class) //
                .filter(e -> E_TEXT.equals(e.getQName())) //
                .findFirst();
    }

    public static Optional<XmlElement> getChildLocationElement(XmlElement aContainer)
    {
        return aContainer.getChildren().select(XmlElement.class) //
                .filter(e -> E_LOCATION.equals(e.getQName())) //
                .findFirst();
    }
}
