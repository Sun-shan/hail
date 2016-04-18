package org.broadinstitute.hail.driver

import org.kohsuke.args4j.{Option => Args4jOption}
import org.broadinstitute.hail.Utils._

object RepartitionHcs extends Command {
  class Options extends BaseOptions {
    @Args4jOption(required = false, name = "-n", aliases = Array("--partitions"), usage = "Number of partitions")
    var k: Integer = _
  }
  def newOptions = new Options

  def name = "repartitionhcs"
  def description = "Repartition the current hard call set, sorted by variant"

  def run(state: State, options: Options): State = {

    if (options.k != null) {
      if (options.k < 1)
        fatal("Number of partitions must be positive.")
      state.copy(hcs = state.hcs.rangePartition(options.k))
    } else
      state.copy(hcs = state.hcs.rangePartition())
  }
}