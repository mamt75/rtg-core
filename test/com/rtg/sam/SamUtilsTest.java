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
package com.rtg.sam;

import static com.rtg.util.StringUtils.LS;
import static com.rtg.util.StringUtils.TAB;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import com.rtg.launcher.GlobalFlags;
import com.rtg.ngs.CgMapCli;
import com.rtg.reader.PrereadArm;
import com.rtg.reader.ReaderLongMock;
import com.rtg.reader.ReaderTestUtils;
import com.rtg.reader.SdfId;
import com.rtg.reader.SequencesReader;
import com.rtg.util.TestUtils;
import com.rtg.util.diagnostic.Diagnostic;
import com.rtg.util.diagnostic.NoTalkbackSlimException;
import com.rtg.util.io.FileUtils;
import com.rtg.util.io.MemoryPrintStream;
import com.rtg.util.io.TestDirectory;
import com.rtg.util.test.FileHelper;

import htsjdk.samtools.Cigar;
import htsjdk.samtools.CigarElement;
import htsjdk.samtools.CigarOperator;
import htsjdk.samtools.SAMFileHeader;
import htsjdk.samtools.SAMFileReader;
import htsjdk.samtools.SAMProgramRecord;
import htsjdk.samtools.SAMRecord;

import junit.framework.TestCase;

/**
 * Tests for {@link SamUtils}
 */
public class SamUtilsTest extends TestCase {

  public SamUtilsTest(final String name) {
    super(name);
  }

  @Override
  public void setUp() {
    GlobalFlags.resetAccessedStatus();
  }

  private static final String NL = "\n";  // SAM files always use \n.
  private static final String SAM1 = ""
    + "0" + TAB + "0" + TAB + "g1" + TAB +  "3" + TAB + "155" + TAB + "6M1D1M" + TAB + "*" + TAB + "0" + TAB + "0" + TAB + "ATCGACG" + TAB + "```````" + TAB + "AS:i:0" + NL
    + "1" + TAB + "0" + TAB + "g1" + TAB +  "3" + TAB + "155" + TAB + "6M1D1M" + TAB + "*" + TAB + "0" + TAB + "0" + TAB + "ATCGACG" + TAB + "```````" + TAB + "AS:i:0" + NL
    + "2" + TAB + "0" + TAB + "g1" + TAB +  "5" + TAB + "155" + TAB + "3M1D4M" + TAB + "*" + TAB + "0" + TAB + "0" + TAB + "GACGCTC" + TAB + "```````" + TAB + "AS:i:1" + NL
    + "4" + TAB + "0" + TAB + "g1" + TAB +  "6" + TAB + "155" + TAB + "3M1D4M" + TAB + "*" + TAB + "0" + TAB + "0" + TAB + "GACGCTC" + TAB + "```````" + TAB + "AS:i:1" + NL
    + "5" + TAB + "0" + TAB + "g1" + TAB + "11" + TAB + "155" + TAB + "8M" + TAB + "*" + TAB + "0" + TAB + "0" + TAB + "TTCAGCTA" + TAB + "````````" + TAB + "AS:i:1" + NL
    ;
  private static final String SAM_HEADER = ""
    + "@HD" + TAB + "VN:1.0" + TAB + "SO:coordinate" + NL
    + "@SQ" + TAB + "SN:g1" + TAB + "LN:20" + NL
    ;

  public void testCatFiles() throws IOException {
    final File main = FileUtils.createTempDir("samtests", "cat");
    Diagnostic.setLogStream();
    try {
      final ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try {
        final File one = new File(main, "0.sam");
        final File two = new File(main, "1.sam");
        FileUtils.stringToFile(SAM_HEADER + SAM1, one);
        FileUtils.stringToFile(SAM_HEADER + SAM1, two);
        SamUtils.samCat(bos, one, two);
        assertTrue(one.exists());
        assertTrue(two.exists());
        bos.flush();
      } finally {
        bos.close();
      }
      assertEquals(SAM_HEADER + SAM1 + SAM1, bos.toString());

    } finally {
      assertTrue(FileHelper.deleteAll(main));
    }
  }

