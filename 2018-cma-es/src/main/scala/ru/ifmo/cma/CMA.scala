package ru.ifmo.cma

import scala.annotation.tailrec

import breeze.linalg.{*, DenseMatrix, DenseVector, diag, eigSym, max, min, norm, sum}
import breeze.numerics.{log, sqrt}
import breeze.stats.distributions.Rand

class CMA(protected val problem: Problem) {
  protected type Vector = DenseVector[Double]
  protected type Matrix = DenseMatrix[Double]

  protected val Vector: DenseVector.type = DenseVector
  protected val Matrix: DenseMatrix.type = DenseMatrix

  private[this] val optimum = problem.knownOptimum

  private[this] val N = problem.dimension
  private[this] val popSize = 4 + (3 * math.log(N)).toInt
  private[this] val mu = popSize / 2

  private[this] val weights0 = math.log(mu + 0.5) - log(Vector.tabulate(mu)(_ + 1.0))
  private[this] val effectiveMu = {
    val sumW = sum(weights0)
    val sumW2 = sum(weights0 * weights0)
    sumW * sumW / sumW2
  }
  private[this] val weights = weights0 / sum(weights0)
  private[this] val chiN = math.sqrt(N) * (1 - 1 / (4.0 * N) + 1 / (21.0 * N * N))
  private[this] val cs = (effectiveMu + 2.0) / (N + effectiveMu + 3.0)
  private[this] val cc = (4.0 + effectiveMu / N) / (N + 4 + 2.0 * effectiveMu / N)
  private[this] val damps = 1 + 2 * math.max(0, math.sqrt((effectiveMu - 1.0) / (N + 1.0)) - 1) + cs
  private[this] val ccov1 = 2 / ((N + 1.3) * (N + 1.3) + effectiveMu)
  private[this] val ccovmu = 2 * (effectiveMu - 2 + 1.0 / effectiveMu) / ((N + 2.0) * (N + 2.0) + effectiveMu)

  private[this] val psQuot = math.sqrt(cs * (2 - cs) * effectiveMu)
  private[this] val pcQuot = math.sqrt(cc * (2 - cc) * effectiveMu)

  private[this] val fitnessTracker = IndexedSeq.newBuilder[Double]

  protected def sampleXYZ(meanVector: Vector, bd: Matrix, sigma: Double): (Vector, Double, Vector) = {
    val z = Vector.rand(N, Rand.gaussian)
    val x = meanVector + sigma * z
    val y = problem(x)
    (x, y, z)
  }

  protected def decomposeAndHandleErrors(matrix: Matrix): (Matrix, eigSym.EigSym[Vector, Matrix]) = {
    val symmetric = (matrix + matrix.t) / 2.0
    val eig = eigSym(symmetric)
    val minEigenvalue = min(eig.eigenvalues)
    val maxEigenvalue = max(eig.eigenvalues)
    if (minEigenvalue <= 0 || maxEigenvalue / minEigenvalue > 1e14) {
      println(s"[WARNING]: eigenvalues are: [$minEigenvalue ... $maxEigenvalue]")
      val addend = maxEigenvalue / 1e14 - math.max(0, minEigenvalue)
      val newMatrix = symmetric + addend * Matrix.eye[Double](N)
      (newMatrix, eigSym(newMatrix))
    } else {
      (symmetric, eig)
    }
  }

  @tailrec
  private[this] def iterate(
    countIterations: Int,
    maxIterations: Int,
    bestArgument: Vector,
    bestValue: Double,
    meanVector: Vector,
    matrix: Matrix,
    sigma: Double,
    pc: Vector,
    ps: Vector,
    tolerance: Double
  ): (Vector, Double) = {
    if (countIterations == maxIterations || optimum.nonEmpty && bestValue <= optimum.get + tolerance) {
      (bestArgument, bestValue)
    } else {
      val (actualMatrix, eigSym.EigSym(eigValues, eigVectors)) = decomposeAndHandleErrors(matrix)
      val bd = eigVectors * diag(sqrt(eigValues))
      val (x, y, z) = IndexedSeq.fill(popSize)(sampleXYZ(meanVector, bd, sigma)).sortBy(_._2).take(mu).unzip3
      fitnessTracker += y.head
      val (newBestArgument, newBestValue) = if (y.head < bestValue) (x.head, y.head) else (bestArgument, bestValue)
      val xMatrix = Matrix(x :_*).t
      val zMatrix = Matrix(z :_*).t
      val xMatrixW = xMatrix(*, ::) * weights
      val zMatrixW = zMatrix(*, ::) * weights
      val xMean = sum(xMatrixW(*, ::))
      val zMean = sum(zMatrixW(*, ::))
      val newPS = (1 - cs) * ps + psQuot * (eigVectors * zMean)
      val hSigB = norm(newPS) / math.sqrt(1 - math.pow(1 - cs, 2 * countIterations)) / chiN < 1.4 + 2 / (N + 1.0)
      val hSig = if (hSigB) 1.0 else 0.0
      val newPC = (1 - cc) * pc + pcQuot * hSig * (xMean - meanVector)
      val rankMuUpdater = (xMatrix(::, *) - meanVector) / sigma
      val newMatrix = (1 - ccov1 - ccovmu + (1 - hSig) * ccov1 * cc * (2 - cc)) * actualMatrix +
        (ccov1 * newPC) * newPC.t + (rankMuUpdater(*, ::) * weights * ccovmu) * rankMuUpdater.t
      val newSigma = sigma * math.exp(math.min(1.0, (norm(newPS) / chiN - 1) * cs / damps))

      iterate(countIterations + 1, maxIterations, newBestArgument, newBestValue,
        xMean, newMatrix, newSigma, newPC, newPS, tolerance)
    }
  }

  def fitnessHistory: IndexedSeq[Double] = fitnessTracker.result()

  def minimize(
    initial: DenseVector[Double],
    sigma: Double,
    iterations: Int,
    tolerance: Double = 1e-9
  ): (DenseVector[Double], Double) = {
    val initialFitness = problem(initial)
    fitnessTracker.clear()
    fitnessTracker += initialFitness
    iterate(
      countIterations = 0,
      maxIterations = iterations,
      bestArgument = initial,
      bestValue = initialFitness,
      meanVector = initial,
      matrix = DenseMatrix.eye(problem.dimension),
      sigma = sigma,
      pc = DenseVector.zeros(N),
      ps = DenseVector.zeros(N),
      tolerance = tolerance
    )
  }
}