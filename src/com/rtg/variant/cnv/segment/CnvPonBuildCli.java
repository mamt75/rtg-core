/*
 * Copyright (c) 2016. Real Time Genomics Limited.
 *
 * Use of this source code is bound by the Real Time Genomics Limited Software Licence Agreement
 * for Academic Non-commercial Research Purposes only.
 *
 * If you did not receive a license accompanying this file, a copy must first be obtained by email
 * from support@realtimegenomics.com.  On downloading, using and/or continuing to use this source
 * code you accept the terms of that license agreement and any amendments to those terms that may
 * be made from time to time by Real Time Genomics Limited.
 */
package com.rtg.variant.cnv.segment;

import static com.rtg.util.cli.CommonFlagCategories.INPUT_OUTPUT;
import static com.rtg.util.cli.CommonFlagCategories.SENSITIVITY_TUNING;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collections;

import com.rtg.bed.BedUtils;
import com.rtg.bed.BedWriter;
import com.rtg.launcher.AbstractCli;
import com.rtg.launcher.CommonFlags;
import com.rtg.reader.SequencesReader;
import com.rtg.reader.SequencesReaderFactory;
import com.rtg.util.Environment;
import com.rtg.util.cli.CommandLine;
import com.rtg.util.cli.CommonFlagCategories;
import com.rtg.util.cli.Flag;
import com.rtg.util.diagnostic.Diagnostic;
import com.rtg.util.diagnostic.NoTalkbackSlimException;
import com.rtg.util.io.FileUtils;
import com.rtg.variant.cnv.preprocess.AddGc;
import com.rtg.variant.cnv.preprocess.GcNormalize;
import com.rtg.variant.cnv.preprocess.NumericColumn;
import com.rtg.variant.cnv.preprocess.RegionDataset;
import com.rtg.variant.cnv.preprocess.StringColumn;
import com.rtg.variant.cnv.preprocess.WeightedMedianNormalize;

/**
 * Provide construction of a panel sample for CNV calling.
 */
public class CnvPonBuildCli extends AbstractCli {

  private static final String VERSION_STRING = "#Version " + Environment.getVersion();
  private static final String CNV_PON_OUTPUT_VERSION = "v1.1";

  private SequencesReader mReference = null;

  @Override
  public String moduleName() {
    return "cnvponbuild";
  }

  @Override
  public String description() {
    return "build a typical CNV normalization sample from a panel of coverage output";
  }

  @Override
  protected void initFlags() {
    mFlags.registerExtendedHelp();
    mFlags.setDescription("Construct a normalized coverage sample from a panel of coverage outputs.");
    CommonFlagCategories.setCategories(mFlags);
    CommonFlags.initNoGzip(mFlags);
    CommonFlags.initIndexFlags(mFlags);
    mFlags.registerRequired('t', CommonFlags.TEMPLATE_FLAG, File.class, CommonFlags.SDF, "SDF containing reference genome").setCategory(INPUT_OUTPUT);
    mFlags.registerRequired('o', CommonFlags.OUTPUT_FLAG, File.class, CommonFlags.FILE, "BED output file").setCategory(INPUT_OUTPUT);
    mFlags.registerOptional(SegmentCli.GCBINS_FLAG, Integer.class, CommonFlags.INT, "number of bins when applying GC correction", 10).setCategory(SENSITIVITY_TUNING);
    mFlags.registerOptional(SegmentCli.COV_COLUMN_NAME, String.class, CommonFlags.STRING, "name of the coverage column in input data", SegmentCli.DEFAULT_COLUMN_NAME).setCategory(SENSITIVITY_TUNING);
    final Flag covFlag = mFlags.registerRequired(File.class, CommonFlags.FILE, "coverage BED file").setCategory(INPUT_OUTPUT);
    covFlag.setMaxCount(Integer.MAX_VALUE);
    mFlags.setValidator(flags -> flags.checkInRange(SegmentCli.GCBINS_FLAG, 0, Integer.MAX_VALUE)
    );
  }

  private NumericColumn normalize(final File coverageFile, final AddGc gcCorrector, final int gcbins) throws IOException {
    Diagnostic.info("Normalizing and G+C correcting " + coverageFile);
    final String coverageColumnName = (String) mFlags.getValue(SegmentCli.COV_COLUMN_NAME);
    final RegionDataset coverageData = RegionDataset.readFromBed(coverageFile, Collections.singletonList(new NumericColumn(coverageColumnName)));
    if (coverageData.columnId(coverageColumnName) == -1) {
      throw new NoTalkbackSlimException("Could not find column named " + coverageColumnName + " in " + coverageFile);
    }
    new WeightedMedianNormalize(coverageData.columnId(coverageColumnName)).process(coverageData);
    final int normCovCol = coverageData.columns() - 1;
    if (gcCorrector != null) {
      new AddGc(mReference).process(coverageData);
      new GcNormalize(normCovCol, gcbins).process(coverageData);
    }
    return coverageData.asNumeric(coverageData.columns() - 1);
  }

  private void writeBedHeader(final BedWriter bw) throws IOException {
    bw.writeln(VERSION_STRING + ", CNV panel BED output " + CNV_PON_OUTPUT_VERSION);
    if (CommandLine.getCommandLine() != null) {
      bw.writeln("#CL\t" + CommandLine.getCommandLine());
    }
    bw.writeComment("#RUN-ID\t" + CommandLine.getRunId());
  }

  @Override
  protected int mainExec(OutputStream out, PrintStream err) throws IOException {
    try (final SequencesReader sr = SequencesReaderFactory.createDefaultSequencesReader((File) mFlags.getValue(CommonFlags.TEMPLATE_FLAG))) {
      mReference = sr;
      final int gcbins = (Integer) mFlags.getValue(SegmentCli.GCBINS_FLAG);
      final AddGc gcCorrector = gcbins > 1 ? new AddGc(mReference) : null;
      final RegionDataset typicalSample = RegionDataset.readFromBed((File) mFlags.getAnonymousValue(0), Collections.singletonList(new StringColumn("label")));
      final double[] sum = new double[typicalSample.size()];
      for (final Object coverageFile : mFlags.getAnonymousValues(0)) {
        final NumericColumn covData = normalize((File) coverageFile, gcCorrector, gcbins);
        if (sum.length != covData.size()) {
          throw new NoTalkbackSlimException("Number of regions in " + coverageFile + " does not match a previous input file");
        }
        for (int k = 0; k < sum.length; ++k) {
          sum[k] += covData.get(k);
        }
      }
      final int n = mFlags.getAnonymousValues(0).size();
      final NumericColumn col = new NumericColumn("normalized-coverage");
      for (final double v : sum) {
        col.add(v / n);
      }
      typicalSample.addColumn(col);
      final boolean gzip = !mFlags.isSet(CommonFlags.NO_GZIP);
      final File bedFile = FileUtils.getZippedFileName(gzip, (File) mFlags.getValue(CommonFlags.OUTPUT_FLAG));
      try (final BedWriter bw = new BedWriter(FileUtils.createOutputStream(bedFile, gzip))) {
        writeBedHeader(bw);
        typicalSample.write(bw);
      }
      final boolean index = !mFlags.isSet(CommonFlags.NO_INDEX);
      if (gzip && index) {
        BedUtils.createBedTabixIndex(bedFile);
      }
    }
    return 0;
  }
}
