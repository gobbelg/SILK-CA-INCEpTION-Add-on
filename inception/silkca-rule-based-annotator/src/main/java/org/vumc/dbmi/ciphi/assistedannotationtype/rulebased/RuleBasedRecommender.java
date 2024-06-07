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
package org.vumc.dbmi.ciphi.assistedannotationtype.rulebased;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.CasUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.vumc.dbmi.ciphi.casconverter.CASConverter;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
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
import src.main.gov.va.vha09.grecc.raptat.gg.datastructures.annotationcomponents.RaptatToken;
import src.main.gov.va.vha09.grecc.raptat.gg.uima.assistedannotation.rulebased.RuleBasedPiModel;
import src.main.gov.va.vha09.grecc.raptat.gg.uima.assistedannotation.rulebased.RuleBasedPiPredictor;
import src.main.gov.va.vha09.grecc.raptat.rn.silkca.datastructures.SimpleAnnotatedPhrase;

/**
 * Recommender class Inception uses when the user annotates. Its primary roles are to create, train,
 * and predict using a model.
 */

public class RuleBasedRecommender
    extends RecommendationEngine
{
    private static final Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    public static final Key<RuleBasedPiModel> KEY_MODEL = new Key<>("model");

    private static final Class<Token> DATAPOINT_UNIT = Token.class;

    /*
     * The pi dictionary used by this recommender is defined to be the most recent '.txt' file found
     * within the folder USER_HOME_FOLDER\.inception\silkca\pidictionary. Generally the file is
     * moved by the '.bat' file that runs the InceptionSilkCA app. The user should put the file into
     * a directory named "DictionaryDirectory," which should be in the same directory containing
     * both the '.bat' file and the InceptionSilkCA.jar file.
     */
    private static final String STRING_PATH_TO_PI_DICTIONARY;
    static {
        String piDictionaryDirectoryPathString = System.getProperty("user.home") + File.separator
                + ".inception" + File.separator + "silkca" + File.separator + "pidictionary";
        File piDictionaryDirectory = new File(piDictionaryDirectoryPathString);
        File[] files = piDictionaryDirectory.listFiles(new FilenameFilter()
        {
            public boolean accept(File dir, String name)
            {
                return name.toLowerCase().endsWith(".txt");
            }
        });

        if (files != null && files.length > 0) {
            File mostRecentFile = files[0];

            // Find the most recently modified file
            for (File file : files) {
                if (file.lastModified() > mostRecentFile.lastModified()) {
                    mostRecentFile = file;
                }
            }
            STRING_PATH_TO_PI_DICTIONARY = mostRecentFile.getAbsolutePath();
        }
        else {
            System.out.println("No .txt files found in the directory.");
            STRING_PATH_TO_PI_DICTIONARY = "";
        }
    }

    private final Logger log = LoggerFactory.getLogger(getClass());

    private RuleBasedPiPredictor predictor;

    private RuleBasedPiModel model = null;

    public RuleBasedRecommender(Recommender aRecommender)
    {
        super(aRecommender);

        LOG.info("Loading Pi Dictionary at: " + STRING_PATH_TO_PI_DICTIONARY);
        this.model = new RuleBasedPiModel(STRING_PATH_TO_PI_DICTIONARY);
        LOG.info("Pi Dictionary for Rule-Based Recommender Loaded");

        this.predictor = new RuleBasedPiPredictor();
    }

    /**
     * When training should be used. Can be always, optional, or never required
     */
    @Override
    public TrainingCapability getTrainingCapability()
    {
        return TrainingCapability.TRAINING_NOT_SUPPORTED;
    }

    /**
     * Train method called by Inception, which in turn calls internal trainModel method. Also
     * extracts SimpleAnnotatedPhrase annotations from CAS objects.
     */
    @Override
    public void train(RecommenderContext aContext, List<CAS> annotatedCasObjects)
        throws RecommendationException
    {
        System.out.println("Training not supported for rule-based system");
    }

    @Override
    public boolean isReadyForPrediction(RecommenderContext aContext)
    {
        return this.model != null;
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

    /**
     * Gets predictions from Raptat
     * 
     * @param candidates
     * @param aModel
     * @return
     */
    private List<SimpleAnnotatedPhrase> predict(CAS aCas, RuleBasedPiModel aModel, Type tokenType,
            Type sentenceType)
    {
        String documentTitle = getDocumentTitleFromCAS(aCas);
        List<List<RaptatToken>> sentencesAsTokenLists = CASConverter.getSentencesAsTokenLists(aCas,
                tokenType, sentenceType);
        List<SimpleAnnotatedPhrase> predictedAnnotations = this.predictor
                .predict(sentencesAsTokenLists, aModel, tokenType, sentenceType, documentTitle);
        return predictedAnnotations;
    }

    @Override
    public EvaluationResult evaluate(List<CAS> aCasses, DataSplitter aDataSplitter)
        throws RecommendationException
    {
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
        Type tokenType = CasUtil.getAnnotationType(aCas, DATAPOINT_UNIT);
        Type sentenceType = CasUtil.getAnnotationType(aCas, Sentence.class);
        List<SimpleAnnotatedPhrase> predictions = predict(aCas, model, tokenType, sentenceType);

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

        Collection<AnnotationFS> candidates = WebAnnoCasUtil.selectOverlapping(aCas, tokenType, aBegin, aEnd);
        return Range.rangeCoveringAnnotations(candidates);
    }

}
