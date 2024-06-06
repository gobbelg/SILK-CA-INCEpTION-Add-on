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
package de.tudarmstadt.ukp.inception.recommendation.config;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.session.SessionRegistry;

import de.tudarmstadt.ukp.clarin.webanno.security.UserDao;
import de.tudarmstadt.ukp.inception.documents.api.DocumentService;
import de.tudarmstadt.ukp.inception.preferences.PreferencesService;
import de.tudarmstadt.ukp.inception.project.api.ProjectService;
import de.tudarmstadt.ukp.inception.recommendation.RecommendationEditorExtension;
import de.tudarmstadt.ukp.inception.recommendation.actionbar.RecommenderActionBarExtension;
import de.tudarmstadt.ukp.inception.recommendation.api.LearningRecordService;
import de.tudarmstadt.ukp.inception.recommendation.api.RecommendationService;
import de.tudarmstadt.ukp.inception.recommendation.api.RecommenderFactoryRegistry;
import de.tudarmstadt.ukp.inception.recommendation.api.recommender.RecommendationEngineFactory;
import de.tudarmstadt.ukp.inception.recommendation.exporter.LearningRecordExporter;
import de.tudarmstadt.ukp.inception.recommendation.exporter.RecommenderExporter;
import de.tudarmstadt.ukp.inception.recommendation.footer.RecommendationEventFooterItem;
import de.tudarmstadt.ukp.inception.recommendation.log.RecommendationAcceptedEventAdapter;
import de.tudarmstadt.ukp.inception.recommendation.log.RecommendationRejectedEventAdapter;
import de.tudarmstadt.ukp.inception.recommendation.log.RecommenderDeletedEventAdapter;
import de.tudarmstadt.ukp.inception.recommendation.log.RecommenderEvaluationResultEventAdapter;
import de.tudarmstadt.ukp.inception.recommendation.metrics.RecommendationMetricsImpl;
import de.tudarmstadt.ukp.inception.recommendation.project.ProjectRecommendersMenuItem;
import de.tudarmstadt.ukp.inception.recommendation.project.RecommenderProjectSettingsPanelFactory;
import de.tudarmstadt.ukp.inception.recommendation.render.RecommendationRelationRenderer;
import de.tudarmstadt.ukp.inception.recommendation.render.RecommendationRenderer;
import de.tudarmstadt.ukp.inception.recommendation.render.RecommendationSpanRenderer;
import de.tudarmstadt.ukp.inception.recommendation.service.RecommendationServiceImpl;
import de.tudarmstadt.ukp.inception.recommendation.service.RecommenderFactoryRegistryImpl;
import de.tudarmstadt.ukp.inception.recommendation.sidebar.RecommendationSidebarFactory;
import de.tudarmstadt.ukp.inception.scheduling.SchedulingService;
import de.tudarmstadt.ukp.inception.schema.api.AnnotationSchemaService;
import de.tudarmstadt.ukp.inception.schema.api.feature.FeatureSupportRegistry;

/**
 * Provides all back-end Spring beans for the recommendation functionality.
 */
