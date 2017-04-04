/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.job.config;

import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.xpack.ml.support.AbstractSerializingTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.mock;

public class JobUpdateTests extends AbstractSerializingTestCase<JobUpdate> {

    @Override
    protected JobUpdate createTestInstance() {
        JobUpdate.Builder update = new JobUpdate.Builder(randomAlphaOfLength(4));
        if (randomBoolean()) {
            update.setDescription(randomAlphaOfLength(20));
        }
        if (randomBoolean()) {
            List<JobUpdate.DetectorUpdate> detectorUpdates = null;
            int size = randomInt(10);
            detectorUpdates = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                String detectorDescription = null;
                if (randomBoolean()) {
                    detectorDescription = randomAlphaOfLength(12);
                }
                List<DetectionRule> detectionRules = null;
                if (randomBoolean()) {
                    detectionRules = new ArrayList<>();
                    Condition condition = new Condition(Operator.GT, "5");
                    detectionRules.add(new DetectionRule("foo", null, Connective.OR, Collections.singletonList(
                            new RuleCondition(RuleConditionType.NUMERICAL_ACTUAL, null, null, condition, null))));
                }
                detectorUpdates.add(new JobUpdate.DetectorUpdate(i, detectorDescription, detectionRules));
            }
            update.setDetectorUpdates(detectorUpdates);
        }
        if (randomBoolean()) {
            update.setModelPlotConfig(new ModelPlotConfig(randomBoolean(), randomAlphaOfLength(10)));
        }
        if (randomBoolean()) {
            update.setAnalysisLimits(new AnalysisLimits(randomNonNegativeLong(), randomNonNegativeLong()));
        }
        if (randomBoolean()) {
            update.setRenormalizationWindowDays(randomNonNegativeLong());
        }
        if (randomBoolean()) {
            update.setBackgroundPersistInterval(TimeValue.timeValueHours(randomIntBetween(1, 24)));
        }
        if (randomBoolean()) {
            update.setModelSnapshotRetentionDays(randomNonNegativeLong());
        }
        if (randomBoolean()) {
            update.setResultsRetentionDays(randomNonNegativeLong());
        }
        if (randomBoolean()) {
            update.setCategorizationFilters(Arrays.asList(generateRandomStringArray(10, 10, false)));
        }
        if (randomBoolean()) {
            update.setCustomSettings(Collections.singletonMap(randomAlphaOfLength(10), randomAlphaOfLength(10)));
        }
        if (randomBoolean()) {
            update.setModelSnapshotId(randomAlphaOfLength(10));
        }

