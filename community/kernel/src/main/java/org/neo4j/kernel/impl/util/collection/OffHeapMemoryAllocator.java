/*
 * Copyright (c) 2002-2018 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.neo4j.kernel.impl.util.collection;

import java.nio.ByteBuffer;

import org.neo4j.collection.offheap.Memory;
import org.neo4j.collection.offheap.MemoryAllocator;
import org.neo4j.kernel.impl.util.collection.OffHeapBlockAllocator.MemoryBlock;
import org.neo4j.memory.MemoryAllocationTracker;
import org.neo4j.unsafe.impl.internal.dragons.UnsafeUtil;

import static java.util.Objects.requireNonNull;
import static org.neo4j.unsafe.impl.internal.dragons.UnsafeUtil.copyMemory;
import static org.neo4j.unsafe.impl.internal.dragons.UnsafeUtil.getLong;
import static org.neo4j.unsafe.impl.internal.dragons.UnsafeUtil.putLong;
import static org.neo4j.unsafe.impl.internal.dragons.UnsafeUtil.setMemory;
import static org.neo4j.util.Preconditions.checkState;

public class OffHeapMemoryAllocator implements MemoryAllocator
{
    private final MemoryAllocationTracker tracker;
    private final OffHeapBlockAllocator blockAllocator;

    public OffHeapMemoryAllocator( MemoryAllocationTracker tracker, OffHeapBlockAllocator blockAllocator )
    {
        this.tracker = requireNonNull( tracker );
        this.blockAllocator = requireNonNull( blockAllocator );
    }

    @Override
    public Memory allocate( long size )
    {
        final MemoryBlock block = blockAllocator.allocate( size, tracker );
        return new OffHeapMemory( block );
    }

    class OffHeapMemory implements Memory
    {
        final MemoryBlock block;

        OffHeapMemory( MemoryBlock block )
        {
            this.block = block;
        }

        @Override
        public long readLong( long offset )
        {
            return getLong( block.addr + offset );
        }

        @Override
        public void writeLong( long offset, long value )
        {
            putLong( block.addr + offset, value );
        }

        @Override
        public void clear()
        {
            setMemory( block.addr, block.size, (byte) 0 );
        }

        @Override
        public long size()
        {
            return block.size;
        }

        @Override
        public void free()
        {
            blockAllocator.free( block, tracker );
        }

        @Override
        public Memory copy()
        {
            final MemoryBlock copy = blockAllocator.allocate( block.size, tracker );
            copyMemory( block.addr, copy.addr, block.size );
            return new OffHeapMemory( copy );
        }

        @Override
        public ByteBuffer asByteBuffer()
        {
            checkState( block.size <= Integer.MAX_VALUE, "Can't create ByteBuffer: memory size exceeds integer limit" );
            try
            {
                return UnsafeUtil.newDirectByteBuffer( block.addr, (int) block.size );
            }
            catch ( Exception e )
            {
                throw new RuntimeException( e );
            }
        }
    }
}