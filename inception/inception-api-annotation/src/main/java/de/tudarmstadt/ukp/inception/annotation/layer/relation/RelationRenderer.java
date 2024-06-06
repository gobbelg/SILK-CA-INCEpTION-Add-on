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
package de.tudarmstadt.ukp.inception.annotation.layer.relation;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.uima.fit.util.CasUtil.selectCovered;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.wicket.Page;
import org.apache.wicket.core.request.handler.IPageRequestHandler;
import org.apache.wicket.request.cycle.PageRequestHandlerTracker;
import org.apache.wicket.request.cycle.RequestCycle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import de.tudarmstadt.ukp.clarin.webanno.api.annotation.rendering.Renderer_ImplBase;
import de.tudarmstadt.ukp.clarin.webanno.model.AnnotationFeature;
import de.tudarmstadt.ukp.inception.rendering.vmodel.VArc;
import de.tudarmstadt.ukp.inception.rendering.vmodel.VComment;
import de.tudarmstadt.ukp.inception.rendering.vmodel.VCommentType;
import de.tudarmstadt.ukp.inception.rendering.vmodel.VDocument;
import de.tudarmstadt.ukp.inception.rendering.vmodel.VID;
import de.tudarmstadt.ukp.inception.rendering.vmodel.VLazyDetail;
import de.tudarmstadt.ukp.inception.rendering.vmodel.VLazyDetailGroup;
import de.tudarmstadt.ukp.inception.rendering.vmodel.VObject;
import de.tudarmstadt.ukp.inception.schema.api.feature.FeatureSupportRegistry;
import de.tudarmstadt.ukp.inception.schema.api.layer.LayerSupportRegistry;
import de.tudarmstadt.ukp.inception.support.uima.ICasUtil;

/**
 * A class that is used to create Brat Arc to CAS relations and vice-versa
 */