  public void testCatFilesDeleteIntermediate() throws IOException {
    final File main = FileUtils.createTempDir("samtests", "cat");
    Diagnostic.setLogStream();
    try {
      final ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try {
        final File one = new File(main, "0.sam");
        final File two = new File(main, "1.sam");
        FileUtils.stringToFile(SAM_HEADER + SAM1, one);
        FileUtils.stringToFile(SAM_HEADER + SAM1, two);
        SamUtils.samCat(false, bos, true, one, two);
        bos.flush();
        assertFalse(one.exists());
        assertFalse(two.exists());
      } finally {
        bos.close();
      }
      assertEquals(SAM_HEADER + SAM1 + SAM1, bos.toString());

    } finally {
      assertTrue(FileHelper.deleteAll(main));
    }
  }

  public void testCatFilesGZ() throws IOException {
    final File main = FileUtils.createTempDir("samtests", "cat");
    Diagnostic.setLogStream();
    try {
      final ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try {
        final File one = new File(main, "0.gz");
        final File two = new File(main, "1.gz");
        FileHelper.stringToGzFile(SAM_HEADER + SAM1, one);
        FileHelper.stringToGzFile(SAM_HEADER + SAM1, two);
        SamUtils.samCat(bos, one, two);
        assertTrue(one.exists());
        assertTrue(two.exists());
        bos.flush();
      } finally {
        bos.close();
      }
      assertEquals(SAM_HEADER + SAM1 + SAM1, bos.toString());

    } finally {
      assertTrue(FileHelper.deleteAll(main));
    }
  }

  public void testCatFilesGZDeleteIntermediate() throws IOException {
    final File main = FileUtils.createTempDir("samtests", "cat");
    Diagnostic.setLogStream();
    try {
      final ByteArrayOutputStream bos = new ByteArrayOutputStream();
      try {
        final File one = new File(main, "0.gz");
        final File two = new File(main, "1.gz");
        FileHelper.stringToGzFile(SAM_HEADER + SAM1, one);
        FileHelper.stringToGzFile(SAM_HEADER + SAM1, two);
        SamUtils.samCat(true, bos, true, one, two);
        bos.flush();
        assertFalse(one.exists());
        assertFalse(two.exists());
      } finally {
        bos.close();
      }
      assertEquals(SAM_HEADER + SAM1 + SAM1, bos.toString());

    } finally {
      assertTrue(FileHelper.deleteAll(main));
    }
  }

