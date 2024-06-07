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
package org.vumc.dbmi.ciphi.assistedannotationtype.probabilistic;

import static de.tudarmstadt.ukp.inception.recommendation.api.recommender.TrainingCapability.TRAINING_REQUIRED;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vumc.dbmi.ciphi.casconverter.CASConverter;
import org.vumc.dbmi.ciphi.casconverter.CASRaptatDocumentGenerator;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.DataSplitter;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.EvaluationResult;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngine;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationException;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommenderContext;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommenderContext.Key;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.TrainingCapability;
import de.tudarmstadt.ukp.inception.rendering.model.Range;
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.AnnotationGroup;
import src.main.gov.va.vha09.grecc.raptat.gg.textanalysis.RaptatDocument;
import src.main.gov.va.vha09.grecc.raptat.gg.uima.assistedannotation.probabilistic.ProbabilisticModel;
import src.main.gov.va.vha09.grecc.raptat.gg.uima.assistedannotation.probabilistic.ProbabilisticPredictor;
import src.main.gov.va.vha09.grecc.raptat.gg.uima.assistedannotation.probabilistic.ProbabilisticTrainer;
import src.main.gov.va.vha09.grecc.raptat.rn.silkca.datastructures.SimpleAnnotatedPhrase;

/**
 * Recommender class Inception uses when the user annotates. Its primary roles are to create, train,
 * and predict using a model.
 */

