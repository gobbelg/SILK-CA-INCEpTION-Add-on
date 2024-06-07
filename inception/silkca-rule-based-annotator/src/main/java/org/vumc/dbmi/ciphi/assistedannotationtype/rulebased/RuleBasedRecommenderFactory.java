/*
RuleB * Licensed to the Vanderbilt University Medical Center under one
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
package org.vumc.dbmi.ciphi.assistedannotationtype.rulebased;

import org.springframework.stereotype.Component;

import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationLayer;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngine;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngineFactoryImplBase;

/**
 * Factory class that Inception uses to create the SilkCARuleBasedAnnotator
 */

@Component
public class RuleBasedRecommenderFactory
    extends RecommendationEngineFactoryImplBase<Void>
{
    public static final String ID = "org.vumc.dbmi.ciphi.RuleBasedPiRecommender";

    @Override
    public String getId()
    {
        return ID;
    }

    @Override
    public RecommendationEngine build(Recommender aRecommender)
    {
        return new RuleBasedRecommender(aRecommender);
    }

    @Override
    public String getName()
    {
        return "RuleBasedPiRecommender";
    }

    /**
     * determine if the layer the administrator wants users to annotate is acceptable to the
     * SilkCARuleBasedAnnotator. Unless the Layer or Feature is null, this function will return
     * true.
     */
    @Override
    public boolean accepts(AnnotationLayer aLayer, AnnotationFeature aFeature)
    {
        if (aLayer == null || aFeature == null) {
            return false;
        }

        return true;
    }
}