@Configuration
@EnableConfigurationProperties(RecommenderPropertiesImpl.class)
@ConditionalOnProperty(prefix = "recommender", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RecommenderServiceAutoConfiguration
{
    private @PersistenceContext EntityManager entityManager;

    @Bean
    public RecommendationService recommendationService(PreferencesService aPreferencesService,
            SessionRegistry aSessionRegistry, UserDao aUserRepository,
            RecommenderFactoryRegistry aRecommenderFactoryRegistry,
            SchedulingService aSchedulingService, AnnotationSchemaService aAnnoService,
            DocumentService aDocumentService, ProjectService aProjectService,
            ApplicationEventPublisher aApplicationEventPublisher)
    {
        return new RecommendationServiceImpl(aPreferencesService, aSessionRegistry, aUserRepository,
                aRecommenderFactoryRegistry, aSchedulingService, aAnnoService, aDocumentService,
                aProjectService, entityManager, aApplicationEventPublisher);
    }

    @Bean
    public RecommenderExporter recommenderExporter(AnnotationSchemaService aAnnotationService,
            RecommendationService aRecommendationService)
    {
        return new RecommenderExporter(aAnnotationService, aRecommendationService);
    }

    @Bean
    public LearningRecordExporter learningRecordExporter(AnnotationSchemaService aAnnotationService,
            DocumentService aDocumentService, LearningRecordService aLearningRecordService)
    {
        return new LearningRecordExporter(aAnnotationService, aDocumentService,
                aLearningRecordService);
    }

    @Bean
    public RecommendationAcceptedEventAdapter recommendationAcceptedEventAdapter()
    {
        return new RecommendationAcceptedEventAdapter();
    }

    @Bean
    public RecommendationRejectedEventAdapter recommendationRejectedEventAdapter()
    {
        return new RecommendationRejectedEventAdapter();
    }

    @Bean
    public RecommenderDeletedEventAdapter recommenderDeletedEventAdapter()
    {
        return new RecommenderDeletedEventAdapter();
    }

    @Bean
    public RecommenderEvaluationResultEventAdapter recommenderEvaluationResultEventAdapter()
    {
        return new RecommenderEvaluationResultEventAdapter();
    }

    @Bean
    public RecommenderProjectSettingsPanelFactory recommenderProjectSettingsPanelFactory()
    {
        return new RecommenderProjectSettingsPanelFactory();
    }

    @ConditionalOnWebApplication
    @Bean
    public ProjectRecommendersMenuItem projectRecommendersMenuItem()
    {
        return new ProjectRecommendersMenuItem();
    }

    @ConditionalOnProperty(prefix = "recommender.sidebar", name = "enabled", //
            havingValue = "true", matchIfMissing = true)
    @Bean
    public RecommendationSidebarFactory recommendationSidebarFactory(
            RecommendationService aRecommendationService)
    {
        return new RecommendationSidebarFactory(aRecommendationService);
    }

    @Bean(name = RecommendationEditorExtension.BEAN_NAME)
    @Autowired
    public RecommendationEditorExtension recommendationEditorExtension(
            AnnotationSchemaService aAnnotationService,
            RecommendationService aRecommendationService,
            ApplicationEventPublisher aApplicationEventPublisher, UserDao aUserService,
            FeatureSupportRegistry aFeatureSupportRegistry)
    {
        return new RecommendationEditorExtension(aAnnotationService, aRecommendationService,
                aApplicationEventPublisher, aUserService, aFeatureSupportRegistry);
    }

    @Bean
    public RecommenderFactoryRegistry recommenderFactoryRegistry(
            @Lazy @Autowired(required = false) List<RecommendationEngineFactory<?>> aExtensions)
    {
        return new RecommenderFactoryRegistryImpl(aExtensions);
    }

    @ConditionalOnWebApplication
    @Bean
    @Autowired
    @ConditionalOnProperty(prefix = "monitoring.metrics", name = "enabled", havingValue = "true")
    public RecommendationMetricsImpl recommendationMetricsImpl(RecommendationService aRecService)
    {
        return new RecommendationMetricsImpl(aRecService);

    }

    @ConditionalOnWebApplication
    @Bean
    @ConditionalOnProperty(prefix = "websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RecommendationEventFooterItem recommendationEventFooterItem()
    {
        return new RecommendationEventFooterItem();
    }

    @Bean
    public RecommendationRenderer recommendationRenderer(AnnotationSchemaService aAnnotationService,
            RecommendationSpanRenderer aRecommendationSpanRenderer,
            RecommendationRelationRenderer aRecommendationRelationRenderer,
            RecommendationService aRecommendationService)
    {
        return new RecommendationRenderer(aAnnotationService, aRecommendationSpanRenderer,
                aRecommendationRelationRenderer, aRecommendationService);
    }

    @Bean
    public RecommendationSpanRenderer recommendationSpanRenderer(
            RecommendationService aRecommendationService,
            AnnotationSchemaService aAnnotationService, FeatureSupportRegistry aFsRegistry,
            RecommenderProperties aRecommenderProperties)
    {
        return new RecommendationSpanRenderer(aRecommendationService, aAnnotationService,
                aFsRegistry, aRecommenderProperties);
    }

    @Bean
    public RecommendationRelationRenderer recommendationRelationRenderer(
            RecommendationService aRecommendationService,
            AnnotationSchemaService aAnnotationService, FeatureSupportRegistry aFsRegistry)
    {
        return new RecommendationRelationRenderer(aRecommendationService, aAnnotationService,
                aFsRegistry);
    }

    @Bean
    public RecommenderActionBarExtension recommenderActionBarExtension(
            RecommendationService aRecommendationService)
    {
        return new RecommenderActionBarExtension(aRecommendationService);
    }
}
