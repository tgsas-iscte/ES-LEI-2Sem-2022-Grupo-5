package org.jgrapht.alg.matching;


import java.util.Arrays;

import org.jgrapht.alg.matching.KuhnMunkresMinimalWeightBipartitePerfectMatching.KuhnMunkresMatrixImplementation;

public class KuhnMunkresMatrixImplementationProduct {
	static double[][] costMatrix;
	double[][] excessMatrix;
	public KuhnMunkresMatrixImplementationProduct(double[][] costMatrix, double[][] excessMatrix) {
		super();
		this.costMatrix = costMatrix;
		this.excessMatrix = excessMatrix;
	}

	/**
	* Composes excess-matrix corresponding to the given cost-matrix
	*/
	public double[][] makeExcessMatrix() {
		double[][] excessMatrix = this.excessMatrix;
		for (int i = 0; i < excessMatrix.length; ++i) {
			try {
				excessMatrix[i] = Arrays.copyOf(KuhnMunkresMatrixImplementationProduct.costMatrix[i], this.costMatrix[i].length);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		for (int i = 0; i < excessMatrix.length; ++i) {
			double cheapestTaskCost = cheapestTaskCost(excessMatrix, i);
			for (int j = 0; j < excessMatrix[i].length; ++j) {
				excessMatrix[i][j] -= cheapestTaskCost;
			}
		}
		for (int j = 0; j < excessMatrix[0].length; ++j) {
			double cheapestWorkerCost = cheapestWorkerCost(excessMatrix, j);
			for (int i = 0; i < excessMatrix.length; ++i) {
				excessMatrix[i][j] -= cheapestWorkerCost;
			}
		}
		return excessMatrix;
	}

	public double cheapestWorkerCost(double[][] excessMatrix, int j) {
		double cheapestWorkerCost = Double.MAX_VALUE;
		for (int i = 0; i < excessMatrix.length; ++i) {
			if (cheapestWorkerCost > excessMatrix[i][j]) {
				cheapestWorkerCost = excessMatrix[i][j];
			}
		}
		return cheapestWorkerCost;
	}

	public double cheapestTaskCost(double[][] excessMatrix, int i) {
		double cheapestTaskCost = Double.MAX_VALUE;
		for (int j = 0; j < excessMatrix[i].length; ++j) {
			if (cheapestTaskCost > excessMatrix[i][j]) {
				cheapestTaskCost = excessMatrix[i][j];
			}
		}
		return cheapestTaskCost;
	}
}