  public void testGuid() {
    Diagnostic.setLogStream();
    try {
      final SAMFileHeader sf = new SAMFileHeader();
      sf.addComment("READ-SDF-ID:" + Long.toHexString(123456));
      SamUtils.checkReadsGuid(sf, new SdfId(123456)); //this should work

      try {
        SamUtils.checkReadsGuid(sf, new SdfId(12));
      } catch (final NoTalkbackSlimException ex) {
        assertEquals("SDF-ID of given SDF does not match SDF used during mapping.", ex.getMessage());
      }

      final SAMFileHeader sf2 = new SAMFileHeader();
      sf2.addComment("READ-SDF-ID:$$$$$$$");
      try {
        SamUtils.checkReadsGuid(sf2, new SdfId(123456));
        fail();
      } catch (final NoTalkbackSlimException ex) {
        assertTrue(ex.getMessage(), ex.getMessage().contains("Malformed READ-SDF-ID: attribute from SAM header : '$$$$$$$'."));
      }

      final MemoryPrintStream mps = new MemoryPrintStream();
      Diagnostic.setLogStream(mps.printStream());
      final SAMFileHeader sf3 = new SAMFileHeader();
      SamUtils.checkReadsGuid(sf3, new SdfId(123456));
      assertTrue(mps.toString().contains("No READ-SDF-ID found in SAM header, unable to verify read-id correctness."));

      final SAMFileHeader sft = new SAMFileHeader();
      sft.addComment("TEMPLATE-SDF-ID:" + Long.toHexString(123456));
      SamUtils.checkReferenceGuid(sft, new SdfId(123456)); //this should work

      try {
        SamUtils.checkReferenceGuid(sft, new SdfId(12));
      } catch (final NoTalkbackSlimException ex) {
        assertEquals("TEMPLATE-SDF-ID of given file does not match reference SDF-ID used during mapping.", ex.getMessage());
      }

      final SAMFileHeader sft2 = new SAMFileHeader();
      sft2.addComment("TEMPLATE-SDF-ID:$$$$$$$");
      try {
        SamUtils.checkReferenceGuid(sft2, new SdfId(123456));
        fail();
      } catch (final NoTalkbackSlimException ex) {
        assertTrue(ex.getMessage(), ex.getMessage().contains("Malformed TEMPLATE-SDF-ID: attribute from SAM header : '$$$$$$$'."));
      }

      final MemoryPrintStream mpst = new MemoryPrintStream();
      Diagnostic.setLogStream(mpst.printStream());
      final SAMFileHeader sft3 = new SAMFileHeader();
      SamUtils.checkReferenceGuid(sft3, new SdfId(123456));
      assertTrue(mpst.toString().contains("No TEMPLATE-SDF-ID found in SAM header, unable to verify reference correctness."));
    } finally {
      Diagnostic.setLogStream();
    }
  }

  public void testRunCommentUpdate() {
    final SAMFileHeader sf = new SAMFileHeader();
    sf.addComment("READ-SDF-ID:" + Long.toHexString(123456));
    final UUID uuid = UUID.randomUUID();
    sf.addComment(SamUtils.RUN_ID_ATTRIBUTE + uuid);
    sf.addComment("TEMPLATE-SDF-ID:$$$$$$$");
    sf.addComment("BLAH:KLJERLWJ");

    SamUtils.updateRunId(sf);

    for (final String s : sf.getComments()) {
      if (s.startsWith(SamUtils.RUN_ID_ATTRIBUTE)) {
        assertFalse(s.contains(uuid.toString()));
      }
    }
  }



  private static final String SAM_HEADER_OLD_PG = ""
    + "@HD" + TAB + "VN:1.0" + TAB + "SO:coordinate" + NL
    + "@SQ" + TAB + "SN:g1" + TAB + "LN:20" + NL
    + "@PG" + TAB + "ID:rtg" + TAB + "CL:map --output mappings_pe_svdel --template template.sdf --input reads_pe_svdel.sdf" + NL
    + "@PG" + TAB + "ID:rtg" + TAB + "CL:svprep -i mappings_pe_svdel" + NL
    + "@PG" + TAB + "ID:rtg" + TAB + "CL:svprep2 -i mappings_pe_svdel" + NL
    ;

  public void testPGMigration() {
    final ByteArrayInputStream bis = new ByteArrayInputStream(SAM_HEADER_OLD_PG.getBytes());
    final SAMFileReader reader = new SAMFileReader(bis);
    SAMProgramRecord prev = null;
    for (final SAMProgramRecord r : reader.getFileHeader().getProgramRecords()) {
      assertEquals(r.getProgramName(), "rtg");
      assertTrue(prev == null || (prev.getId().equals(r.getPreviousProgramGroupId())));
      prev = r;
    }
  }


  public void testIsBam() throws Exception {
    final File dir = FileHelper.createTempDirectory();
    try {
      final File file3 = FileHelper.resourceToFile("com/rtg/sam/resources/bam.bam", new File(dir, "bam3.bam"));
      final File file4 = FileHelper.resourceToFile("com/rtg/sam/resources/unmated.sam", new File(dir, "test4.sam"));

      assertTrue(SamUtils.isBAMFile(file3));
      assertFalse(SamUtils.isBAMFile(file4));
    } finally {
      FileHelper.deleteAll(dir);
    }
  }


