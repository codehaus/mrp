/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.mmtk.utility.heap;

import org.mmtk.plan.Plan;
import org.mmtk.policy.Space;
import static org.mmtk.policy.Space.PAGES_IN_CHUNK;
import org.mmtk.utility.alloc.EmbeddedMetaData;
import org.mmtk.utility.Conversions;
import org.mmtk.utility.GenericFreeList;
import org.mmtk.vm.VM;
import org.mmtk.utility.Constants;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class manages the allocation of pages for a space.  When a
 * page is requested by the space both a page budget and the use of
 * virtual address space are checked.  If the request for space can't
 * be satisfied (for either reason) a GC may be triggered.<p>
 */
@Uninterruptible
public final class FreeListPageResource extends PageResource implements Constants {

  private final GenericFreeList freeList;
  private int highWaterMark = 0;
  private final int metaDataPagesPerRegion;
  private int pagesCurrentlyOnFreeList = 0;

  /**
   * Constructor
   *
   * Contiguous free list resource. The address range is pre-defined at
   * initialization time and is immutable.
   *
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   * @param start The start of the address range allocated to this resource
   * @param bytes The size of the address rage allocated to this resource
   */
  public FreeListPageResource(int pageBudget, Space space, Address start,
      Extent bytes) {
    super(pageBudget, space, start);
    int pages = Conversions.bytesToPages(bytes);
    freeList = new GenericFreeList(pages);
    pagesCurrentlyOnFreeList = pages;
    this.metaDataPagesPerRegion = 0;
  }

  /**
   * Constructor
   *
   * Contiguous free list resource. The address range is pre-defined at
   * initialization time and is immutable.
   *
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   * @param start The start of the address range allocated to this resource
   * @param bytes The size of the address rage allocated to this resource
   * @param metaDataPagesPerRegion The number of pages of meta data
   * that are embedded in each region.
   */
  public FreeListPageResource(int pageBudget, Space space, Address start,
      Extent bytes, int metaDataPagesPerRegion) {
    super(pageBudget, space, start);
    this.metaDataPagesPerRegion = metaDataPagesPerRegion;
    int pages = Conversions.bytesToPages(bytes);
    freeList = new GenericFreeList(pages, EmbeddedMetaData.PAGES_IN_REGION);
    pagesCurrentlyOnFreeList = pages;
    reserveMetaData(space.getExtent());
  }

  /**
   * Constructor
   *
   * Discontiguous monotone resource. The address range is <i>not</i>
   * pre-defined at initialization time and is dynamically defined to
   * be some set of pages, according to demand and availability.
   *
   * @param pageBudget The budget of pages available to this memory
   * manager before it must poll the collector.
   * @param space The space to which this resource is attached
   */
  public FreeListPageResource(int pageBudget, Space space, int metaDataPagesPerRegion) {
    super(pageBudget, space);
    this.metaDataPagesPerRegion = metaDataPagesPerRegion;
    this.start = Space.AVAILABLE_START;
    freeList = new GenericFreeList(Map.globalPageMap, Map.getDiscontigFreeListPROrdinal(this));
    pagesCurrentlyOnFreeList = 0;
  }

  /**
   * Return the number of available physical pages for this resource.
   * This includes all pages currently free on the resource's free list.
   * If the resource is using discontiguous space it also includes
   * currently unassigned discontiguous space.<p>
   *
   * Note: This just considers physical pages (ie virtual memory pages
   * allocated for use by this resource). This calculation is orthogonal
   * to and does not consider any restrictions on the number of pages
   * this resource may actually use at any time (ie the number of
   * committed and reserved pages).<p>
   *
   * Note: The calculation is made on the assumption that all space that
   * could be assigned to this resource would be assigned to this resource
   * (ie the unused discontiguous space could just as likely be assigned
   * to another competing resource).
   *
   * @return The number of available physical pages for this resource.
   */
  @Override
  public int getAvailablePhysicalPages() {
    int rtn = pagesCurrentlyOnFreeList;
    if (!contiguous)
      rtn += Map.getAvailableDiscontiguousChunks()*(Space.PAGES_IN_CHUNK-metaDataPagesPerRegion);
    return rtn;
  }

