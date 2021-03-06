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

package com.rtg.variant.bayes;

/**
 */
public class HoeffdingAlleleBalanceTest extends AbstractAlleleBalanceTest {
  public void testAlleleBalanceZeroCoverage() {
    assertEquals(0.0, balance(new int[]{0, 0, 0, 0}, new double[]{0.0, 0.0, 0.0, 0.0}, findIndexByName("B", "B")));
  }
  public void testAlleleBalancePerfectBalanceNoError() {
    assertEquals(0.000, balance(new int[]{9, 9, 0, 0}, new double[]{0.0, 0.0, 0.0, 0.0}, findIndexByName("B", "D")), 0.001);
  }

  public void testAlleleBalancePerfectBalanceError() {
    assertEquals(-0.009, balance(new int[]{9, 9, 0, 0}, new double[]{0.2, 0.2, 0.0, 0.0}, findIndexByName("B", "D")), 0.001);
  }

  public void testAlleleBalanceUnbalanced() {
    assertEquals(-9.0, balance(new int[]{9, 0, 0, 0}, new double[]{0.0, 0.0, 0.0, 0.0}, findIndexByName("B", "D")), 0.001);
  }

  public void testBinomialBalancedBetterThanUnbalanced() {
    final double balance = balance(new int[]{7, 7, 0, 0}, new double[]{0.1, 0.1, 0.0, 0.0}, findIndexByName("B", "D"));
    final double unbalanced = balance(new int[]{7, 4, 0, 0}, new double[]{0.1, 0.1, 0.0, 0.0}, findIndexByName("B", "D"));
    assertTrue(balance > unbalanced);
  }

  public void testBinomialGetsWorseAsBalanceDecreases() {
    final double unbalanced = balance(new int[]{7, 4, 0, 0}, new double[]{0.1, 0.1, 0.0, 0.0}, findIndexByName("B", "D"));
    final double worse = balance(new int[]{7, 3, 0, 0}, new double[]{0.1, 0.1, 0.0, 0.0}, findIndexByName("B", "D"));
    assertTrue(unbalanced > worse);
  }

  @Override
  protected HoeffdingAlleleBalance getAlleleBalanceProbability() {
    return new HoeffdingAlleleBalance(0.5);
  }
}