  public void testCheckNhPicard() {
    final SAMRecord rec = new SAMRecord(null);
    rec.setReadString("TATTAGGATTGAGACTGGTAAAATGGNCCACCAAG");
    assertEquals(null, SamUtils.getNHOrIH(rec));
    rec.setAttribute("IH", 1);
    assertEquals(Integer.valueOf(1), SamUtils.getNHOrIH(rec));
    rec.setAttribute("NH", 2);
    assertEquals(Integer.valueOf(2), SamUtils.getNHOrIH(rec));
  }


  private static final String CG_QUALITY = "+" + LS + "00000" + "0000000000" + "0000000000" + "0000000000" + LS;
  public void testCGMapbug1144() throws IOException {
    //set showBug to true to get the failure for the bug 1144
    final boolean showBug = false;
    Diagnostic.setLogStream();
    final String templateSeq = ">template" + LS + "GACCATCAGGACAAACACATGGATACATGGAGGGGAACACACACAC" + LS;
    //                                               CAATCAGGAC      GTGGATACATGGAGGGGAAC
    //                                                                                 ACA AC
    //System.err.println(templateSeq);
    final PrintStream err = System.err;
    final File parent = FileUtils.createTempDir("testbug1144", "overlap2Same");
    try {
      if (!showBug) {
        System.setErr(TestUtils.getNullPrintStream());
      }
      final File reads = new File(parent, "reads");
      final File left = new File(reads, "left");
      final File right = new File(reads, "right");
      final String left0 =  "AAAAA AAAAAAAAAA AAAAAAAAAA AAAAAAAAAA"; //Random seq
      ReaderTestUtils.getReaderDNAFastqCG("@reL" + LS + left0 + CG_QUALITY, left, PrereadArm.LEFT);
      final String right0 = "CAATCAGGACGTGGATACATGGAGGGGAACACAAC"; //Buggy seq
      ReaderTestUtils.getReaderDNAFastqCG("@reR" + LS + right0 + CG_QUALITY, right, PrereadArm.RIGHT);

      final File template = new File(parent, "template");
      ReaderTestUtils.getReaderDNA(templateSeq, template, null);
      final File output = new File(parent, "out");
      final CgMapCli cgmap = new CgMapCli();
      final MemoryPrintStream mps = new MemoryPrintStream();
      final int code = cgmap.mainInit(new String[] {"-o", output.getPath(), "-i", reads.getPath(), "-t", template.getPath(), "-E", "10", "-Z", "-T", "1", "--sam", "--no-merge"}, mps.outputStream(), mps.printStream());
      assertEquals(mps.toString(), 0, code);
      if (showBug) {
        final String str = dumpSam(new File(output, "unmated.sam"));
        //System.err.println(str);
        //not clear what the real alignment should be - the "good" result below assumes an overlap of 2
        assertTrue(str.contains("GC:Z:28S2G3S")); //bug has GC:Z:30S1G3S
      }
    } finally {
      System.setErr(err);
      //System.err.println(parent.getPath());
      FileHelper.deleteAll(parent);
    }
  }

  String dumpSam(final File samFile) throws IOException {
    final StringBuilder sb = new StringBuilder();
    final RecordIterator<SAMRecord> iter = new SkipInvalidRecordsIterator(samFile);
    while (iter.hasNext()) {
      final SAMRecord sam = iter.next();
      sb.append(sam.getSAMString().trim()).append(LS);
    }
    iter.close();
    return sb.toString();
  }

