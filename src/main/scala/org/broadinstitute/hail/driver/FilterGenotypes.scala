package org.broadinstitute.hail.driver

import org.broadinstitute.hail.Utils._
import org.broadinstitute.hail.expr
import org.broadinstitute.hail.methods._
import org.broadinstitute.hail.annotations._
import org.broadinstitute.hail.variant.{VariantDataset, Variant, Genotype, Sample}
import org.kohsuke.args4j.{Option => Args4jOption}

object FilterGenotypes extends Command {

  class Options extends BaseOptions {
    @Args4jOption(required = false, name = "--keep", usage = "Keep only listed samples in current dataset")
    var keep: Boolean = false

    @Args4jOption(required = false, name = "--remove", usage = "Remove listed samples from current dataset")
    var remove: Boolean = false

    @Args4jOption(required = true, name = "-c", aliases = Array("--condition"),
      usage = "Filter condition expression")
    var condition: String = _
  }

  def newOptions = new Options

  def name = "filtergenotypes"

  def description = "Filter genotypes in current dataset"

  def run(state: State, options: Options): State = {
    val sc = state.sc
    val vds = state.vds

    if (!options.keep && !options.remove)
      fatal(name + ": one of `--keep' or `--remove' required")

    val keep = options.keep

    val symTab = Map(
      "v" ->(0, expr.TVariant),
      "va" ->(1, vds.metadata.variantAnnotationSignatures.toExprType),
      "s" ->(2, expr.TSample),
      "sa" ->(3, vds.metadata.sampleAnnotationSignatures.toExprType),
      "g" ->(4, expr.TGenotype))
    val a = new Array[Any](5)

    val f: () => Any = expr.Parser.parse[Any](symTab, a, options.condition)

    val sampleIdsBc = sc.broadcast(vds.sampleIds)
    val sampleAnnotationsBc = sc.broadcast(vds.metadata.sampleAnnotations)

    val noCall = Genotype()
    val newVDS = vds.mapValuesWithAll(
      (v: Variant, va: Annotations, s: Int, g: Genotype) => {
        a(0) = v
        a(1) = va.attrs
        a(2) = sampleIdsBc.value(s)
        a(3) = sampleAnnotationsBc.value(s).attrs
        a(4) = g
        if (Filter.keepThisAny(f(), keep))
          g
        else
          noCall
      })
    state.copy(vds = newVDS)
  }
}
