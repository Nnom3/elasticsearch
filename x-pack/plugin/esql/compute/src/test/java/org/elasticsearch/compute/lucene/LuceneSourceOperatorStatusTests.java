/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.lucene;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.elasticsearch.common.Strings;
import org.elasticsearch.common.io.stream.Writeable;
import org.elasticsearch.test.AbstractWireSerializingTestCase;
import org.elasticsearch.test.ESTestCase;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.hamcrest.Matchers.equalTo;

@LuceneTestCase.AwaitsFix(bugUrl = "https://github.com/elastic/elasticsearch/issues/103774")
public class LuceneSourceOperatorStatusTests extends AbstractWireSerializingTestCase<LuceneSourceOperator.Status> {
    public static LuceneSourceOperator.Status simple() {
        return new LuceneSourceOperator.Status(2, Set.of("*:*"), new TreeSet<>(List.of("a:0", "a:1")), 0, 1, 5, 123, 99990, 8000);
    }

    public static String simpleToJson() {
        return """
            {
              "processed_slices" : 2,
              "processed_queries" : [
                "*:*"
              ],
              "processed_shards" : [
                "a:0",
                "a:1"
              ],
              "slice_index" : 0,
              "total_slices" : 1,
              "pages_emitted" : 5,
              "slice_min" : 123,
              "slice_max" : 99990,
              "current" : 8000
            }""";
    }

    public void testToXContent() {
        assertThat(Strings.toString(simple(), true, true), equalTo(simpleToJson()));
    }

    @Override
    protected Writeable.Reader<LuceneSourceOperator.Status> instanceReader() {
        return LuceneSourceOperator.Status::new;
    }

    @Override
    public LuceneSourceOperator.Status createTestInstance() {
        return new LuceneSourceOperator.Status(
            randomNonNegativeInt(),
            randomProcessedQueries(),
            randomProcessedShards(),
            randomNonNegativeInt(),
            randomNonNegativeInt(),
            randomNonNegativeInt(),
            randomNonNegativeInt(),
            randomNonNegativeInt(),
            randomNonNegativeInt()
        );
    }

    private static Set<String> randomProcessedQueries() {
        int size = between(0, 10);
        Set<String> set = new TreeSet<>();
        while (set.size() < size) {
            set.add(randomAlphaOfLength(5));
        }
        return set;
    }

    private static Set<String> randomProcessedShards() {
        int size = between(0, 10);
        Set<String> set = new TreeSet<>();
        while (set.size() < size) {
            set.add(randomAlphaOfLength(3) + ":" + between(0, 10));
        }
        return set;
    }

    @Override
    protected LuceneSourceOperator.Status mutateInstance(LuceneSourceOperator.Status instance) {
        int processedSlices = instance.processedSlices();
        Set<String> processedQueries = instance.processedQueries();
        Set<String> processedShards = instance.processedShards();
        int sliceIndex = instance.sliceIndex();
        int totalSlices = instance.totalSlices();
        int pagesEmitted = instance.pagesEmitted();
        int sliceMin = instance.sliceMin();
        int sliceMax = instance.sliceMax();
        int current = instance.current();
        switch (between(0, 8)) {
            case 0 -> processedSlices = randomValueOtherThan(processedSlices, ESTestCase::randomNonNegativeInt);
            case 1 -> processedQueries = randomValueOtherThan(processedQueries, LuceneSourceOperatorStatusTests::randomProcessedQueries);
            case 2 -> processedQueries = randomValueOtherThan(processedShards, LuceneSourceOperatorStatusTests::randomProcessedShards);
            case 3 -> sliceIndex = randomValueOtherThan(sliceIndex, ESTestCase::randomNonNegativeInt);
            case 4 -> totalSlices = randomValueOtherThan(totalSlices, ESTestCase::randomNonNegativeInt);
            case 5 -> pagesEmitted = randomValueOtherThan(pagesEmitted, ESTestCase::randomNonNegativeInt);
            case 6 -> sliceMin = randomValueOtherThan(sliceMin, ESTestCase::randomNonNegativeInt);
            case 7 -> sliceMax = randomValueOtherThan(sliceMax, ESTestCase::randomNonNegativeInt);
            case 8 -> current = randomValueOtherThan(current, ESTestCase::randomNonNegativeInt);
            default -> throw new UnsupportedOperationException();
        }
        ;
        return new LuceneSourceOperator.Status(
            processedSlices,
            processedQueries,
            processedShards,
            sliceIndex,
            totalSlices,
            pagesEmitted,
            sliceMin,
            sliceMax,
            current
        );
    }
}
