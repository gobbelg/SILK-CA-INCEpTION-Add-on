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
package de.tudarmstadt.ukp.inception.recommendation.imls.opennlp.ner;

import static de.tudarmstadt.ukp.clarin.webanno.api.annotation.util.WebAnnoCasUtil.selectOverlapping;
import static de.tudarmstadt.ukp.inception.recommendation.api.evaluation.EvaluationResult.toEvaluationResult;
import static de.tudarmstadt.ukp.inception.rendering.model.Range.rangeCoveringAnnotations;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.uima.fit.util.CasUtil.getType;
import static org.apache.uima.fit.util.CasUtil.selectCovered;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.jcas.tcas.Annotation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.DataSplitter;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.EvaluationResult;
import de.tudarmstadt.ukp.inception.recommendation.api.evaluation.LabelPair;
import de.tudarmstadt.ukp.inception.recommendation.api.model.Recommender;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngine;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationException;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommenderContext;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommenderContext.Key;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.TrainingCapability;
import de.tudarmstadt.ukp.inception.rendering.model.Range;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import opennlp.tools.ml.BeamSearch;
import opennlp.tools.namefind.NameFinderME;
import opennlp.tools.namefind.NameSample;
import opennlp.tools.namefind.TokenNameFinderFactory;
import opennlp.tools.namefind.TokenNameFinderModel;
import opennlp.tools.util.Span;
import opennlp.tools.util.TrainingParameters;

