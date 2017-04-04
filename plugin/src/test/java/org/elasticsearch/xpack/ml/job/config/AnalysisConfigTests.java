/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License;
 * you may not use this file except in compliance with the Elastic License.
 */
package org.elasticsearch.xpack.ml.job.config;

import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.test.ESTestCase;
import org.elasticsearch.xpack.ml.job.messages.Messages;
import org.elasticsearch.xpack.ml.support.AbstractSerializingTestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class AnalysisConfigTests extends AbstractSerializingTestCase<AnalysisConfig> {

    @Override
    protected AnalysisConfig createTestInstance() {
        return createRandomized().build();
    }

    public static AnalysisConfig.Builder createRandomized() {
        List<Detector> detectors = new ArrayList<>();
        int numDetectors = randomIntBetween(1, 10);
        for (int i = 0; i < numDetectors; i++) {
            Detector.Builder builder = new Detector.Builder("count", null);
            builder.setPartitionFieldName("part");
            detectors.add(builder.build());
        }
        AnalysisConfig.Builder builder = new AnalysisConfig.Builder(detectors);

        if (randomBoolean()) {
            builder.setBatchSpan(TimeValue.timeValueSeconds(randomIntBetween(1, 1_000_000)));
        }
        TimeValue bucketSpan = AnalysisConfig.Builder.DEFAULT_BUCKET_SPAN;
        if (randomBoolean()) {
            bucketSpan = TimeValue.timeValueSeconds(randomIntBetween(1, 1_000_000));
            builder.setBucketSpan(bucketSpan);
        }
        if (randomBoolean()) {
            builder.setCategorizationFieldName(randomAlphaOfLength(10));
            builder.setCategorizationFilters(Arrays.asList(generateRandomStringArray(10, 10, false)));
        }
        if (randomBoolean()) {
            builder.setLatency(TimeValue.timeValueSeconds(randomIntBetween(1, 1_000_000)));
        }
        if (randomBoolean()) {
            int numBucketSpans = randomIntBetween(0, 10);
            List<TimeValue> multipleBucketSpans = new ArrayList<>();
            for (int i = 2; i <= numBucketSpans; i++) {
                multipleBucketSpans.add(TimeValue.timeValueSeconds(bucketSpan.getSeconds() * i));
            }
            builder.setMultipleBucketSpans(multipleBucketSpans);
        }
        if (randomBoolean()) {
            builder.setMultivariateByFields(randomBoolean());
        }
        if (randomBoolean()) {
            builder.setOverlappingBuckets(randomBoolean());
        }
        if (randomBoolean()) {
            builder.setResultFinalizationWindow(randomNonNegativeLong());
        }

        boolean usePerPartitionNormalisation = randomBoolean();
        builder.setUsePerPartitionNormalization(usePerPartitionNormalisation);
        if (!usePerPartitionNormalisation) { // influencers can't be used with per partition normalisation
            builder.setInfluencers(Arrays.asList(generateRandomStringArray(10, 10, false)));
        }
        return builder;
    }

    @Override
    protected Writeable.Reader<AnalysisConfig> instanceReader() {
        return AnalysisConfig::new;
    }

    @Override
    protected AnalysisConfig parseInstance(XContentParser parser) {
        return AnalysisConfig.PARSER.apply(parser, null).build();
    }

    public void testFieldConfiguration_singleDetector_notPreSummarised() {
        // Single detector, not pre-summarised
        Detector.Builder det = new Detector.Builder("metric", "responsetime");
        det.setByFieldName("airline");
        det.setPartitionFieldName("sourcetype");
        AnalysisConfig ac = createConfigWithDetectors(Collections.singletonList(det.build()));

        Set<String> termFields = new TreeSet<>(Arrays.asList(new String[]{
                "airline", "sourcetype"}));
        Set<String> analysisFields = new TreeSet<>(Arrays.asList(new String[]{
                "responsetime", "airline", "sourcetype"}));

        assertEquals(termFields.size(), ac.termFields().size());
        assertEquals(analysisFields.size(), ac.analysisFields().size());

        for (String s : ac.termFields()) {
            assertTrue(termFields.contains(s));
        }

        for (String s : termFields) {
            assertTrue(ac.termFields().contains(s));
        }

        for (String s : ac.analysisFields()) {
            assertTrue(analysisFields.contains(s));
        }

        for (String s : analysisFields) {
            assertTrue(ac.analysisFields().contains(s));
        }

        assertEquals(1, ac.fields().size());
        assertTrue(ac.fields().contains("responsetime"));

        assertEquals(1, ac.byFields().size());
        assertTrue(ac.byFields().contains("airline"));

        assertEquals(1, ac.partitionFields().size());
        assertTrue(ac.partitionFields().contains("sourcetype"));

        assertNull(ac.getSummaryCountFieldName());

        // Single detector, pre-summarised
        analysisFields.add("summaryCount");
        AnalysisConfig.Builder builder = new AnalysisConfig.Builder(ac);
        builder.setSummaryCountFieldName("summaryCount");
        ac = builder.build();

        for (String s : ac.analysisFields()) {
            assertTrue(analysisFields.contains(s));
        }

        for (String s : analysisFields) {
            assertTrue(ac.analysisFields().contains(s));
        }

        assertEquals("summaryCount", ac.getSummaryCountFieldName());
    }

    public void testFieldConfiguration_multipleDetectors_NotPreSummarised() {
        // Multiple detectors, not pre-summarised
        List<Detector> detectors = new ArrayList<>();

        Detector.Builder det = new Detector.Builder("metric", "metric1");
        det.setByFieldName("by_one");
        det.setPartitionFieldName("partition_one");
        detectors.add(det.build());

        det = new Detector.Builder("metric", "metric2");
        det.setByFieldName("by_two");
        det.setOverFieldName("over_field");
        detectors.add(det.build());

        det = new Detector.Builder("metric", "metric2");
        det.setByFieldName("by_two");
        det.setPartitionFieldName("partition_two");
        detectors.add(det.build());

        AnalysisConfig.Builder builder = new AnalysisConfig.Builder(detectors);
        builder.setInfluencers(Collections.singletonList("Influencer_Field"));
        AnalysisConfig ac = builder.build();


        Set<String> termFields = new TreeSet<>(Arrays.asList(new String[]{
                "by_one", "by_two", "over_field",
                "partition_one", "partition_two", "Influencer_Field"}));
        Set<String> analysisFields = new TreeSet<>(Arrays.asList(new String[]{
                "metric1", "metric2", "by_one", "by_two", "over_field",
                "partition_one", "partition_two", "Influencer_Field"}));

        assertEquals(termFields.size(), ac.termFields().size());
        assertEquals(analysisFields.size(), ac.analysisFields().size());

        for (String s : ac.termFields()) {
            assertTrue(s, termFields.contains(s));
        }

        for (String s : termFields) {
            assertTrue(s, ac.termFields().contains(s));
        }

        for (String s : ac.analysisFields()) {
            assertTrue(analysisFields.contains(s));
        }

        for (String s : analysisFields) {
            assertTrue(ac.analysisFields().contains(s));
        }

        assertEquals(2, ac.fields().size());
        assertTrue(ac.fields().contains("metric1"));
        assertTrue(ac.fields().contains("metric2"));

        assertEquals(2, ac.byFields().size());
        assertTrue(ac.byFields().contains("by_one"));
        assertTrue(ac.byFields().contains("by_two"));

        assertEquals(1, ac.overFields().size());
        assertTrue(ac.overFields().contains("over_field"));

        assertEquals(2, ac.partitionFields().size());
        assertTrue(ac.partitionFields().contains("partition_one"));
        assertTrue(ac.partitionFields().contains("partition_two"));

        assertNull(ac.getSummaryCountFieldName());
    }

    public void testFieldConfiguration_multipleDetectors_PreSummarised() {
        // Multiple detectors, pre-summarised
        AnalysisConfig.Builder builder = createConfigBuilder();
        builder.setSummaryCountFieldName("summaryCount");
        AnalysisConfig ac = builder.build();

        assertTrue(ac.analysisFields().contains("summaryCount"));
        assertEquals("summaryCount", ac.getSummaryCountFieldName());

        builder = createConfigBuilder();
        builder.setBucketSpan(TimeValue.timeValueSeconds(1000));
        builder.setMultipleBucketSpans(Arrays.asList(
                TimeValue.timeValueSeconds(5000), TimeValue.timeValueSeconds(10000), TimeValue.timeValueSeconds(24000)));
        ac = builder.build();
        assertTrue(ac.getMultipleBucketSpans().contains(TimeValue.timeValueSeconds(5000)));
        assertTrue(ac.getMultipleBucketSpans().contains(TimeValue.timeValueSeconds(10000)));
        assertTrue(ac.getMultipleBucketSpans().contains(TimeValue.timeValueSeconds(24000)));
    }

    public void testEquals_GivenSameReference() {
        AnalysisConfig config = createFullyPopulatedConfig();
        assertTrue(config.equals(config));
    }

    public void testEquals_GivenDifferentClass() {
        assertFalse(createFullyPopulatedConfig().equals("a string"));
    }

    public void testEquals_GivenNull() {
        assertFalse(createFullyPopulatedConfig().equals(null));
    }

    public void testEquals_GivenEqualConfig() {
        AnalysisConfig config1 = createFullyPopulatedConfig();
        AnalysisConfig config2 = createFullyPopulatedConfig();

        assertTrue(config1.equals(config2));
        assertTrue(config2.equals(config1));
        assertEquals(config1.hashCode(), config2.hashCode());
    }

    public void testEquals_GivenDifferentBatchSpan() {
        AnalysisConfig.Builder builder = createConfigBuilder();
        builder.setBatchSpan(TimeValue.timeValueHours(3));
        AnalysisConfig config1 = builder.build();

        builder = createConfigBuilder();
        builder.setBatchSpan(TimeValue.timeValueHours(4));
        AnalysisConfig config2 = builder.build();

        assertFalse(config1.equals(config2));
        assertFalse(config2.equals(config1));
    }

    public void testEquals_GivenDifferentBucketSpan() {
        AnalysisConfig.Builder builder = createConfigBuilder();
        builder.setBucketSpan(TimeValue.timeValueSeconds(1800));
        AnalysisConfig config1 = builder.build();

        builder = createConfigBuilder();
        builder.setBucketSpan(TimeValue.timeValueHours(1));
        AnalysisConfig config2 = builder.build();

        assertFalse(config1.equals(config2));
        assertFalse(config2.equals(config1));
    }

    public void testEquals_GivenCategorizationField() {
        AnalysisConfig.Builder builder = createConfigBuilder();
        builder.setCategorizationFieldName("foo");
        AnalysisConfig config1 = builder.build();

        builder = createConfigBuilder();
        builder.setCategorizationFieldName("bar");
        AnalysisConfig config2 = builder.build();

        assertFalse(config1.equals(config2));
        assertFalse(config2.equals(config1));
    }

    public void testEquals_GivenDifferentDetector() {
        AnalysisConfig config1 = createConfigWithDetectors(Collections.singletonList(new Detector.Builder("min", "low_count").build()));

        AnalysisConfig config2 = createConfigWithDetectors(Collections.singletonList(new Detector.Builder("min", "high_count").build()));

        assertFalse(config1.equals(config2));
        assertFalse(config2.equals(config1));
    }

    public void testEquals_GivenDifferentInfluencers() {
        AnalysisConfig.Builder builder = createConfigBuilder();
        builder.setInfluencers(Arrays.asList("foo"));
        AnalysisConfig config1 = builder.build();

        builder = createConfigBuilder();
        builder.setInfluencers(Arrays.asList("bar"));
        AnalysisConfig config2 = builder.build();

        assertFalse(config1.equals(config2));
        assertFalse(config2.equals(config1));
    }

    public void testEquals_GivenDifferentLatency() {
        AnalysisConfig.Builder builder = createConfigBuilder();
        builder.setLatency(TimeValue.timeValueSeconds(1800));
        AnalysisConfig config1 = builder.build();

        builder = createConfigBuilder();
        builder.setLatency(TimeValue.timeValueSeconds(1801));
        AnalysisConfig config2 = builder.build();

        assertFalse(config1.equals(config2));
        assertFalse(config2.equals(config1));
    }

    public void testEquals_GivenDifferentPeriod() {
        AnalysisConfig.Builder builder = createConfigBuilder();
        builder.setPeriod(1800L);
        AnalysisConfig config1 = builder.build();

        builder = createConfigBuilder();
        builder.setPeriod(3600L);
        AnalysisConfig config2 = builder.build();

        assertFalse(config1.equals(config2));
        assertFalse(config2.equals(config1));
    }

    public void testEquals_GivenSummaryCountField() {
        AnalysisConfig.Builder builder = createConfigBuilder();
        builder.setSummaryCountFieldName("foo");
        AnalysisConfig config1 = builder.build();

        builder = createConfigBuilder();
        builder.setSummaryCountFieldName("bar");
        AnalysisConfig config2 = builder.build();

        assertFalse(config1.equals(config2));
        assertFalse(config2.equals(config1));
    }

    public void testEquals_GivenMultivariateByField() {
        AnalysisConfig.Builder builder = createConfigBuilder();
        builder.setMultivariateByFields(true);
        AnalysisConfig config1 = builder.build();

        builder = createConfigBuilder();
        builder.setMultivariateByFields(false);
        AnalysisConfig config2 = builder.build();

        assertFalse(config1.equals(config2));
        assertFalse(config2.equals(config1));
    }

    public void testEquals_GivenDifferentCategorizationFilters() {
        AnalysisConfig config1 = createFullyPopulatedConfig();
        AnalysisConfig.Builder builder = createConfigBuilder();
        builder.setCategorizationFilters(Arrays.asList("foo", "bar"));
        builder.setCategorizationFieldName("cat");
        AnalysisConfig config2 = builder.build();

        assertFalse(config1.equals(config2));
        assertFalse(config2.equals(config1));
    }

    public void testExtractReferencedLists() {
        DetectionRule rule1 =
                new DetectionRule(null, null, Connective.OR, Arrays.asList(RuleCondition.createCategorical("foo", "filter1")));
        DetectionRule rule2 =
                new DetectionRule(null, null, Connective.OR, Arrays.asList(RuleCondition.createCategorical("foo", "filter2")));
        Detector.Builder detector1 = new Detector.Builder("count", null);
        detector1.setByFieldName("foo");
        detector1.setDetectorRules(Arrays.asList(rule1));
        Detector.Builder detector2 = new Detector.Builder("count", null);
        detector2.setDetectorRules(Arrays.asList(rule2));
        detector2.setByFieldName("foo");
        AnalysisConfig config = new AnalysisConfig.Builder(
                Arrays.asList(detector1.build(), detector2.build(), new Detector.Builder("count", null).build())).build();

        assertEquals(new HashSet<>(Arrays.asList("filter1", "filter2")), config.extractReferencedFilters());
    }

    private static AnalysisConfig createFullyPopulatedConfig() {
        AnalysisConfig.Builder builder = new AnalysisConfig.Builder(
                Collections.singletonList(new Detector.Builder("min", "count").build()));
        builder.setBucketSpan(TimeValue.timeValueHours(1));
        builder.setBatchSpan(TimeValue.timeValueHours(24));
        builder.setCategorizationFieldName("cat");
        builder.setCategorizationFilters(Arrays.asList("foo"));
        builder.setInfluencers(Arrays.asList("myInfluencer"));
        builder.setLatency(TimeValue.timeValueSeconds(3600));
        builder.setPeriod(100L);
        builder.setSummaryCountFieldName("sumCount");
        return builder.build();
    }

    private static AnalysisConfig createConfigWithDetectors(List<Detector> detectors) {
        return new AnalysisConfig.Builder(detectors).build();
    }

    private static AnalysisConfig.Builder createConfigBuilder() {
        return new AnalysisConfig.Builder(Collections.singletonList(new Detector.Builder("min", "count").build()));
    }

    public void testVerify_throws() {
        // count works with no fields
        Detector d = new Detector.Builder("count", null).build();
        new AnalysisConfig.Builder(Collections.singletonList(d)).build();

        try {
            d = new Detector.Builder("distinct_count", null).build();
            new AnalysisConfig.Builder(Collections.singletonList(d)).build();
            assertTrue(false); // shouldn't get here
        } catch (IllegalArgumentException e) {
            assertEquals("Unless the function is 'count' one of field_name, by_field_name or over_field_name must be set", e.getMessage());
        }

        // should work now
        Detector.Builder builder = new Detector.Builder("distinct_count", "somefield");
        builder.setOverFieldName("over");
        new AnalysisConfig.Builder(Collections.singletonList(builder.build())).build();

        builder = new Detector.Builder("info_content", "somefield");
        builder.setOverFieldName("over");
        d = builder.build();
        new AnalysisConfig.Builder(Collections.singletonList(builder.build())).build();

        builder.setByFieldName("by");
        new AnalysisConfig.Builder(Collections.singletonList(builder.build())).build();

        try {
            builder = new Detector.Builder("made_up_function", "somefield");
            builder.setOverFieldName("over");
            new AnalysisConfig.Builder(Collections.singletonList(builder.build())).build();
            assertTrue(false); // shouldn't get here
        } catch (IllegalArgumentException e) {
            assertEquals("Unknown function 'made_up_function'", e.getMessage());
        }
    }

    public void testVerify_GivenNegativeBucketSpan() {
        AnalysisConfig.Builder config = createValidConfig();
        config.setBucketSpan(TimeValue.timeValueSeconds(-1));

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class, () -> config.build());

        assertEquals("bucket_span cannot be less or equal than 0. Value = -1", e.getMessage());
    }

    public void testVerify_GivenNegativeBatchSpan() {
        AnalysisConfig.Builder analysisConfig = createValidConfig();
        analysisConfig.setBatchSpan(TimeValue.timeValueSeconds(-1));

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class, () -> analysisConfig.build());

        assertEquals("batch_span cannot be less or equal than 0. Value = -1", e.getMessage());
    }

    public void testVerify_GivenNegativeLatency() {
        AnalysisConfig.Builder analysisConfig = createValidConfig();
        analysisConfig.setLatency(TimeValue.timeValueSeconds(-1));

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class, () -> analysisConfig.build());

        assertEquals("latency cannot be less than 0. Value = -1", e.getMessage());
    }

    public void testVerify_GivenNegativePeriod() {
        AnalysisConfig.Builder analysisConfig = createValidConfig();
        analysisConfig.setPeriod(-1L);

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class, () -> analysisConfig.build());

        assertEquals(Messages.getMessage(Messages.JOB_CONFIG_FIELD_VALUE_TOO_LOW, "period", 0, -1), e.getMessage());
    }


    public void testVerify_GivenDefaultConfig_ShouldBeInvalidDueToNoDetectors() {
        AnalysisConfig.Builder analysisConfig = createValidConfig();
        analysisConfig.setDetectors(null);

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class, () -> analysisConfig.build());

        assertEquals(Messages.getMessage(Messages.JOB_CONFIG_NO_DETECTORS), e.getMessage());
    }


    public void testVerify_GivenValidConfig() {
        AnalysisConfig.Builder analysisConfig = createValidConfig();
        analysisConfig.build();
    }

    public void testVerify_GivenValidConfigWithCategorizationFieldNameAndCategorizationFilters() {
        AnalysisConfig.Builder analysisConfig = createValidConfig();
        analysisConfig.setCategorizationFieldName("myCategory");
        analysisConfig.setCategorizationFilters(Arrays.asList("foo", "bar"));

        analysisConfig.build();
    }

    public void testVerify_OverlappingBuckets() {
        List<Detector> detectors;
        Detector detector;

        boolean onByDefault = false;

        // Uncomment this when overlappingBuckets turned on by default
        if (onByDefault) {
            // Test overlappingBuckets unset
            AnalysisConfig.Builder analysisConfig = createValidConfig();
            analysisConfig.setBucketSpan(TimeValue.timeValueSeconds(5000L));
            analysisConfig.setBatchSpan(TimeValue.ZERO);
            detectors = new ArrayList<>();
            detector = new Detector.Builder("count", null).build();
            detectors.add(detector);
            detector = new Detector.Builder("mean", "value").build();
            detectors.add(detector);
            analysisConfig.setDetectors(detectors);
            AnalysisConfig ac = analysisConfig.build();
            assertTrue(ac.getOverlappingBuckets());

            // Test overlappingBuckets unset
            analysisConfig = createValidConfig();
            analysisConfig.setBucketSpan(TimeValue.timeValueSeconds(5000L));
            analysisConfig.setBatchSpan(TimeValue.ZERO);
            detectors = new ArrayList<>();
            detector = new Detector.Builder("count", null).build();
            detectors.add(detector);
            detector = new Detector.Builder("rare", "value").build();
            detectors.add(detector);
            analysisConfig.setDetectors(detectors);
            ac = analysisConfig.build();
            assertFalse(ac.getOverlappingBuckets());

            // Test overlappingBuckets unset
            analysisConfig = createValidConfig();
            analysisConfig.setBucketSpan(TimeValue.timeValueSeconds(5000L));
            analysisConfig.setBatchSpan(TimeValue.ZERO);
            detectors = new ArrayList<>();
            detector = new Detector.Builder("count", null).build();
            detectors.add(detector);
            detector = new Detector.Builder("min", "value").build();
            detectors.add(detector);
            detector = new Detector.Builder("max", "value").build();
            detectors.add(detector);
            analysisConfig.setDetectors(detectors);
            ac = analysisConfig.build();
            assertFalse(ac.getOverlappingBuckets());
        }

        // Test overlappingBuckets set
        AnalysisConfig.Builder analysisConfig = createValidConfig();
        analysisConfig.setBucketSpan(TimeValue.timeValueSeconds(5000L));
        detectors = new ArrayList<>();
        detector = new Detector.Builder("count", null).build();
        detectors.add(detector);
        Detector.Builder builder = new Detector.Builder("rare", null);
        builder.setByFieldName("value");
        detectors.add(builder.build());
        analysisConfig.setOverlappingBuckets(false);
        analysisConfig.setDetectors(detectors);
        assertFalse(analysisConfig.build().getOverlappingBuckets());

        // Test overlappingBuckets set
        analysisConfig = createValidConfig();
        analysisConfig.setBucketSpan(TimeValue.timeValueSeconds(5000L));
        analysisConfig.setOverlappingBuckets(true);
        detectors = new ArrayList<>();
        detector = new Detector.Builder("count", null).build();
        detectors.add(detector);
        builder = new Detector.Builder("rare", null);
        builder.setByFieldName("value");
        detectors.add(builder.build());
        analysisConfig.setDetectors(detectors);
        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class, analysisConfig::build);
        assertEquals("Overlapping buckets cannot be used with function '[rare]'", e.getMessage());

        // Test overlappingBuckets set
        analysisConfig = createValidConfig();
        analysisConfig.setBucketSpan(TimeValue.timeValueSeconds(5000L));
        analysisConfig.setOverlappingBuckets(false);
        detectors = new ArrayList<>();
        detector = new Detector.Builder("count", null).build();
        detectors.add(detector);
        detector = new Detector.Builder("mean", "value").build();
        detectors.add(detector);
        analysisConfig.setDetectors(detectors);
        AnalysisConfig ac = analysisConfig.build();
        assertFalse(ac.getOverlappingBuckets());
    }

    public void testMultipleBucketsConfig() {
        AnalysisConfig.Builder ac = createValidConfig();
        ac.setMultipleBucketSpans(Arrays.asList(
                TimeValue.timeValueSeconds(10L),
                TimeValue.timeValueSeconds(15L),
                TimeValue.timeValueSeconds(20L),
                TimeValue.timeValueSeconds(25L),
                TimeValue.timeValueSeconds(30L),
                TimeValue.timeValueSeconds(35L)));
        List<Detector> detectors = new ArrayList<>();
        Detector detector = new Detector.Builder("count", null).build();
        detectors.add(detector);
        ac.setDetectors(detectors);

        ac.setBucketSpan(TimeValue.timeValueSeconds(4L));
        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class, ac::build);
        assertEquals(Messages.getMessage(Messages.JOB_CONFIG_MULTIPLE_BUCKETSPANS_MUST_BE_MULTIPLE, "10s", "4s"), e.getMessage());

        ac.setBucketSpan(TimeValue.timeValueSeconds(5L));
        ac.build();

        AnalysisConfig.Builder ac2 = createValidConfig();
        ac2.setBucketSpan(TimeValue.timeValueSeconds(5L));
        ac2.setDetectors(detectors);
        ac2.setMultipleBucketSpans(Arrays.asList(
                TimeValue.timeValueSeconds(10L),
                TimeValue.timeValueSeconds(15L),
                TimeValue.timeValueSeconds(20L),
                TimeValue.timeValueSeconds(25L),
                TimeValue.timeValueSeconds(30L)));
        assertFalse(ac.equals(ac2));
        ac2.setMultipleBucketSpans(Arrays.asList(
                TimeValue.timeValueSeconds(10L),
                TimeValue.timeValueSeconds(15L),
                TimeValue.timeValueSeconds(20L),
                TimeValue.timeValueSeconds(25L),
                TimeValue.timeValueSeconds(30L),
                TimeValue.timeValueSeconds(35L)));

        ac.setBucketSpan(TimeValue.timeValueSeconds(222L));
        ac.setMultipleBucketSpans(Arrays.asList());
        ac.build();

        ac.setMultipleBucketSpans(Arrays.asList(TimeValue.timeValueSeconds(222L)));
        e = ESTestCase.expectThrows(IllegalArgumentException.class, () -> ac.build());
        assertEquals(Messages.getMessage(Messages.JOB_CONFIG_MULTIPLE_BUCKETSPANS_MUST_BE_MULTIPLE, "3.7m", "3.7m"), e.getMessage());

        ac.setMultipleBucketSpans(Arrays.asList(TimeValue.timeValueSeconds(-444L), TimeValue.timeValueSeconds(-888L)));
        e = ESTestCase.expectThrows(IllegalArgumentException.class, () -> ac.build());
        assertEquals(Messages.getMessage(Messages.JOB_CONFIG_MULTIPLE_BUCKETSPANS_MUST_BE_MULTIPLE, -444, "3.7m"), e.getMessage());
    }

    public void testVerify_GivenCategorizationFiltersButNoCategorizationFieldName() {
        AnalysisConfig.Builder config = createValidConfig();
        config.setCategorizationFilters(Arrays.asList("foo"));

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class, () -> config.build());

        assertEquals(Messages.getMessage(Messages.JOB_CONFIG_CATEGORIZATION_FILTERS_REQUIRE_CATEGORIZATION_FIELD_NAME), e.getMessage());
    }

    public void testVerify_GivenDuplicateCategorizationFilters() {
        AnalysisConfig.Builder config = createValidConfig();
        config.setCategorizationFieldName("myCategory");
        config.setCategorizationFilters(Arrays.asList("foo", "bar", "foo"));

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class, () -> config.build());

        assertEquals(Messages.getMessage(Messages.JOB_CONFIG_CATEGORIZATION_FILTERS_CONTAINS_DUPLICATES), e.getMessage());
    }

    public void testVerify_GivenEmptyCategorizationFilter() {
        AnalysisConfig.Builder config = createValidConfig();
        config.setCategorizationFieldName("myCategory");
        config.setCategorizationFilters(Arrays.asList("foo", ""));

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class, () -> config.build());

        assertEquals(Messages.getMessage(Messages.JOB_CONFIG_CATEGORIZATION_FILTERS_CONTAINS_EMPTY), e.getMessage());
    }


    public void testCheckDetectorsHavePartitionFields() {
        AnalysisConfig.Builder config = createValidConfig();
        config.setUsePerPartitionNormalization(true);

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class, () -> config.build());

        assertEquals(Messages.getMessage(Messages.JOB_CONFIG_PER_PARTITION_NORMALIZATION_REQUIRES_PARTITION_FIELD), e.getMessage());
    }

    public void testCheckDetectorsHavePartitionFields_doesntThrowWhenValid() {
        AnalysisConfig.Builder config = createValidConfig();
        Detector.Builder builder = new Detector.Builder(config.build().getDetectors().get(0));
        builder.setPartitionFieldName("pField");
        config.build().getDetectors().set(0, builder.build());
        config.setUsePerPartitionNormalization(true);

        config.build();
    }

    public void testCheckNoInfluencersAreSet() {

        AnalysisConfig.Builder config = createValidConfig();
        Detector.Builder builder = new Detector.Builder(config.build().getDetectors().get(0));
        builder.setPartitionFieldName("pField");
        config.build().getDetectors().set(0, builder.build());
        config.setInfluencers(Arrays.asList("inf1", "inf2"));
        config.setUsePerPartitionNormalization(true);

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class, () -> config.build());

        assertEquals(Messages.getMessage(Messages.JOB_CONFIG_PER_PARTITION_NORMALIZATION_CANNOT_USE_INFLUENCERS), e.getMessage());
    }

    public void testVerify_GivenCategorizationFiltersContainInvalidRegex() {

        AnalysisConfig.Builder config = createValidConfig();
        config.setCategorizationFieldName("myCategory");
        config.setCategorizationFilters(Arrays.asList("foo", "("));

        IllegalArgumentException e = ESTestCase.expectThrows(IllegalArgumentException.class, () -> config.build());

        assertEquals(Messages.getMessage(Messages.JOB_CONFIG_CATEGORIZATION_FILTERS_CONTAINS_INVALID_REGEX, "("), e.getMessage());
    }

    private static AnalysisConfig.Builder createValidConfig() {
        List<Detector> detectors = new ArrayList<>();
        Detector detector = new Detector.Builder("count", null).build();
        detectors.add(detector);
        AnalysisConfig.Builder analysisConfig = new AnalysisConfig.Builder(detectors);
        analysisConfig.setBucketSpan(TimeValue.timeValueHours(1));
        analysisConfig.setBatchSpan(TimeValue.timeValueHours(2));
        analysisConfig.setLatency(TimeValue.ZERO);
        analysisConfig.setPeriod(0L);
        return analysisConfig;
    }
}
