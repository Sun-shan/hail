package org.broadinstitute.hail.driver

import org.broadinstitute.hail.methods.{LinearRegressionFromHardCallSet, CovariateData, Pedigree}
import org.broadinstitute.hail.variant.HardCallSet
import org.kohsuke.args4j.{Option => Args4jOption}
import org.broadinstitute.hail.Utils._

object LinearRegressionFromHardCallSetCommand extends Command {

  def name = "linreghcs"
  def description = "Compute beta, std error, t-stat, and p-val for each SNP with additional sample covariates from hard call set"

  class Options extends BaseOptions {
    @Args4jOption(required = true, name = "-o", aliases = Array("--output"), usage = "Output root filename")
    var output: String = _

    @Args4jOption(required = true, name = "-f", aliases = Array("--fam"), usage = ".fam file")
    var famFilename: String = _

    @Args4jOption(required = true, name = "-c", aliases = Array("--cov"), usage = ".cov file")
    var covFilename: String = _
  }
  def newOptions = new Options

  def run(state: State, options: Options): State = {
    val hcs = state.hcs
    val ped = Pedigree.read(options.famFilename, state.hadoopConf, hcs.sampleIds)
    val cov = CovariateData.read(options.covFilename, state.hadoopConf, hcs.sampleIds)
      .filterSamples(ped.phenotypedSamples)

    // FIXME: won't want to check this in production, should ensure it elsewhere
    if (!(hcs.localSamples sameElements cov.covRowSample))
      fatal("Samples misaligned, recreate .hcs using .ped and .cov")

    val linreg = LinearRegressionFromHardCallSet(hcs, ped, cov)

    linreg.write(options.output)

    state
  }
}