  public void testSamRunId() {
    final MemoryPrintStream mps = new MemoryPrintStream();
    Diagnostic.setLogStream(mps.printStream());
    try {
      final SAMFileHeader header = new SAMFileHeader();
      header.addComment(SamUtils.RUN_ID_ATTRIBUTE + "booyahhhhh");
      SamUtils.logRunId(header);
      assertTrue(mps.toString().contains("Referenced SAM file with RUN-ID: booyahhhhh"));
      mps.reset();
      assertEquals("", mps.toString().trim());
      SamUtils.logRunId(header);
      assertEquals("", mps.toString().trim());
    } finally {
      Diagnostic.setLogStream();
    }
  }

  public void testCigarCodes() {
    //this actually does need to be in this order.
    assertTrue(Arrays.equals(new char[] {'M', 'I', 'D', 'N', 'S', 'H', 'P', '=', 'X'}, SamUtils.getCigarCodes()));
  }

  public final void testConvertCigars() {
    Cigar testCigar = new Cigar();
    testCigar.add(new CigarElement(1, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));

    Cigar res = SamUtils.convertToLegacyCigar(testCigar);
    assertEquals("2M", res.toString());

    testCigar = new Cigar();
    testCigar.add(new CigarElement(1, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));
    testCigar.add(new CigarElement(1, CigarOperator.EQ));

    res = SamUtils.convertToLegacyCigar(testCigar);
    assertEquals("3M", res.toString());

    testCigar = new Cigar();
    testCigar.add(new CigarElement(1, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));
    testCigar.add(new CigarElement(1, CigarOperator.D));
    testCigar.add(new CigarElement(1, CigarOperator.EQ));

    res = SamUtils.convertToLegacyCigar(testCigar);
    assertEquals("2M1D1M", res.toString());

    testCigar = new Cigar();
    testCigar.add(new CigarElement(1, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));
    testCigar.add(new CigarElement(1, CigarOperator.I));
    testCigar.add(new CigarElement(1, CigarOperator.EQ));

    res = SamUtils.convertToLegacyCigar(testCigar);
    assertEquals("2M1I1M", res.toString());

    //90=1X25=1X1=1X15=1I65=
    testCigar = new Cigar();
    testCigar.add(new CigarElement(90, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));
    testCigar.add(new CigarElement(25, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));
    testCigar.add(new CigarElement(1, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));
    testCigar.add(new CigarElement(15, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.I));
    testCigar.add(new CigarElement(65, CigarOperator.EQ));
    res = SamUtils.convertToLegacyCigar(testCigar);
    assertEquals("134M1I65M", res.toString());

    //15=1X41=1X28=1X3=1X14=1X1I22=1X52=1X17=

    testCigar = new Cigar();
    testCigar.add(new CigarElement(15, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));
    testCigar.add(new CigarElement(41, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));
    testCigar.add(new CigarElement(28, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));
    testCigar.add(new CigarElement(3, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));
    testCigar.add(new CigarElement(14, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));
    testCigar.add(new CigarElement(1, CigarOperator.I));
    testCigar.add(new CigarElement(22, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));
    testCigar.add(new CigarElement(52, CigarOperator.EQ));
    testCigar.add(new CigarElement(1, CigarOperator.X));
    testCigar.add(new CigarElement(17, CigarOperator.EQ));
    res = SamUtils.convertToLegacyCigar(testCigar);
    assertEquals("106M1I93M", res.toString());
  }

  public void testZippedSamFileName() {
    assertEquals("test.sam.gz", SamUtils.getZippedSamFileName(true, new File("test")).getName());
    assertEquals("test.sam", SamUtils.getZippedSamFileName(false, new File("test")).getName());
    assertEquals("test.sam.gz", SamUtils.getZippedSamFileName(true, new File("test.sam")).getName());
    assertEquals("test.sam", SamUtils.getZippedSamFileName(false, new File("test.sam")).getName());
    assertEquals("test.sam.gz", SamUtils.getZippedSamFileName(true, new File("test.sam.gz")).getName());
    assertEquals("test.sam.gz.sam", SamUtils.getZippedSamFileName(false, new File("test.sam.gz")).getName());
  }

