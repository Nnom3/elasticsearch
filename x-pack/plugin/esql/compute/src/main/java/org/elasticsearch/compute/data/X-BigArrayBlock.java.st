/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0; you may not use this file except in compliance with the Elastic License
 * 2.0.
 */

package org.elasticsearch.compute.data;

import org.apache.lucene.util.RamUsageEstimator;
import org.elasticsearch.common.util.$Array$;
import org.elasticsearch.core.Releasables;

import java.util.BitSet;

/**
 * Block implementation that stores values in a {@link $Type$BigArrayVector}. Does not take ownership of the given
 * {@link $Array$} and does not adjust circuit breakers to account for it.
 * This class is generated. Do not edit it.
 */
public final class $Type$BigArrayBlock extends AbstractArrayBlock implements $Type$Block {

    private static final long BASE_RAM_BYTES_USED = 0; // TODO: fix this
    private final $Type$BigArrayVector vector;

    public $Type$BigArrayBlock(
        $Array$ values,
        int positionCount,
        int[] firstValueIndexes,
        BitSet nulls,
        MvOrdering mvOrdering,
        BlockFactory blockFactory
    ) {
        this(
            new $Type$BigArrayVector(values, firstValueIndexes == null ? positionCount : firstValueIndexes[positionCount], blockFactory),
            positionCount,
            firstValueIndexes,
            nulls,
            mvOrdering,
            blockFactory
        );
    }

    private $Type$BigArrayBlock(
        $Type$BigArrayVector vector,
        int positionCount,
        int[] firstValueIndexes,
        BitSet nulls,
        MvOrdering mvOrdering,
        BlockFactory blockFactory
    ) {
        super(positionCount, firstValueIndexes, nulls, mvOrdering, blockFactory);
        this.vector = vector;
        assert firstValueIndexes == null
            ? vector.getPositionCount() == getPositionCount()
            : firstValueIndexes[getPositionCount()] == vector.getPositionCount();
    }

    @Override
    public $Type$Vector asVector() {
        return null;
    }

    @Override
    public $type$ get$Type$(int valueIndex) {
        return vector.get$Type$(valueIndex);
    }

    @Override
    public $Type$Block filter(int... positions) {
        // TODO use reference counting to share the vector
        try (var builder = blockFactory().new$Type$BlockBuilder(positions.length)) {
            for (int pos : positions) {
                if (isNull(pos)) {
                    builder.appendNull();
                    continue;
                }
                int valueCount = getValueCount(pos);
                int first = getFirstValueIndex(pos);
                if (valueCount == 1) {
                    builder.append$Type$(get$Type$(getFirstValueIndex(pos)$if(BytesRef)$, scratch$endif$));
                } else {
                    builder.beginPositionEntry();
                    for (int c = 0; c < valueCount; c++) {
                        builder.append$Type$(get$Type$(first + c$if(BytesRef)$, scratch$endif$));
                    }
                    builder.endPositionEntry();
                }
            }
            return builder.mvOrdering(mvOrdering()).build();
        }
    }

    @Override
    public ElementType elementType() {
        return ElementType.$TYPE$;
    }

    @Override
    public $Type$Block expand() {
        if (firstValueIndexes == null) {
            incRef();
            return this;
        }
        if (nullsMask == null) {
            vector.incRef();
            return vector.asBlock();
        }

        // The following line is correct because positions with multi-values are never null.
        int expandedPositionCount = vector.getPositionCount();
        long bitSetRamUsedEstimate = Math.max(nullsMask.size(), BlockRamUsageEstimator.sizeOfBitSet(expandedPositionCount));
        blockFactory().adjustBreaker(bitSetRamUsedEstimate);

        $Type$BigArrayBlock expanded = new $Type$BigArrayBlock(
            vector,
            expandedPositionCount,
            null,
            shiftNullsToExpandedPositions(),
            MvOrdering.DEDUPLICATED_AND_SORTED_ASCENDING,
            blockFactory()
        );
        blockFactory().adjustBreaker(expanded.ramBytesUsedOnlyBlock() - bitSetRamUsedEstimate);
        // We need to incRef after adjusting any breakers, otherwise we might leak the vector if the breaker trips.
        vector.incRef();
        return expanded;
    }

    private long ramBytesUsedOnlyBlock() {
        return BASE_RAM_BYTES_USED + BlockRamUsageEstimator.sizeOf(firstValueIndexes) + BlockRamUsageEstimator.sizeOfBitSet(nullsMask);
    }

    @Override
    public long ramBytesUsed() {
        return ramBytesUsedOnlyBlock() + RamUsageEstimator.sizeOf(vector);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof $Type$Block that) {
            return $Type$Block.equals(this, that);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return $Type$Block.hash(this);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName()
            + "[positions="
            + getPositionCount()
            + ", mvOrdering="
            + mvOrdering()
            + ", ramBytesUsed="
            + vector.ramBytesUsed()
            + ']';
    }

    @Override
    public void allowPassingToDifferentDriver() {
        super.allowPassingToDifferentDriver();
        vector.allowPassingToDifferentDriver();
    }

    @Override
    public void closeInternal() {
        blockFactory().adjustBreaker(-ramBytesUsedOnlyBlock());
        Releasables.closeExpectNoException(vector);
    }
}
