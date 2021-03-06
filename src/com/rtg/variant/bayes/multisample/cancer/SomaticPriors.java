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

package com.rtg.variant.bayes.multisample.cancer;

import java.util.Arrays;

import com.rtg.util.integrity.Exam;
import com.rtg.util.integrity.IntegralAbstract;
import com.rtg.variant.bayes.Code;
import com.rtg.variant.bayes.Description;
import com.rtg.variant.bayes.Hypotheses;

/**
 * Mutate haploid or diploid priors and generate probabilities for each mutation.
 */
abstract class SomaticPriors<D extends Description> extends IntegralAbstract {

  /**
   * @param mutation probability of a somatic mutation.
   * @param ref the reference hypothesis.
   * @param priors unnormalized probabilities of transitions.
   * @return normalized transitions including 1-mutation for the reference.
   */
  static double[] mutationNormalize(final double mutation, final int ref, final double[] priors) {
    assert Exam.assertDistribution(priors);
    final double[] norm = new double[priors.length];
    double sum = 0.0;
    for (int k = 0; k < priors.length; ++k) {
      if (k != ref) {
        final double d = priors[k] * mutation;
        norm[k] = d;
        sum += d;
      }
    }
    norm[ref] = 1.0 - sum;
    return norm;
  }

  static double[][] defaultUniformPriors(final int size) {
    // Each row is normalized
    final double uniform = 1.0 / (size - 1);
    final double[][] initialPriors = new double[size][size];
    for (int k = 0; k < size; ++k) {
      Arrays.fill(initialPriors[k], uniform);
      initialPriors[k][k] = 0;
    }
    return initialPriors;
  }

  /**
   * @param mutation probability of a somatic mutation.
   * @param loh probability that there will be a loss of heterozygosity.
   * @param hypotheses the set of current hypotheses.
   * @param initialPriors probabilities of transitions between haploid hypotheses (assumed to be normalized).
   * @return probabilities of somatic transitions between possibly diploid hypotheses.
   * @param <D> description type
   */
  static <D extends Description> double[][] makeQ(final double mutation, double loh, final Hypotheses<D> hypotheses, double[][] initialPriors) {
    final int length = hypotheses.size();
    final double[][] q = new double[length][length];
    new SomaticPriors<D>(hypotheses, mutation, loh, initialPriors) {
      @Override
      void update(final int k, final int j, final double probability) {
        q[k][j] += probability;
      }
    }.update();
    return q;
  }

  protected final Hypotheses<?> mHypotheses;
  private final double mLoh;
  private final double mMutation;
  private final double[][] mInitialPriors;

  /**
   * @param hypotheses to be mutated.
   * @param mutation probability of a single mutation.
   * @param loh probability of loss of heterozygosity.
   * @param initialPriors initial unnormalized haploid transition probabilities.
   */
  SomaticPriors(Hypotheses<D> hypotheses, double mutation, double loh, double[][] initialPriors) {
    mHypotheses = hypotheses;
    mLoh = loh;
    mMutation = mutation;
    mInitialPriors = initialPriors;
    assert integrity();
    assert globalIntegrity();
  }

  /**
   * Generate all mutations and their probabilities.
   */
  void update() {
    if (mHypotheses.haploid()) {
      updateHaploid();
    } else {
      if (mLoh < 1.0) {
        updateDiploid();
      }
      if (mLoh > 0.0) {
        updateLoh();
      }
    }
  }

  private void updateHaploid() {
    for (int k = 0; k < mHypotheses.size(); ++k) {
      final double[] mut = mutant(k);
      for (int i = 0; i < mHypotheses.description().size(); ++i) {
        update(k, i, mut[i]);
      }
    }
  }

  private void updateDiploid() {
    final int size = mHypotheses.description().size();
    final double[][] mut = new double[size][];
    for (int i = 0; i < size; ++i) {
      mut[i] = mutant(i);
    }
    final double notLoh = 1.0 - mLoh;
    final Code code = mHypotheses.code();
    for (int k = 0; k < mHypotheses.size(); ++k) {
      final int a = code.a(k);
      final int b = code.bc(k);
      final double[] mut0 = mut[a];
      final double[] mut1 = mut[b];
      for (int i = 0; i < mHypotheses.description().size(); ++i) {
        final double prob0 = mut0[i] * notLoh;
        for (int j = 0; j < mHypotheses.description().size(); ++j) {
          final double prob1 = mut1[j];
          final int k2 = code.code(i, j);
          update(k, k2, prob0 * prob1);
        }
      }
    }
  }

  void updateLoh() {
    final double loh = mLoh * 0.5;
    final int size = mHypotheses.description().size();
    final double[][] mut = new double[size][];
    for (int i = 0; i < size; ++i) {
      mut[i] = mutant(i);
    }
    final Code code = mHypotheses.code();
    for (int k = 0; k < mHypotheses.size(); ++k) {
      final int c0 = code.a(k);
      final double[] mut0 = mut[c0];
      for (int j = 0; j < mHypotheses.description().size(); ++j) {
        final double prob = mut0[j];
        update(k, code.code(j, j), loh * prob);
      }

      final int c2 = code.bc(k);
      final double[] mut2 = mut[c2];
      for (int j = 0; j < mHypotheses.description().size(); ++j) {
        final double prob = mut2[j];
        update(k, code.code(j, j), loh * prob);
      }
    }
  }

  /**
   * Called for each mutation.
   * @param key1 the original name of category.
   * @param key2 the mutated name of category.
   * @param probability of the mutation.
   */
  abstract void update(final int key1, final int key2, final double probability);

  /**
   * Compute the transition probability from k to each of the allowed codes.
   * @param k the original hypothesis
   * @return the transition probabilities.
   */
  double[] mutant(int k) {
    return mutationNormalize(mMutation, k, mInitialPriors[k]);
  }

  @Override
  public boolean globalIntegrity() {
    integrity();
    final int size = mHypotheses.description().size();
    for (int i = 0; i < size; ++i) {
      final double[] pr = mInitialPriors[i];
      Exam.assertEquals(size, pr.length);
      for (int j = 0; j < size; ++j) {
        final double pv = pr[j];
        Exam.assertTrue(0.0 <= pv && pv <= 1.0 && !Double.isNaN(pv));
      }
    }
    return true;
  }

  @Override
  public boolean integrity() {
    Exam.assertTrue(0.0 <= mMutation && mMutation <= 1.0 && !Double.isNaN(mMutation));
    final int size = mHypotheses.description().size();
    Exam.assertEquals(size, mInitialPriors.length);
    return true;
  }
}