        return update.build();
    }

    @Override
    protected Writeable.Reader<JobUpdate> instanceReader() {
        return JobUpdate::new;
    }

    @Override
    protected JobUpdate parseInstance(XContentParser parser) {
        return JobUpdate.PARSER.apply(parser, null).build();
    }

    public void testMergeWithJob() {
        List<JobUpdate.DetectorUpdate> detectorUpdates = new ArrayList<>();
        List<DetectionRule> detectionRules1 = Collections.singletonList(new DetectionRule("client", null, Connective.OR,
                Collections.singletonList(
                        new RuleCondition(RuleConditionType.NUMERICAL_ACTUAL, null, null, new Condition(Operator.GT, "5"), null))));
        detectorUpdates.add(new JobUpdate.DetectorUpdate(0, "description-1", detectionRules1));
        List<DetectionRule> detectionRules2 = Collections.singletonList(new DetectionRule("host", null, Connective.OR,
                Collections.singletonList(
                        new RuleCondition(RuleConditionType.NUMERICAL_ACTUAL, null, null, new Condition(Operator.GT, "5"), null))));
        detectorUpdates.add(new JobUpdate.DetectorUpdate(1, "description-2", detectionRules2));

        ModelPlotConfig modelPlotConfig = new ModelPlotConfig(randomBoolean(), randomAlphaOfLength(10));
        AnalysisLimits analysisLimits = new AnalysisLimits(randomNonNegativeLong(), randomNonNegativeLong());
        List<String> categorizationFilters = Arrays.asList(generateRandomStringArray(10, 10, false));
        Map<String, Object> customSettings = Collections.singletonMap(randomAlphaOfLength(10), randomAlphaOfLength(10));

        JobUpdate.Builder updateBuilder = new JobUpdate.Builder("foo");
        updateBuilder.setDescription("updated_description");
        updateBuilder.setDetectorUpdates(detectorUpdates);
        updateBuilder.setModelPlotConfig(modelPlotConfig);
        updateBuilder.setAnalysisLimits(analysisLimits);
        updateBuilder.setBackgroundPersistInterval(TimeValue.timeValueHours(randomIntBetween(1, 24)));
        updateBuilder.setResultsRetentionDays(randomNonNegativeLong());
        updateBuilder.setModelSnapshotRetentionDays(randomNonNegativeLong());
        updateBuilder.setRenormalizationWindowDays(randomNonNegativeLong());
        updateBuilder.setCategorizationFilters(categorizationFilters);
        updateBuilder.setCustomSettings(customSettings);
        updateBuilder.setModelSnapshotId(randomAlphaOfLength(10));
        JobUpdate update = updateBuilder.build();

        Job.Builder jobBuilder = new Job.Builder("foo");
        Detector.Builder d1 = new Detector.Builder("info_content", "domain");
        d1.setOverFieldName("client");
        Detector.Builder d2 = new Detector.Builder("min", "field");
        d2.setOverFieldName("host");
        AnalysisConfig.Builder ac = new AnalysisConfig.Builder(Arrays.asList(d1.build(), d2.build()));
        ac.setCategorizationFieldName("cat_field");
        jobBuilder.setAnalysisConfig(ac);
        jobBuilder.setCreateTime(new Date());

        Job updatedJob = update.mergeWithJob(jobBuilder.build());

        assertEquals(update.getDescription(), updatedJob.getDescription());
        assertEquals(update.getModelPlotConfig(), updatedJob.getModelPlotConfig());
        assertEquals(update.getAnalysisLimits(), updatedJob.getAnalysisLimits());
        assertEquals(update.getRenormalizationWindowDays(), updatedJob.getRenormalizationWindowDays());
        assertEquals(update.getBackgroundPersistInterval(), updatedJob.getBackgroundPersistInterval());
        assertEquals(update.getModelSnapshotRetentionDays(), updatedJob.getModelSnapshotRetentionDays());
        assertEquals(update.getResultsRetentionDays(), updatedJob.getResultsRetentionDays());
        assertEquals(update.getCategorizationFilters(), updatedJob.getAnalysisConfig().getCategorizationFilters());
        assertEquals(update.getCustomSettings(), updatedJob.getCustomSettings());
        assertEquals(update.getModelSnapshotId(), updatedJob.getModelSnapshotId());
        for (JobUpdate.DetectorUpdate detectorUpdate : update.getDetectorUpdates()) {
            assertNotNull(updatedJob.getAnalysisConfig().getDetectors().get(detectorUpdate.getIndex()).getDetectorDescription());
            assertEquals(detectorUpdate.getDescription(),
                    updatedJob.getAnalysisConfig().getDetectors().get(detectorUpdate.getIndex()).getDetectorDescription());
            assertNotNull(updatedJob.getAnalysisConfig().getDetectors().get(detectorUpdate.getIndex()).getDetectorDescription());
            assertEquals(detectorUpdate.getRules(),
                    updatedJob.getAnalysisConfig().getDetectors().get(detectorUpdate.getIndex()).getDetectorRules());
        }
    }

    public void testIsAutodetectProcessUpdate() {
        JobUpdate update = new JobUpdate.Builder("foo").build();
        assertFalse(update.isAutodetectProcessUpdate());
        update = new JobUpdate.Builder("foo").setModelPlotConfig(new ModelPlotConfig(true, "ff")).build();
        assertTrue(update.isAutodetectProcessUpdate());
        update = new JobUpdate.Builder("foo").setDetectorUpdates(Arrays.asList(mock(JobUpdate.DetectorUpdate.class))).build();
        assertTrue(update.isAutodetectProcessUpdate());
    }
}