  public void testSamReadName() {
    assertNull(SamUtils.samReadName(null, false));
    assertNull(SamUtils.samReadName(null, true));

    assertEquals("a", SamUtils.samReadName("a/1", true));
    assertEquals("a", SamUtils.samReadName("a/2", true));
    assertEquals("a/3", SamUtils.samReadName("a/3", true));

    assertEquals("a/1", SamUtils.samReadName("a/1", false));
    assertEquals("a/2", SamUtils.samReadName("a/2", false));

    assertEquals("/1", SamUtils.samReadName("/1", true));
    assertEquals("/2", SamUtils.samReadName("/2", true));
  }


  private static final String OUT_SAM = "alignments.sam";

  private static final String SAM_HEAD = ""
    + "@HD" + TAB + "VN:1.0" + TAB + "SO:coordinate\n"
    + "@SQ" + TAB + "SN:gi" + TAB + "LN:30\n";

  private static final String SAM_HEADWRONG = ""
    + "@HD" + TAB + "VN:1.0" + TAB + "SO:coordinate\n"
    + "@SQ" + TAB + "SN:wrong" + TAB + "LN:30\n";

  private static final String SAM_HEAD_LONGER = ""
    + "@HD" + TAB + "VN:1.0" + TAB + "SO:coordinate\n"
    + "@SQ" + TAB + "SN:gi" + TAB + "LN:30\n"
    + "@SQ" + TAB + "SN:gj" + TAB + "LN:60\n";

  private static final String SAM_HEAD_DIFF_LEN = ""
    + "@HD" + TAB + "VN:1.0" + TAB + "SO:coordinate\n"
    + "@SQ" + TAB + "SN:gi" + TAB + "LN:40\n";

  public void testPass() throws IOException {
    final File alignmentsDir = FileUtils.createTempDir("samheaderchecktest", "check");
    final File file1 = new File(alignmentsDir, OUT_SAM + 1);
    final File file2 = new File(alignmentsDir, OUT_SAM + 2);
    final File file3 = new File(alignmentsDir, OUT_SAM + 3);
    FileUtils.stringToFile(SAM_HEAD, file1);
    FileUtils.stringToFile(SAM_HEAD, file2);
    FileUtils.stringToFile(SAM_HEAD, file3);
    final ArrayList<File> files = new ArrayList<>();
    files.add(file1);
    files.add(file2);
    files.add(file3);
    final SAMFileHeader header = SamUtils.getUberHeader(files);
    assertNotNull(header);
    assertEquals(0, header.getSequenceIndex("gi"));
    assertTrue(FileHelper.deleteAll(alignmentsDir));
  }

