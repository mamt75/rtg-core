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

package com.rtg.variant.eval;

import static com.rtg.util.StringUtils.LS;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.rtg.tabix.TabixIndexer;
import com.rtg.tabix.UnindexableDataException;
import com.rtg.util.MathUtils;
import com.rtg.util.Pair;
import com.rtg.util.PosteriorUtils;
import com.rtg.util.diagnostic.Diagnostic;
import com.rtg.util.intervals.RangeList;
import com.rtg.util.intervals.ReferenceRanges;
import com.rtg.util.io.FileUtils;
import com.rtg.util.test.FileHelper;
import com.rtg.vcf.header.VcfHeader;

import junit.framework.TestCase;

/**
 */
public class TabixVcfRecordSetTest extends TestCase {

  private static final String CALLS = ""
    + VcfHeader.MINIMAL_HEADER + "\tSAMPLE" + LS
    + "simulatedSequence1\t583\t.\tA\tT\t50\tPASS\t.\tGT:GQ\t1/0:" + PosteriorUtils.phredIfy(18.5 * MathUtils.LOG_10) + LS
    + "simulatedSequence19\t637\t.\tG\tC\t50\tPASS\t.\tGT:GQ\t1/0:" + PosteriorUtils.phredIfy(5.3 * MathUtils.LOG_10) + LS
    + "simulatedSequence45\t737\t.\tG\tC\t50\tPASS\t.\tGT:GQ\t1/1:" + PosteriorUtils.phredIfy(7.4 * MathUtils.LOG_10) + LS;

  public void testSomeMethod() throws IOException, UnindexableDataException {
    Diagnostic.setLogStream();
    final File dir = FileUtils.createTempDir("tabixVarianceTest", "test");
    try {
      final File input = new File(dir, "snp_only.vcf.gz");
      FileHelper.resourceToFile("com/rtg/sam/resources/snp_only.vcf.gz", input);
      final File tabix = new File(dir, "snp_only.vcf.gz.tbi");
      FileHelper.resourceToFile("com/rtg/sam/resources/snp_only.vcf.gz.tbi", tabix);
      final File out = new File(dir, "other.vcf.gz");
      FileHelper.stringToGzFile(CALLS, out);
      new TabixIndexer(out).saveVcfIndex();
      Collection<Pair<String, Integer>> names = new ArrayList<>();
      for (int seq = 1; seq < 32; seq++) {
        names.add(new Pair<>("simulatedSequence" + seq, -1));
      }
      ReferenceRanges<String> ranges = new ReferenceRanges<>(false);
      for (int seq = 1; seq < 32; seq++) {
        ranges.put("simulatedSequence" + seq, new RangeList<>(new RangeList.RangeData<>(-1, Integer.MAX_VALUE, "simulatedSequence" + seq)));
      }
      final VariantSet set = new TabixVcfRecordSet(input, out, ranges, names, null, RocSortValueExtractor.NULL_EXTRACTOR, true, false, 100);

      final Set<String> expected = new HashSet<>();
      for (int seq = 1; seq < 32; seq++) {
        expected.add("simulatedSequence" + seq);
      }
      expected.remove("simulatedSequence12");
      // All other sequences either not contained in reference (N<32), or not in both baseline and calls (45)

      Pair<String, Map<VariantSetType, List<DetectedVariant>>> current;
      while ((current = set.nextSet()) != null) {
        final String currentName = current.getA();
        assertTrue("unexpected sequence <" + currentName + ">", expected.contains(currentName));
        expected.remove(currentName);
        if (currentName.equals("simulatedSequence19")) {
          assertEquals(1, current.getB().get(VariantSetType.CALLS).size());
          assertEquals(6, current.getB().get(VariantSetType.BASELINE).size());
        }
        if (currentName.equals("simulatedSequence45")) {
          assertEquals(0, current.getB().get(VariantSetType.BASELINE).size());
          assertEquals(1, current.getB().get(VariantSetType.CALLS).size());
        }
      }
      assertTrue("these sequences weren't used: " + expected, expected.isEmpty());
    } finally {
      FileHelper.deleteAll(dir);
    }
  }
}
