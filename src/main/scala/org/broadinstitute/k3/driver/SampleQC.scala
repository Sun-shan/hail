package org.broadinstitute.k3.driver

import org.apache.spark.broadcast.Broadcast
import org.broadinstitute.k3.methods._
import org.broadinstitute.k3.variant._
import org.broadinstitute.k3.Utils._
import org.kohsuke.args4j.{Option => Args4jOption}

object SampleQC extends Command {

  class Options extends BaseOptions {
    @Args4jOption(required = true, name = "-o", aliases = Array("--output"), usage = "Output file")
    var output: String = _
  }

  def newOptions = new Options

  def name = "sampleqc"

  def description = "Compute per-sample QC metrics"

  def results(vds: VariantDataset,
    methods: Array[AggregateMethod],
    derivedMethods: Array[DerivedMethod]): Map[Int, Array[Any]] = {
    val methodIndex = methods.zipWithIndex.toMap

    val methodsBc = vds.sparkContext.broadcast(methods)

    vds
      .aggregateBySampleWithKeys(methods.map(_.aggZeroValue: Any))(
        (acc, v, s, g) => methodsBc.value.zipWith[Any, Any](acc, (m, acci) =>
            m.seqOpWithKeys(v, s, g, acci.asInstanceOf[m.T])),
        (x, y) => methodsBc.value.zipWith[Any, Any, Any](x, y, (m, xi, yi) =>
            m.combOp(xi.asInstanceOf[m.T], yi.asInstanceOf[m.T])))
      .mapValues(values =>
        values ++ derivedMethods.map(_.map(MethodValues(methodIndex, values))))
  }

  def run(state: State, options: Options): State = {
    val sc = state.sc
    val vds = state.vds

    val singletons: Broadcast[Set[Variant]] = sc.broadcast(sSingletonVariants(vds))

    val methods: Array[AggregateMethod] = Array(
      nCalledPer, nNotCalledPer,
      nHomRefPer, nHetPer, nHomVarPer,
      nSNPPerSample, nInsertionPerSample, nDeletionPerSample,
      new nSingletonPerSample(singletons), nTransitionPerSample, nTransversionPerSample,
      dpStatCounterPer, gqStatCounterPer
    )

    val derivedMethods: Array[DerivedMethod] = Array(
      nNonRefPer, rTiTvPerSample, rHetHomPer, rDeletionInsertionPerSample, dpMeanPer, dpStDevPer, gqMeanPer, gqStDevPer
    )

    val r = results(vds, methods, derivedMethods)
    writeTextFile(options.output, state.hadoopConf) { s =>
      val allMethods = methods ++ derivedMethods
      val header = "sampleID" + "\t" + allMethods.map(_.name).mkString("\t") + "\n"
      s.write(header)

      for ((id, i) <- vds.sampleIds.zipWithIndex) {
        s.write(id + "\t" + r(i).mkString("\t") + "\n")
      }
    }

    state
  }
}