  public void testFailures() throws IOException {
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    final PrintStream logStream = new PrintStream(bos);
    Diagnostic.setLogStream(logStream);
    final File alignmentsDir = FileUtils.createTempDir("samheaderchecktest", "check");
    final File file1 = new File(alignmentsDir, OUT_SAM + 1);
    final File file2 = new File(alignmentsDir, OUT_SAM + 2);
    final File file3 = new File(alignmentsDir, OUT_SAM + 3);
    final File file4 = new File(alignmentsDir, OUT_SAM + 4);
    FileUtils.stringToFile(SAM_HEAD, file1);
    FileUtils.stringToFile(SAM_HEADWRONG, file2);
    FileUtils.stringToFile(SAM_HEAD_LONGER, file3);
    FileUtils.stringToFile(SAM_HEAD_DIFF_LEN, file4);
    final ArrayList<File> files = new ArrayList<>();
    files.add(file1);
    files.add(file2);
    try {
      SamUtils.getUberHeader(files);
      fail();
    } catch (NoTalkbackSlimException e) {
      //expected
    }
    files.clear();
    files.add(file1);
    files.add(file3);
    try {
      SamUtils.getUberHeader(files);
      fail();
    } catch (NoTalkbackSlimException e) {
      //expected
    }
    files.clear();
    files.add(file3);
    files.add(file1);
    try {
      SamUtils.getUberHeader(files);
      fail();
    } catch (NoTalkbackSlimException e) {
      //expected
    }
    files.clear();
    files.add(file1);
    files.add(file4);
    try {
      SamUtils.getUberHeader(files);
      fail();
    } catch (NoTalkbackSlimException e) {
      //expected
    }
    logStream.flush();
    final String[] exp = {""
      + "The SAM file \"" + file1.getPath()
      + "\" cannot be merged with the SAM file \"" + file2.getPath()
      + "\" because their headers are incompatible." + LS,
      "The SAM file \"" + file1.getPath()
      + "\" cannot be merged with the SAM file \"" + file3.getPath()
      + "\" because their headers are incompatible." + LS,
      "The SAM file \"" + file3.getPath()
      + "\" cannot be merged with the SAM file \"" + file1.getPath()
      + "\" because their headers are incompatible." + LS,
      "The SAM file \"" + file1.getPath()
      + "\" cannot be merged with the SAM file \"" + file4.getPath()
      + "\" because their headers are incompatible." + LS
      };
    TestUtils.containsAll(bos.toString(), exp);
    assertTrue(FileHelper.deleteAll(alignmentsDir));
    Diagnostic.setLogStream();
  }

  public void testReadGroupFailure() throws IOException {
    final ByteArrayOutputStream bos = new ByteArrayOutputStream();
    final PrintStream logStream = new PrintStream(bos);
    Diagnostic.setLogStream(logStream);
    final File alignmentsDir = FileUtils.createTempDir("samheaderchecktest", "check");
    final File file1 = new File(alignmentsDir, OUT_SAM + 1);
    final File file2 = new File(alignmentsDir, OUT_SAM + 2);
    FileUtils.stringToFile("@HD" + TAB + "VN:1.0" + TAB + "SO:coordinate\n" + "@SQ" + TAB + "SN:gi" + TAB + "LN:30\n" + "@RG\tID:blah\tSM:sam1\tPL:IONTORRENT", file1);
    FileUtils.stringToFile("@HD" + TAB + "VN:1.0" + TAB + "SO:coordinate\n" + "@SQ" + TAB + "SN:gi" + TAB + "LN:30\n" + "@RG\tID:blah\tSM:sam2\tPL:IONTORRENT", file2);
    final ArrayList<File> files = new ArrayList<>();
    files.add(file1);
    files.add(file2);
    try {
      SamUtils.getUberHeader(files);
      fail();
    } catch (final NoTalkbackSlimException ntse) {
      assertEquals("1", ntse.getMessage());
    }

    logStream.flush();
    final String[] exp = {file2.getPath() + " contained read group with id \"blah\" and sample \"sam2\" but"
    };
    TestUtils.containsAll(bos.toString(), exp);
    assertTrue(FileHelper.deleteAll(alignmentsDir));
    Diagnostic.setLogStream();
  }

