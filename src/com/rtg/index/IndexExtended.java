/*
 * Copyright (c) 2014. Real Time Genomics Limited.
 *
 * Use of this source code is bound by the Real Time Genomics Limited Software Licence Agreement
 * for Academic Non-commercial Research Purposes only.
 *
 * If you did not receive a license accompanying this file, a copy must first be obtained by email
 * from support@realtimegenomics.com.  On downloading, using and/or continuing to use this source
 * code you accept the terms of that license agreement and any amendments to those terms that may
 * be made from time to time by Real Time Genomics Limited.
 */
package com.rtg.index;

import java.io.IOException;

/**
 * Extends the basic index to allow hashes longer than 64 bits (that is more than one long).
 *
 */
public interface IndexExtended extends Index {
  /**
   * Adds an extended hash code to the index and stores the associated value.
   * The id must be &gt;= 0
   * @param hash the hash key
   * @param value to be associated with the key
   * @throws IllegalStateException if index has been frozen.
   */
  void add(long[] hash, long value) throws IllegalStateException;

  /**
   * Search for the supplied hash code.
   * A call is done to <code>found(id)</code> for each hit where id is the associated
   * id.
   * @param hash the hash
   * @param finder the finder
   * @throws IllegalStateException if index has not been frozen.
   * @throws IOException if the finder produces such an exception.
   */
  void search(long[] hash, Finder finder) throws IOException, IllegalStateException;

  /**
   * Iterate over all entries in this index.
   * A call is done to <code>found(hash, value)</code> for each entry.
   * @param finder the finder
   * @throws IllegalStateException if index has not been frozen.
   * @throws IOException if the finder produces such an exception.
   */
  void scanAll(FinderHashValueExtended finder) throws IOException, IllegalStateException;

  /**
   * Determine whether the index contains the supplied hash code.
   * @param hash the hash
   * @return true iff the index contains the hash.
   * @throws IllegalStateException if index has not been frozen.
   */
  boolean contains(long[] hash) throws IllegalStateException;

  /**
   * Search for the supplied hash code and return the number of hits.
   * @param hash the hash
   * @return the number of hits for the hash (&gt;= 0).
   * @throws IllegalStateException if index has not been frozen.
   */
  int count(long[] hash) throws IllegalStateException;

  /**
   * Find the index of the first occurrence of the hash, or negative if the hash is not in the index.
   * @param hash the hash to find.
   * @return internal location of the hash (if &lt; 0 then not found).
   * @throws IllegalStateException if index has not been frozen.
   */
  long first(long[] hash) throws IllegalStateException;

  /**
   * Get the hash at the found location (see search).
   * @param found internal location of the hash (&gt; 0).
   * @return the hash code.
   */
  long[] getHashExtended(long found);
}


