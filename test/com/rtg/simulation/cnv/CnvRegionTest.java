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
package com.rtg.simulation.cnv;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;

import com.rtg.reader.PrereadType;
import com.rtg.reader.ReaderTestUtils;
import com.rtg.reader.SdfWriter;
import com.rtg.reader.SequencesReader;
import com.rtg.reader.SequencesReaderFactory;
import com.rtg.simulation.CnvFileChecker;
import com.rtg.util.Constants;
import com.rtg.util.InvalidParamsException;
import com.rtg.util.StringUtils;
import com.rtg.util.TestUtils;
import com.rtg.util.diagnostic.Diagnostic;
import com.rtg.util.io.FileUtils;
import com.rtg.util.test.FileHelper;
import com.rtg.util.test.NotRandomRandom;

import junit.framework.TestCase;


/**
 */
public class CnvRegionTest extends TestCase {

  private File mDir;
  private CnvPriorParams mPriors;
  //private NotRandomRandom mRandom;
  private static final String TB = "\t";


  /**
   */
  public CnvRegionTest(String name) {
    super(name);
  }

  public void test() {
    final CnvRegion region = new CnvRegion(0, 40, 100);

    final CnvRegion region2 = new CnvRegion(0, 40, 0);
    region2.mNumCopies = 9;
    assertEquals(0, region2.getCN());

    region.addCopy(region2);
    //System.err.println(region.toString());
    assertEquals("seq: 0 cnved: false start: 40 length: 100 del-1: false del-2: false copies: 0 "
        + "num copies added: 1 num flags added: 1" + StringUtils.LS
        + " heterolist: false",
        region.toString());

    final NotRandomRandom mRandom = new NotRandomRandom();
    final CnvRegion regionPriors = new CnvRegion(0, 40, 100, mPriors);
    assertFalse(regionPriors.isUnpickable(new int[]{1000}, true, -1));
    regionPriors.initializeAsCnved(mRandom);
    assertTrue(regionPriors.isUnpickable(new int[]{1000}, true, -1));
    TestUtils.containsAll(regionPriors.toString(), "copies: 0", "del-1: true", "del-2: true");
    final CnvRegion regionPriors2 = new CnvRegion(0, 40, 100, mPriors);
    regionPriors2.initializeAsCnved(mRandom);
    TestUtils.containsAll(regionPriors2.toString(), "copies: 0", "del-1: true", "del-2: false");
    final CnvRegion regionPriors3 = new CnvRegion(0, 40, 100, mPriors);
    regionPriors3.initializeAsCnved(mRandom);
    TestUtils.containsAll(regionPriors3.toString(), "copies: 1", "del-1: false", "del-2: false");
    final CnvRegion regionPriors6 = new CnvRegion(0, 40, 100, mPriors);
    regionPriors6.initializeAsCnved(mRandom);
    TestUtils.containsAll(regionPriors6.toString(), "copies: 2", "del-1: false", "del-2: false");
    assertEquals(2, CnvRegion.generateNumCopies(mRandom, mPriors, 5));
    assertEquals(4, CnvRegion.generateNumCopies(mRandom, mPriors, 5));
    assertEquals(8, CnvRegion.generateNumCopies(mRandom, mPriors, 5));
    assertEquals(9, CnvRegion.generateNumCopies(mRandom, mPriors, 5));


    //System.err.println(regionPriors.toString());
    final CnvRegion region3 = new CnvRegion(0, 40, 0, 0);
    TestUtils.containsAll(region3.toString(), "del-1: true");
    TestUtils.containsAll(region3.toString(), "del-1: true", "del-2: true");
  }

  public void testGenerate() throws IOException {
    Diagnostic.setLogStream();
    //final File temp = FileHelper.createTempDirectory();
    final String genome = ""
      + ">seq1\n"
      + "AAAAATTTTTGGGGGAAAAATTTTTGGGGGAAAAATTTTTGGGGGAAAAATTTTTGGGGGAAAAA" + StringUtils.LS;
    final File in = ReaderTestUtils.getDNASubDir(genome, mDir);
    try {
      try (SequencesReader dsr = SequencesReaderFactory.createDefaultSequencesReader(in)) {
        final File cnvs = new File(mDir, "test.cnv");
        final File outputDirectory = new File(mDir, "out");
        final File twinDirectory = new File(mDir, "twin");

        final SdfWriter output = new SdfWriter(outputDirectory, Constants.MAX_FILE_SIZE,
          PrereadType.UNKNOWN, false, true, false, dsr.type());
        try {
          final SdfWriter twin = new SdfWriter(twinDirectory, Constants.MAX_FILE_SIZE,
            PrereadType.UNKNOWN, false, true, false, dsr.type());
          try {
            final CnvSimulator cs = new CnvSimulator(dsr, output, twin, new FileOutputStream(cnvs), new NotRandomRandom(), mPriors, 10, Integer.MAX_VALUE);
            cs.generate();
            final String csstr = cs.toString();
            TestUtils.containsAll(csstr, "CnvSimulator", "No. breakpoints ",
              "Sequence 0 No. regions 2", "priors set");
            final File psFile = new File(mDir, "console");
            final FileOutputStream ps = new FileOutputStream(psFile);
            try {
              cs.outputHistograms(ps);
            } finally {
              ps.flush();
              ps.close();
            }
          } finally {
            twin.close();
          }
        } finally {

          output.close();
        }
        final String cnvfilestr = FileUtils.fileToString(cnvs);
        //System.err.println(cnvfilestr);
        TestUtils.containsAll(cnvfilestr, "#Seq" + TB + "start" + TB + "end" + TB + "label" + TB + "cn" + TB + "bp-cn" + TB + "error",
          "seq1" + TB + "0" + TB + "65" + TB + "cnv" + TB + "2" + TB + "0" + TB + "0.0");

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();

        new CnvFileChecker(new PrintStream(bos), new StringReader(cnvfilestr)).check();

        bos.close();

        assertEquals("", bos.toString());

        final int totalLength = CnvSimulatorTest.calculateTotalLength(cnvfilestr);
        final SequencesReader outputReader = SequencesReaderFactory.createDefaultSequencesReader(outputDirectory);
        try {
          final SequencesReader twinReader = SequencesReaderFactory.createDefaultSequencesReader(twinDirectory);
          try {
            assertEquals(totalLength, outputReader.totalLength() + twinReader.totalLength());
          } finally {
            twinReader.close();
          }
        } finally {
          outputReader.close();
        }
      }
    } finally {
    }
  }
  @Override
  public void setUp() throws IOException, InvalidParamsException {
    mDir = FileHelper.createTempDirectory();
    //mRandom = new NotRandomRandom();
    mPriors = CnvPriorParams.builder().cnvpriors("cnv-default").create();
  }

  @Override
  public void tearDown()  {
    assertTrue(FileHelper.deleteAll(mDir));
    mDir = null;
    //mRandom = null;
    mPriors = null;
  }
}
