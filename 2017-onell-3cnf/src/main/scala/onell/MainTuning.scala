package onell

import onell.algorithms.OnePlusLambdaLambdaGA
import onell.algorithms.OnePlusLambdaLambdaGA.ConstantTuning
import onell.problems.{OneMax, Random3CNF}

object MainTuning {
  def main(args: Array[String]): Unit = {
    val map = args.flatMap(s => if (s.startsWith("--") && s.contains("=")) {
      val index = s.indexOf('=')
      Some(s.substring(2, index) -> s.substring(index + 1))
    } else None).toMap

    def getKey(key: String) = map.getOrElse(key, throw new IllegalArgumentException("No --" + key + "=... argument"))

    val tuning = ConstantTuning(
      mutationProbabilityQuotient = getKey("mutation-q").toDouble,
      crossoverProbabilityQuotient = getKey("crossover-q").toDouble,
      firstPopulationSizeQuotient = getKey("first-popsize-q").toDouble,
      secondPopulationSizeQuotient = getKey("second-popsize-q").toDouble
    )
    val tuningMultipleOnSuccess = getKey("tuning-success").toDouble
    val tuningMultipleOnFailure = getKey("tuning-failure").toDouble
    val tuningAlgorithm = OnePlusLambdaLambdaGA.adaptiveLog(tuningMultipleOnSuccess, tuningMultipleOnFailure)
    val n = getKey("problem-size").toInt
    val problem = getKey("function") match {
      case "onemax" => new OneMax(n)
      case "maxsat" => new Random3CNF(n, (4 * n * math.log(n)).toInt)
    }
    val evalLimit = getKey("evaluation-limit").toLong
    val algo = new OnePlusLambdaLambdaGA[Int](lambdaTuning = tuningAlgorithm,
                                              constantTuning = tuning,
                                              evaluationLimit = evalLimit)
    val index = algo.metrics.indexOf("Fitness evaluations")
    val metrics = algo.solve(problem.newInstance)
    println(metrics(index) / n)
  }
}