  public void testSamAgainstRef() throws IOException {
    // create sdf
    // create sam file(s)
    // SamUtils.checkHeadersAgainstReference
    //TEMPLATE-SDF-ID:

    try (TestDirectory dir = new TestDirectory()) {
      final File samFileGood = new File(dir, "good.sam");
      final File samFileBad = new File(dir, "bad.sam");

      final StringBuilder contents = new StringBuilder();
      contents.append("@SQ\tSN:gi0\tLN:10000\n");
      contents.append("@RG\tID:rg1\tSM:sm1\tPL:ILLUMINA\n");
      contents.append("@CO\tHELLO\n");
      contents.append("38218590\t99\tgi0\t182\t55\t1=3X52=1X43=\t=\t390\t308\tCTGACCCCACGCTGCCCACAACAGCTCCAGAGAAATTCCTAGATATATTTTTACTATTATCACTTTTTAATGTTTTCATTTTAAGTTATTTATTGGCCCT\tCCCFFFFFHHHHHJJJJJJJJJJJJJJJJJJJJIJJJJJJJJJJJJIJJJJJJIJJJJIJJJIIJJHHHHHHFFFDFFEEFEEEEEFEEDEFEDDDDDDD\tAS:i:4\tNM:i:4\tXA:i:6\tRG:Z:lic-rtg-b1_1\tIH:i:1\tNH:i:1\tRG:Z:rg1\n");
      contents.append("38851323\t163\tgi0\t384\t34\t36=1X14=1X48=\t=\t611\t327\tATTTTGTATTTTTAATATTGTATTTTTGAGAGTGTAACCTCTACTCTAGATCTTTAATCTTTGCTTTTGGTATTTGTTATCAATTTTGTACCTTTAAGAA\tCCCFFFFFHHHHHJJJJJJJIIJJJJJJJJJJGGGIJJJJJIJJJJJIIIIIJJJJJJJJJJJJJJJJJJFHIIJJIIIJJHHHHHHHFFFFFFFCCECE\tAS:i:2\tNM:i:2\tXA:i:4\tRG:Z:lic-rtg-b1_1\tIH:i:1\tNH:i:1\tRG:Z:rg1\n");

      FileUtils.stringToFile("@HD\tVN:1.4\tSO:coordinate\n@CO\tTEMPLATE-SDF-ID:779dc54e-8d24-431e-9c16-1a655e53a129\n" + contents.toString(), samFileGood);
      FileUtils.stringToFile("@HD\tVN:1.4\tSO:coordinate\n@CO\tTEMPLATE-SDF-ID:779dc54e-8d24-431e-9c16-1a655e53a192\n" + contents.toString(), samFileBad);

      final File refFile = new File(dir, "sdf");
      final SequencesReader sr = new ReaderLongMock(1) {
        @Override
        public SdfId getSdfId() {
          return new SdfId("779dc54e-8d24-431e-9c16-1a655e53a129");
        }
        @Override
        public File path() {
          return refFile;
        }
        @Override
        public String name(long index) {
          return index == 0 ? "gi0" : null;
        }
      };

      try (final ByteArrayOutputStream bos = new ByteArrayOutputStream();
          final PrintStream logStream = new PrintStream(bos)) {
        Diagnostic.setLogStream(logStream);
        final ArrayList<File> samFiles = new ArrayList<>();
        samFiles.add(samFileGood);
        samFiles.add(samFileGood);
        SamUtils.checkUberHeaderAgainstReference(sr, SamUtils.getUberHeader(samFiles), false);
        final String log = bos.toString();
        assertEquals(0, log.length());
      }

      try (final ByteArrayOutputStream bos = new ByteArrayOutputStream();
          final PrintStream logStream = new PrintStream(bos)) {
        Diagnostic.setLogStream(logStream);
        final ArrayList<File> samFiles = new ArrayList<>();
        samFiles.add(samFileGood);
        samFiles.add(samFileGood);
        SamUtils.checkUberHeaderAgainstReference(sr, SamUtils.getUberHeader(samFiles), true);
        final String log = bos.toString();
        assertEquals(0, log.length());
      }

      try (final ByteArrayOutputStream bos = new ByteArrayOutputStream();
          final PrintStream logStream = new PrintStream(bos)) {
        Diagnostic.setLogStream(logStream);
        final ArrayList<File> samFiles = new ArrayList<>();
        samFiles.add(samFileGood);
        samFiles.add(samFileBad);
        SamUtils.getUberHeader(samFiles);
        final String log = bos.toString();
        assertTrue(log, log.contains("Input SAM files contain mismatching template GUIDs"));
      }

      Diagnostic.setLogStream();
    }
  }

}