public class ProbabilisticRecommender
    extends RecommendationEngine
{
    public static final Key<ProbabilisticModel> KEY_MODEL = new Key<>("model");

    private static final Class<Token> DATAPOINT_UNIT = Token.class;

    private final Logger log = LoggerFactory.getLogger(getClass());

    private ProbabilisticTrainer probabilisticTrainer;
    private ProbabilisticPredictor probabilisticPredictor;

    // TODO:
    // Change to location of application
    private String stringPathToProperties = "";

    public ProbabilisticRecommender(Recommender aRecommender)
    {
        super(aRecommender);

        // We may want to keep a property file within .silkca that stores the path to properties
        // files
        // for bothte probabilisticTrainer and probabilisticPredictor
        this.probabilisticTrainer = new ProbabilisticTrainer(this.featureName,
                stringPathToProperties);
        this.probabilisticPredictor = new ProbabilisticPredictor();
    }

    /**
     * When training should be used. Can be always, optional, or never required
     */
    @Override
    public TrainingCapability getTrainingCapability()
    {
        return TRAINING_REQUIRED;
    }

    /**
     * Train method called by Inception, which in turn calls internal trainModel method. Also
     * extracts SimpleAnnotatedPhrase annotations from CAS objects.
     */
    @Override
    public void train(RecommenderContext aContext, List<CAS> annotatedCasObjects)
        throws RecommendationException
    {

        if (aContext.get(KEY_MODEL).isEmpty()) {
            aContext.put(KEY_MODEL, new ProbabilisticModel());
        }

        try {
            ProbabilisticModel probabilisticModel = aContext.get(KEY_MODEL).get();
            List<CAS> trainingCASes = removePreviousCasObjects(aContext, annotatedCasObjects);
            List<AnnotationGroup> annotationGroups = CASConverter
                    .convertToAnnotationGroups(trainingCASes, featureName, layerName);

            String revisedStringPathToSolution = this.probabilisticTrainer.train(annotationGroups);

            /*
             * Note that all the annotated CAS object should have been used for training at this
             * point, including previous CAS objects and the ones currently used for training. We
             * store these CAS objects so that we can later check on what has already been used for
             * training.
             */
            probabilisticModel.setCasObjectsUsedForTraining(annotatedCasObjects);
            probabilisticModel.setStringPathToSolutionFile(revisedStringPathToSolution);
        }
        catch (IllegalArgumentException e) {
            e.printStackTrace();
            throw new RecommendationException("Illegal argument used for annotation recommender");
        }
    }

    @Override
    public boolean isReadyForPrediction(RecommenderContext aContext)
    {
        return aContext.get(KEY_MODEL).map(Objects::nonNull).orElse(false);
    }

    private List<SimpleAnnotatedPhrase> extractAnnotations(List<CAS> aCasses)
    {
        List<SimpleAnnotatedPhrase> annotations = new ArrayList<>();
        for (CAS cas : aCasses) {
            Type annotationType = CasUtil.getType(cas, layerName);
            Feature predictedFeature = annotationType.getFeatureByBaseName(featureName);
            for (AnnotationFS ann : CasUtil.select(cas, annotationType)) {
                String label = ann.getFeatureValueAsString(predictedFeature);
                if (isNotEmpty(label)) {
                    String documentName = getDocumentTitleFromCAS(cas);
                    annotations.add(new SimpleAnnotatedPhrase(documentName, ann.getCoveredText(),
                            label, ann.getBegin(), ann.getEnd()));
                    System.out.println(documentName);
                }
            }
        }
        return annotations;
    }

    private List<CAS> removePreviousCasObjects(RecommenderContext context, List<CAS> casList)
    {
        List<CAS> resultList = new ArrayList<CAS>(casList);
        Optional<ProbabilisticModel> previousModel = context.get(KEY_MODEL);
        if (previousModel.isPresent()) {
            Iterator<CAS> casIterator = casList.listIterator();
            while (casIterator.hasNext()) {
                CAS cas = casIterator.next();
                if (previousModel.get().casUsedForTraining(cas)) {
                    casIterator.remove();
                }
            }
        }
        return resultList;
    }

    /**
     * Gets predictions from Raptat
     * 
     * @param candidates
     * @param aModel
     * @return
     */
    private List<SimpleAnnotatedPhrase> predict(CAS aCas, ProbabilisticModel aModel)
    {
        RaptatDocument raptatDocument = CASRaptatDocumentGenerator.generateRaptatDocument(aCas);
        return this.probabilisticPredictor.predict(raptatDocument, aModel);
    }

    @Override
    public EvaluationResult evaluate(List<CAS> aCasses, DataSplitter aDataSplitter)
        throws RecommendationException
    {
        List<SimpleAnnotatedPhrase> data = extractAnnotations(aCasses);
        List<SimpleAnnotatedPhrase> trainingData = new ArrayList<>();
        List<SimpleAnnotatedPhrase> testData = new ArrayList<>();

        for (SimpleAnnotatedPhrase ann : data) {
            switch (aDataSplitter.getTargetSet(ann)) {
            case TRAIN:
                trainingData.add(ann);
                break;
            case TEST:
                testData.add(ann);
                break;
            case IGNORE:
                break;
            }
        }

        int trainingSetSize = trainingData.size();
        int testSetSize = testData.size();
        double overallTrainingSize = data.size() - testSetSize;
        double trainRatio = (overallTrainingSize > 0) ? trainingSetSize / overallTrainingSize : 0.0;

        if (trainingData.size() < 1 || testData.size() < 1) {
            log.info("Not enough data to evaluate, skipping!");
            EvaluationResult result = new EvaluationResult(DATAPOINT_UNIT.getSimpleName(),
                    getRecommender().getLayer().getUiName(), trainingSetSize, testSetSize,
                    trainRatio);
            result.setEvaluationSkipped(true);
            return result;
        }

        EvaluationResult result = new EvaluationResult();
        result.setEvaluationSkipped(true);
        return result;
    }

    @Override
    public int estimateSampleCount(List<CAS> aCasses)
    {
        return extractAnnotations(aCasses).size();
    }

    private String getDocumentTitleFromCAS(CAS cas)
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

    /**
     * Predict method used by Inception, which gets predictions from internal predict() method. Also
     * inserts predictions into CAS.
     */
    @Override
    public Range predict(RecommenderContext aContext, CAS aCas, int aBegin, int aEnd)
        throws RecommendationException
    {
        ProbabilisticModel model = aContext.get(KEY_MODEL).orElseThrow(
                () -> new RecommendationException("Key [" + KEY_MODEL + "] not found in context"));

        Type tokenType = CasUtil.getAnnotationType(aCas, DATAPOINT_UNIT);
        Collection<AnnotationFS> candidates = WebAnnoCasUtil.selectOverlapping(aCas, tokenType,
                aBegin, aEnd);
        List<SimpleAnnotatedPhrase> predictions = predict(aCas, model);

        Type predictedType = getPredictedType(aCas);
        Feature scoreFeature = getScoreFeature(aCas);
        Feature scoreExplanationFeature = getScoreExplanationFeature(aCas);
        Feature predictedFeature = getPredictedFeature(aCas);
        Feature isPredictionFeature = getIsPredictionFeature(aCas);

        for (SimpleAnnotatedPhrase ann : predictions) {
            AnnotationFS annotation = aCas.createAnnotation(predictedType, ann.beginOffset(),
                    ann.endOffset());
            annotation.setStringValue(predictedFeature, ann.label());
            annotation.setDoubleValue(scoreFeature, 1);
            annotation.setStringValue(scoreExplanationFeature, "");
            annotation.setBooleanValue(isPredictionFeature, true);
            aCas.addFsToIndexes(annotation);
        }
        return new Range(candidates);
    }

}