  /**
   * Allocate <code>pages</code> pages from this resource.<p>
   *
   * If the request can be satisfied, then ensure the pages are
   * mmpapped and zeroed before returning the address of the start of
   * the region.  If the request cannot be satisfied, return zero.
   *
   * @param pages The number of pages to be allocated.
   * @return The start of the first page if successful, zero on
   * failure.
   */
  @Inline
  protected Address allocPages(int pages) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(metaDataPagesPerRegion == 0 || pages <= PAGES_IN_CHUNK - metaDataPagesPerRegion);
    lock();
    int pageOffset = freeList.alloc(pages);
    if (pageOffset == GenericFreeList.FAILURE && !contiguous)
      pageOffset = allocateContiguousChunks(pages);
    if (pageOffset == -1) {
      unlock();
      return Address.zero();
    } else {
      pagesCurrentlyOnFreeList -= pages;
      if (pageOffset > highWaterMark) {
        if ((pageOffset ^ highWaterMark) > EmbeddedMetaData.PAGES_IN_REGION) {
          int regions = 1 + ((pageOffset - highWaterMark) >> EmbeddedMetaData.LOG_PAGES_IN_REGION);
          int metapages = regions * metaDataPagesPerRegion;
          reserved += metapages;
          committed += metapages;
        }
        highWaterMark = pageOffset;
      }
      Address rtn = start.plus(Conversions.pagesToBytes(pageOffset));
      Mmapper.ensureMapped(rtn, pages);
      VM.memory.zero(rtn, Conversions.pagesToBytes(pages));
      commitPages(pages, pages);
      unlock();
      return rtn;
    }
  }

  /**
   * Release a group of pages, associated with this page resource,
   * that were allocated together, optionally zeroing on release and
   * optionally memory protecting on release.
   *
   * @param first The first page in the group of pages that were
   * allocated together.
   */
  @Inline
  public void releasePages(Address first) {
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(Conversions.isPageAligned(first));

    int pageOffset = Conversions.bytesToPages(first.diff(start));

    int pages = freeList.size(pageOffset);
    if (ZERO_ON_RELEASE)
      VM.memory.zero(first, Conversions.pagesToBytes(pages));
    /* Can't use protect here because of the chunk sizes involved!
    if (protectOnRelease.getValue())
      LazyMmapper.protect(first, pages);
     */
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(pages <= committed);

    lock();
    reserved -= pages;
    committed -= pages;
    int freed = freeList.free(pageOffset, true);
    pagesCurrentlyOnFreeList += pages;
    boolean completelyFreed = !contiguous && metaDataPagesPerRegion > 0 && freed == (PAGES_IN_CHUNK - metaDataPagesPerRegion);
    if (!contiguous && (freed % PAGES_IN_CHUNK == 0)) {
      /* maybe we've freed this entire chunk.  let's check... */
      int regionStart = pageOffset & ~(PAGES_IN_CHUNK - 1);
      int nextRegionStart = regionStart + PAGES_IN_CHUNK;
      while (regionStart >= 0 && freeList.isCoalescable(regionStart))
        regionStart -= PAGES_IN_CHUNK;
      while (nextRegionStart < GenericFreeList.MAX_UNITS && freeList.isCoalescable(nextRegionStart))
        nextRegionStart += PAGES_IN_CHUNK;
       if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(regionStart >= 0 && nextRegionStart < GenericFreeList.MAX_UNITS);
      completelyFreed = (freed == nextRegionStart - regionStart);
    }
    if (completelyFreed)
      freeContiguousChunk(Space.chunkAlign(first, true));
    unlock();
  }

  /**
   * Allocate sufficient contiguous chunks within a discontiguous region to
   * satisfy the pending request.  Note that this is purely about address space
   * allocation within a discontiguous region.  This method does not reserve
   * individual pages, it merely assigns a suitably large region of virtual
   * memory from within the discontiguous region for use by a particular space.
   *
   * @param pages The number of pages currently being requested
   * @return A chunk number or GenericFreelist.FAILURE
   */
  private int allocateContiguousChunks(int pages) {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(metaDataPagesPerRegion == 0 || pages <= PAGES_IN_CHUNK - metaDataPagesPerRegion);
    int rtn = GenericFreeList.FAILURE;
    int requiredChunks = Space.requiredChunks(pages);
    Address region = space.growDiscontiguousSpace(requiredChunks);
    if (!region.isZero()) {
      int regionStart = Conversions.bytesToPages(region.diff(start));
      int regionEnd = regionStart + (requiredChunks*Space.PAGES_IN_CHUNK) - 1;
      freeList.setUncoalescable(regionStart);
      freeList.setUncoalescable(regionEnd + 1);
      for (int p = regionStart; p < regionEnd; p += Space.PAGES_IN_CHUNK) {
        int liberated;
        if (p != regionStart)
          freeList.clearUncoalescable(p);
        liberated = freeList.free(p, true); // add chunk to our free list
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(liberated == Space.PAGES_IN_CHUNK + (p - regionStart));
        if (metaDataPagesPerRegion > 1)
          freeList.alloc(metaDataPagesPerRegion, p); // carve out space for metadata
        pagesCurrentlyOnFreeList += Space.PAGES_IN_CHUNK - metaDataPagesPerRegion;
      }
      rtn = freeList.alloc(pages); // re-do the request which triggered this call
    }
    return rtn;
  }

  /**
   * Release a single chunk from a discontiguous region.  All this does is
   * release a chunk from the virtual address space associated with this
   * discontiguous space.
   *
   * @param chunk The chunk to be freed
   */
  private void freeContiguousChunk(Address chunk) {
    int numChunks = Map.getContiguousRegionChunks(chunk);
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(numChunks == 1 || metaDataPagesPerRegion == 0);

    /* nail down all pages associated with the chunk, so it is no longer on our free list */
    int chunkStart = Conversions.bytesToPages(chunk.diff(start));
    int chunkEnd = chunkStart + (numChunks*Space.PAGES_IN_CHUNK);
    while (chunkStart < chunkEnd) {
      freeList.setUncoalescable(chunkStart);
      if (metaDataPagesPerRegion > 0)
        freeList.free(chunkStart);  // first free any metadata pages
      int tmp = freeList.alloc(Space.PAGES_IN_CHUNK, chunkStart); // then alloc the entire chunk
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(tmp == chunkStart);
      chunkStart += Space.PAGES_IN_CHUNK;
      pagesCurrentlyOnFreeList -= (Space.PAGES_IN_CHUNK - metaDataPagesPerRegion);
    }
    /* now return the address space associated with the chunk for global reuse */
    space.releaseDiscontiguousChunks(chunk);
  }

  /**
   * Reserve virtual address space for meta-data.
   *
   * @param extent The size of this space
   */
  private void reserveMetaData(Extent extent) {
    highWaterMark = 0;
    if (metaDataPagesPerRegion > 0) {
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(start.toWord().rshl(EmbeddedMetaData.LOG_BYTES_IN_REGION).lsh(EmbeddedMetaData.LOG_BYTES_IN_REGION).toAddress().EQ(start));
      Extent size = extent.toWord().rshl(EmbeddedMetaData.LOG_BYTES_IN_REGION).lsh(EmbeddedMetaData.LOG_BYTES_IN_REGION).toExtent();
      Address cursor = start.plus(size);
      while (cursor.GT(start)) {
        cursor = cursor.minus(EmbeddedMetaData.BYTES_IN_REGION);
        int unit = cursor.diff(start).toWord().rshl(LOG_BYTES_IN_PAGE).toInt();
        int tmp = freeList.alloc(metaDataPagesPerRegion, unit);
        pagesCurrentlyOnFreeList -= metaDataPagesPerRegion;
        if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(tmp == unit);
      }
    }
  }

  /**
   * Adjust a page request to include metadata requirements, if any.  In the
   * case of a free-list allocator, meta-data is pre-allocated, so simply
   * return the un-adjusted request size.
   *
   * @param pages The size of the pending allocation in pages
   * @return The (unadjusted) request size, since metadata is pre-allocated
   */
  public int adjustForMetaData(int pages) { return pages; }

  public Address getHighWater() {
    return start.plus(Extent.fromIntSignExtend(highWaterMark<<LOG_BYTES_IN_PAGE));
  }

  /**
   * Return the size of the super page
   *
   * @param first the Address of the first word in the superpage
   * @return the size in bytes
   */
  @Inline
  public Extent getSize(Address first) {
    if (VM.VERIFY_ASSERTIONS)
      VM.assertions._assert(Conversions.isPageAligned(first));

    int pageOffset = Conversions.bytesToPages(first.diff(start));
    int pages = freeList.size(pageOffset);
    return Conversions.pagesToBytes(pages);
  }

  /**
   * Resize the free list associated with this resource.  This method is
   * called to re-set the free list once the global free list (which it shares)
   * is finalized.  There's a circular dependency, so we need an explicit
   * call-back to reset the free list size.
   */
  @Interruptible
  public void resizeFreeList() {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(!contiguous && !Plan.isInitialized());
    freeList.resizeFreeList();
  }
}