public class OpenNlpNerRecommender
    extends RecommendationEngine
{
    public static final Key<TokenNameFinderModel> KEY_MODEL = new Key<>("opennlp_ner_model");
    private static final Logger LOG = LoggerFactory.getLogger(OpenNlpNerRecommender.class);

    private static final String NO_NE_TAG = "O";

    private static final Class<Sentence> SAMPLE_UNIT = Sentence.class;
    private static final Class<Token> DATAPOINT_UNIT = Token.class;

    private static final int MIN_TRAINING_SET_SIZE = 2;
    private static final int MIN_TEST_SET_SIZE = 2;

    private final OpenNlpNerRecommenderTraits traits;

    public OpenNlpNerRecommender(Recommender aRecommender, OpenNlpNerRecommenderTraits aTraits)
    {
        super(aRecommender);

        traits = aTraits;
    }

    @Override
    public boolean isReadyForPrediction(RecommenderContext aContext)
    {
        return aContext.get(KEY_MODEL).map(Objects::nonNull).orElse(false);
    }

    @Override
    public void train(RecommenderContext aContext, List<CAS> aCasses) throws RecommendationException
    {
        var nameSamples = extractNameSamples(aCasses);

        if (nameSamples.size() < 2) {
            aContext.warn("Not enough training data: [%d] items", nameSamples.size());
            return;
        }

        // The beam size controls how many results are returned at most. But even if the user
        // requests only few results, we always use at least the default bean size recommended by
        // OpenNLP
        int beamSize = Math.max(maxRecommendations, NameFinderME.DEFAULT_BEAM_SIZE);

        var params = traits.getParameters();
        params.put(BeamSearch.BEAM_SIZE_PARAMETER, Integer.toString(beamSize));

        var model = train(nameSamples, params);

        aContext.put(KEY_MODEL, model);
    }

    @Override
    public TrainingCapability getTrainingCapability()
    {
        return TrainingCapability.TRAINING_REQUIRED;
    }

    @Override
    public Range predict(RecommenderContext aContext, CAS aCas, int aBegin, int aEnd)
        throws RecommendationException
    {
        var model = aContext.get(KEY_MODEL).orElseThrow(
                () -> new RecommendationException("Key [" + KEY_MODEL + "] not found in context"));

        var finder = new NameFinderME(model);

        var sampleUnitType = getType(aCas, SAMPLE_UNIT);
        var tokenType = getType(aCas, Token.class);
        var predictedType = getPredictedType(aCas);

        var predictedFeature = getPredictedFeature(aCas);
        var isPredictionFeature = getIsPredictionFeature(aCas);
        var scoreFeature = getScoreFeature(aCas);

        var units = selectOverlapping(aCas, sampleUnitType, aBegin, aEnd);
        var predictionCount = 0;

        for (var unit : units) {
            if (predictionCount >= traits.getPredictionLimit()) {
                break;
            }
            predictionCount++;

            var tokenAnnotations = selectCovered(tokenType, unit);
            var tokens = tokenAnnotations.stream() //
                    .map(AnnotationFS::getCoveredText) //
                    .toArray(String[]::new);

            for (var prediction : finder.find(tokens)) {
                var label = prediction.getType();
                if (NameSample.DEFAULT_TYPE.equals(label) || BLANK_LABEL.equals(label)) {
                    label = null;
                }

                int begin = tokenAnnotations.get(prediction.getStart()).getBegin();
                int end = tokenAnnotations.get(prediction.getEnd() - 1).getEnd();
                var annotation = aCas.createAnnotation(predictedType, begin, end);

                annotation.setStringValue(predictedFeature, label);
                if (scoreFeature != null) {
                    annotation.setDoubleValue(scoreFeature, prediction.getProb());
                }
                if (isPredictionFeature != null) {
                    annotation.setBooleanValue(isPredictionFeature, true);
                }

                aCas.addFsToIndexes(annotation);
            }
        }

        return rangeCoveringAnnotations(units);
    }

    @Override
    public int estimateSampleCount(List<CAS> aCasses)
    {
        return extractNameSamples(aCasses).size();
    }

    @Override
    public EvaluationResult evaluate(List<CAS> aCasses, DataSplitter aDataSplitter)
        throws RecommendationException
    {
        var data = extractNameSamples(aCasses);
        var trainingSet = new ArrayList<NameSample>();
        var testSet = new ArrayList<NameSample>();

        for (var nameSample : data) {
            switch (aDataSplitter.getTargetSet(nameSample)) {
            case TRAIN:
                trainingSet.add(nameSample);
                break;
            case TEST:
                testSet.add(nameSample);
                break;
            default:
                // Do nothing
                break;
            }
        }

        var testSetSize = testSet.size();
        var trainingSetSize = trainingSet.size();
        var overallTrainingSize = data.size() - testSetSize;
        var trainRatio = (overallTrainingSize > 0) ? trainingSetSize / overallTrainingSize : 0.0;

        if (trainingSetSize < MIN_TRAINING_SET_SIZE || testSetSize < MIN_TEST_SET_SIZE) {
            String msg = String.format(
                    "Not enough evaluation data: training set size [%d] (min. %d), test set size [%d] (min. %d) of total [%d] (min. %d)",
                    trainingSetSize, MIN_TRAINING_SET_SIZE, testSetSize, MIN_TEST_SET_SIZE,
                    data.size(), (MIN_TRAINING_SET_SIZE + MIN_TEST_SET_SIZE));
            LOG.info(msg);

            var result = new EvaluationResult(DATAPOINT_UNIT.getSimpleName(),
                    SAMPLE_UNIT.getSimpleName(), trainingSetSize, testSetSize, trainRatio);
            result.setEvaluationSkipped(true);
            result.setErrorMsg(msg);
            return result;
        }

        LOG.info("Training on [{}] samples, predicting on [{}] of total [{}]", trainingSet.size(),
                testSet.size(), data.size());

        // Train model
        var model = train(trainingSet, traits.getParameters());
        var nameFinder = new NameFinderME(model);

        // Evaluate
        var labelPairs = new ArrayList<LabelPair>();
        for (var sample : testSet) {
            // During evaluation, we sample data across documents and shuffle them into training and
            // tests sets. Thus, we consider every sample as coming from a unique document and
            // always clear the adaptive data between samples. clear adaptive data from feature
            // generators if necessary
            nameFinder.clearAdaptiveData();

            // Span contains one NE, Array of them all in one sentence
            var sampleTokens = sample.getSentence();
            var predictedNames = nameFinder.find(sampleTokens);
            var goldNames = sample.getNames();

            labelPairs.addAll(determineLabelsForASentence(sampleTokens, predictedNames, goldNames));
        }

        return labelPairs.stream().collect(toEvaluationResult(DATAPOINT_UNIT.getSimpleName(),
                SAMPLE_UNIT.getSimpleName(), trainingSetSize, testSetSize, trainRatio, NO_NE_TAG));
    }

    /**
     * Extract AnnotatedTokenPairs with info on predicted and gold label for each token of the given
     * sentence.
     */
    private List<LabelPair> determineLabelsForASentence(String[] sentence, Span[] predictedNames,
            Span[] goldNames)
    {
        int predictedNameIdx = 0;
        int goldNameIdx = 0;

        var labelPairs = new ArrayList<LabelPair>();
        // Spans store which tokens are part of it as [begin,end).
        // Tokens are counted 0 to length of sentence.
        // Therefore go through all tokens, determine which span they are part of
        // for predictions and gold ones. Assign label accordingly to the annotated-token.
        for (int i = 0; i < sentence.length; i++) {

            var predictedLabel = NO_NE_TAG;
            if (predictedNameIdx < predictedNames.length) {
                var predictedName = predictedNames[predictedNameIdx];
                predictedLabel = determineLabel(predictedName, i);

                if (i > predictedName.getEnd()) {
                    predictedNameIdx++;
                }
            }

            var goldLabel = NO_NE_TAG;
            if (goldNameIdx < goldNames.length) {
                Span goldName = goldNames[goldNameIdx];
                goldLabel = determineLabel(goldName, i);
                if (i > goldName.getEnd()) {
                    goldNameIdx++;
                }
            }

            labelPairs.add(new LabelPair(goldLabel, predictedLabel));

        }
        return labelPairs;
    }

    /**
     * Check that token index is part of the given span and return the span's label or no-label
     * (token is outside span).
     */
    private String determineLabel(Span aName, int aTokenIdx)
    {
        var label = NO_NE_TAG;

        if (aName.getStart() <= aTokenIdx && aName.getEnd() > aTokenIdx) {
            label = aName.getType();
        }

        return label;
    }

    private List<NameSample> extractNameSamples(List<CAS> aCasses)
    {
        var nameSamples = new ArrayList<NameSample>();

        nextCas: for (var cas : aCasses) {
            var sampleUnitType = getType(cas, SAMPLE_UNIT);
            var tokenType = getType(cas, Token.class);

            var firstSampleInCas = true;
            for (var sampleUnit : cas.<Annotation> select(sampleUnitType)) {
                if (nameSamples.size() >= traits.getTrainingSetSizeLimit()) {
                    break nextCas;
                }

                if (isBlank(sampleUnit.getCoveredText())) {
                    continue;
                }

                var tokens = cas.<Annotation> select(tokenType).coveredBy(sampleUnit).asList();
                var tokenTexts = tokens.stream().map(AnnotationFS::getCoveredText)
                        .toArray(String[]::new);
                var annotatedSpans = extractAnnotatedSpans(cas, sampleUnit, tokens);
                if (annotatedSpans.length == 0) {
                    continue;
                }

                var nameSample = new NameSample(tokenTexts, annotatedSpans, firstSampleInCas);
                nameSamples.add(nameSample);
                firstSampleInCas = false;
            }
        }

        return nameSamples;
    }

    private Span[] extractAnnotatedSpans(CAS aCas, AnnotationFS aSampleUnit,
            Collection<? extends AnnotationFS> aTokens)
    {
        if (aTokens.isEmpty()) {
            return new Span[0];
        }
        
        // Create spans from target annotations
        var annotationType = getType(aCas, layerName);
        var feature = annotationType.getFeatureByBaseName(featureName);
        var annotations = selectCovered(annotationType, aSampleUnit);

        if (annotations.isEmpty()) {
            return new Span[0];
        }

        // Convert character offsets to token indices
        var idxTokenBeginOffset = new Int2ObjectOpenHashMap<AnnotationFS>();
        var idxTokenEndOffset = new Int2ObjectOpenHashMap<AnnotationFS>();
        var idxToken = new Object2IntOpenHashMap<AnnotationFS>();
        var idx = 0;
        for (var token : aTokens) {
            idxTokenBeginOffset.put(token.getBegin(), token);
            idxTokenEndOffset.put(token.getEnd(), token);
            idxToken.put(token, idx);
            idx++;
        }

        var result = new ArrayList<Span>();
        var highestEndTokenPositionObserved = -1;
        var numberOfAnnotations = annotations.size();
        for (int i = 0; i < numberOfAnnotations; i++) {
            var annotation = annotations.get(i);
            var label = annotation.getFeatureValueAsString(feature);
            if (isBlank(label)) {
                label = BLANK_LABEL;
            }

            var beginToken = idxTokenBeginOffset.get(annotation.getBegin());
            var endToken = idxTokenEndOffset.get(annotation.getEnd());
            if (beginToken == null || endToken == null) {
                LOG.warn("Skipping annotation not starting/ending at token boundaries: [{}-{}, {}]",
                        annotation.getBegin(), annotation.getEnd(), label);
                continue;
            }

            var begin = idxToken.getInt(beginToken);
            var end = idxToken.getInt(endToken);

            // If the begin offset of the current annotation is lower than the highest offset so far
            // observed, then it is overlapping with some annotation that we have seen before.
            // Because OpenNLP NER does not support overlapping annotations, we skip it.
            if (begin < highestEndTokenPositionObserved) {
                LOG.debug("Skipping overlapping annotation: [{}-{}, {}]", begin, end + 1, label);
                continue;
            }

            result.add(new Span(begin, end + 1, label));
            highestEndTokenPositionObserved = end + 1;
        }

        return result.toArray(new Span[result.size()]);
    }

    private TokenNameFinderModel train(List<NameSample> aNameSamples,
            TrainingParameters aParameters)
        throws RecommendationException
    {
        try (var stream = new NameSampleStream(aNameSamples)) {
            var finderFactory = new TokenNameFinderFactory();
            return NameFinderME.train("unknown", null, stream, aParameters, finderFactory);
        }
        catch (IOException e) {
            LOG.error("Exception during training the OpenNLP Named Entity Recognizer model.", e);
            throw new RecommendationException("Error while training OpenNLP pos", e);
        }
    }
}