public class RelationRenderer
    extends Renderer_ImplBase<RelationAdapter>
{
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final List<RelationLayerBehavior> behaviors;
    private final RelationLayerTraits traits;

    private Type type;
    private Type spanType;
    private Feature targetFeature;
    private Feature sourceFeature;
    private Feature attachFeature;

    public RelationRenderer(RelationAdapter aTypeAdapter,
            LayerSupportRegistry aLayerSupportRegistry,
            FeatureSupportRegistry aFeatureSupportRegistry, List<RelationLayerBehavior> aBehaviors)
    {
        super(aTypeAdapter, aLayerSupportRegistry, aFeatureSupportRegistry);

        if (aBehaviors == null) {
            behaviors = emptyList();
        }
        else {
            List<RelationLayerBehavior> temp = new ArrayList<>(aBehaviors);
            AnnotationAwareOrderComparator.sort(temp);
            behaviors = temp;
        }

        RelationAdapter typeAdapter = getTypeAdapter();
        traits = typeAdapter.getTraits(RelationLayerTraits.class)
                .orElseGet(RelationLayerTraits::new);
    }

    @Override
    protected boolean typeSystemInit(TypeSystem aTypeSystem)
    {
        RelationAdapter typeAdapter = getTypeAdapter();
        type = aTypeSystem.getType(typeAdapter.getAnnotationTypeName());
        spanType = aTypeSystem.getType(typeAdapter.getAttachTypeName());

        if (type == null || spanType == null) {
            // If the types are not defined, then we do not need to try and render them because the
            // CAS does not contain any instances of them
            return false;
        }

        targetFeature = type.getFeatureByBaseName(typeAdapter.getTargetFeatureName());
        sourceFeature = type.getFeatureByBaseName(typeAdapter.getSourceFeatureName());
        attachFeature = spanType.getFeatureByBaseName(typeAdapter.getAttachFeatureName());

        return true;
    }

    @Override
    public List<AnnotationFS> selectAnnotationsInWindow(CAS aCas, int aWindowBegin, int aWindowEnd)
    {
        return selectCovered(aCas, type, aWindowBegin, aWindowEnd);
    }

    @Override
    public void render(final CAS aCas, List<AnnotationFeature> aFeatures, VDocument aResponse,
            int aWindowBegin, int aWindowEnd)
    {
        if (!checkTypeSystem(aCas)) {
            return;
        }

        RelationAdapter typeAdapter = getTypeAdapter();

        // Index mapping annotations to the corresponding rendered arcs
        Map<AnnotationFS, VArc> annoToArcIdx = new HashMap<>();

        List<AnnotationFS> annotations = selectAnnotationsInWindow(aCas, aWindowBegin, aWindowEnd);

        for (AnnotationFS fs : annotations) {
            for (VObject arc : render(aResponse, fs, aFeatures, aWindowBegin, aWindowEnd)) {
                if (!(arc instanceof VArc)) {
                    continue;
                }

                aResponse.add(arc);
                annoToArcIdx.put(fs, (VArc) arc);

                renderRequiredFeatureErrors(aFeatures, fs, aResponse);
            }
        }

        for (RelationLayerBehavior behavior : behaviors) {
            behavior.onRender(typeAdapter, aResponse, annoToArcIdx);
        }
    }

    Optional<String> renderYield(AnnotationFS fs)
    {
        var yield = new HashSet<Annotation>();
        var queue = new ArrayDeque<Annotation>();
        queue.add((Annotation) getTargetFs(fs));
        var relationsBySource = fs.getCAS().<Annotation> select(type)
                .collect(groupingBy(this::getSourceFs));
        while (!queue.isEmpty()) {
            var source = queue.pop();
            if (!yield.contains(source)) {
                yield.add(source);
                var relations = relationsBySource.getOrDefault(source, emptyList());
                for (var rel : relations) {
                    queue.add((Annotation) getTargetFs(rel));
                }
            }
        }

        var sortedYield = yield.stream() //
                .sorted(Comparator.comparingInt(Annotation::getBegin)) //
                .collect(toList());
        var message = getYieldMessage(sortedYield);
        return Optional.of(message);
    }

    @Override
    public List<VObject> render(VDocument aVDocument, AnnotationFS aFS,
            List<AnnotationFeature> aFeatures, int aWindowBegin, int aWindowEnd)
    {
        if (!checkTypeSystem(aFS.getCAS())) {
            return Collections.emptyList();
        }

        var typeAdapter = getTypeAdapter();
        var dependentFs = getTargetFs(aFS);
        var governorFs = getSourceFs(aFS);

        if (dependentFs == null || governorFs == null) {
            StringBuilder message = new StringBuilder();

            message.append("Relation [" + typeAdapter.getLayer().getName() + "] with id ["
                    + ICasUtil.getAddr(aFS) + "] has loose ends - cannot render.");
            if (typeAdapter.getAttachFeatureName() != null) {
                message.append("\nRelation [" + typeAdapter.getLayer().getName()
                        + "] attached to feature [" + typeAdapter.getAttachFeatureName() + "].");
            }
            message.append("\nDependent: " + dependentFs);
            message.append("\nGovernor: " + governorFs);

            RequestCycle requestCycle = RequestCycle.get();
            IPageRequestHandler handler = PageRequestHandlerTracker.getLastHandler(requestCycle);
            Page page = (Page) handler.getPage();
            page.warn(message.toString());

            return Collections.emptyList();
        }

        Map<String, String> labelFeatures = renderLabelFeatureValues(typeAdapter, aFS, aFeatures);

        if (traits.isRenderArcs()) {
            var arc = VArc.builder().forAnnotation(aFS) //
                    .withLayer(typeAdapter.getLayer()) //
                    .withSource(governorFs) //
                    .withTarget(dependentFs) //
                    .withFeatures(labelFeatures) //
                    .build();

            return asList(arc);
        }
        else {
            AnnotationFS governor = (AnnotationFS) governorFs;
            AnnotationFS dependent = (AnnotationFS) dependentFs;

            StringBuilder noteBuilder = new StringBuilder();
            noteBuilder.append(typeAdapter.getLayer().getUiName());
            noteBuilder.append("\n");
            noteBuilder.append(governor.getCoveredText());
            noteBuilder.append(" -> ");
            noteBuilder.append(dependent.getCoveredText());
            noteBuilder.append("\n");

            for (Entry<String, String> entry : labelFeatures.entrySet()) {
                noteBuilder.append(entry.getKey());
                noteBuilder.append(" = ");
                noteBuilder.append(entry.getValue());
                noteBuilder.append("\n");
            }

            String note = noteBuilder.toString().stripTrailing();
            aVDocument.add(new VComment(governorFs, VCommentType.INFO, "\n⬆️ " + note));
            aVDocument.add(new VComment(dependentFs, VCommentType.INFO, "\n⬇️ " + note));

            return Collections.emptyList();
        }
    }

    @Override
    public List<VLazyDetailGroup> lookupLazyDetails(CAS aCas, VID aVid, int aWindowBeginOffset,
            int aWindowEndOffset)
    {
        if (!checkTypeSystem(aCas)) {
            return Collections.emptyList();
        }

        var fs = ICasUtil.selectByAddr(aCas, AnnotationFS.class, aVid.getId());

        var group = new VLazyDetailGroup();

        var dependentFs = getTargetFs(fs);
        if (dependentFs instanceof AnnotationFS) {
            group.addDetail(new VLazyDetail("Target",
                    abbreviate(((AnnotationFS) dependentFs).getCoveredText(), 300)));
        }

        var governorFs = getSourceFs(fs);
        if (governorFs instanceof AnnotationFS) {
            group.addDetail(new VLazyDetail("Origin",
                    abbreviate(((AnnotationFS) governorFs).getCoveredText(), 300)));
        }

        renderYield(fs).ifPresent(
                yield -> group.addDetail(new VLazyDetail("Yield", abbreviate(yield, "...", 300))));

        var details = super.lookupLazyDetails(aCas, aVid, aWindowBeginOffset, aWindowEndOffset);
        if (!group.getDetails().isEmpty()) {
            details.add(0, group);
        }
        return details;
    }

    /**
     * The relations yield message
     */
    private String getYieldMessage(Iterable<Annotation> sortedDepFs)
    {
        StringBuilder cm = new StringBuilder();
        int end = -1;
        for (Annotation depFs : sortedDepFs) {
            if (end == -1) {
                cm.append(depFs.getCoveredText());
                end = depFs.getEnd();
            }
            // if no space between token and punct
            else if (end == depFs.getBegin()) {
                cm.append(depFs.getCoveredText());
                end = depFs.getEnd();
            }
            else if (end + 1 != depFs.getBegin()) {
                cm.append(" ... ").append(depFs.getCoveredText());
                end = depFs.getEnd();
            }
            else {
                cm.append(" ").append(depFs.getCoveredText());
                end = depFs.getEnd();
            }

        }
        return cm.toString();
    }

    /**
     * Get relation links to display in relation yield
     */
    private Map<Integer, Set<Integer>> getRelationLinks(CAS aCas)
    {
        var typeAdapter = getTypeAdapter();
        var relations = new ConcurrentHashMap<Integer, Set<Integer>>();

        for (var fs : aCas.<Annotation> select(type)) {
            var govFs = getSourceFs(fs);
            var depFs = getTargetFs(fs);

            if (govFs == null || depFs == null) {
                log.warn("Relation [" + typeAdapter.getLayer().getName() + "] with id ["
                        + VID.of(fs) + "] has loose ends - cannot render.");
                continue;
            }

            var links = relations.get(ICasUtil.getAddr(depFs));
            if (links == null) {
                links = new ConcurrentSkipListSet<>();
            }

            links.add(ICasUtil.getAddr(govFs));
            relations.put(ICasUtil.getAddr(depFs), links);
        }

        // Update other subsequent links
        for (int i = 0; i < relations.keySet().size(); i++) {
            for (var fs : relations.keySet()) {
                updateLinks(relations, fs);
            }
        }

        // to start displaying the text from the governor, include it
        for (var fs : relations.keySet()) {
            relations.get(fs).add(fs);
        }

        return relations;
    }

    private void updateLinks(Map<Integer, Set<Integer>> aRelLinks, Integer aGov)
    {
        for (var dep : aRelLinks.get(aGov)) {
            if (aRelLinks.containsKey(dep)
                    && !aRelLinks.get(aGov).containsAll(aRelLinks.get(dep))) {
                aRelLinks.get(aGov).addAll(aRelLinks.get(dep));
                updateLinks(aRelLinks, dep);
            }
        }
    }

    private FeatureStructure getSourceFs(FeatureStructure fs)
    {
        if (attachFeature != null) {
            return fs.getFeatureValue(sourceFeature).getFeatureValue(attachFeature);
        }

        return fs.getFeatureValue(sourceFeature);
    }

    private FeatureStructure getTargetFs(FeatureStructure fs)
    {
        if (attachFeature != null) {
            return fs.getFeatureValue(targetFeature).getFeatureValue(attachFeature);
        }

        return fs.getFeatureValue(targetFeature);
    }
}
