package org.broadinstitute.hail.driver

import org.apache.spark.broadcast.Broadcast
import org.apache.spark.rdd.RDD
import org.apache.spark.util.StatCounter
import org.broadinstitute.hail.annotations._
import org.broadinstitute.hail.methods._
import org.broadinstitute.hail.variant._
import org.broadinstitute.hail.Utils._
import org.kohsuke.args4j.{Option => Args4jOption}

import scala.collection.mutable

object SampleQCCombiner {
  val header = "callRate\t" +
    "nCalled\t" +
    "nNotCalled\t" +
    "nHomRef\t" +
    "nHet\t" +
    "nHomVar\t" +
    "nSNP\t" +
    "nInsertion\t" +
    "nDeletion\t" +
    "nSingleton\t" +
    "nTransition\t" +
    "nTransversion\t" +
    "dpMean\tdpStDev\t" +
    "dpMeanHomRef\tdpStDevHomRef\t" +
    "dpMeanHet\tdpStDevHet\t" +
    "dpMeanHomVar\tdpStDevHomVar\t" +
    "gqMean\tgqStDev\t" +
    "gqMeanHomRef\tgqStDevHomRef\t" +
    "gqMeanHet\tgqStDevHet\t" +
    "gqMeanHomVar\tgqStDevHomVar\t" +
    "nNonRef\t" +
    "rTiTv\t" +
    "rHetHomVar\t" +
    "rDeletionInsertion"

  val signatures = Map("callRate" -> new SimpleSignature("Double"),
    "nCalled" -> new SimpleSignature("Int"),
    "nNotCalled" -> new SimpleSignature("Int"),
    "nHomRef" -> new SimpleSignature("Int"),
    "nHet" -> new SimpleSignature("Int"),
    "nHomVar" -> new SimpleSignature("Int"),
    "nSNP" -> new SimpleSignature("Int"),
    "nInsertion" -> new SimpleSignature("Int"),
    "nDeletion" -> new SimpleSignature("Int"),
    "nSingleton" -> new SimpleSignature("Int"),
    "nTransition" -> new SimpleSignature("Int"),
    "nTransversion" -> new SimpleSignature("Int"),
    "dpMean" -> new SimpleSignature("Double"),
    "dpStDev" -> new SimpleSignature("Double"),
    "dpMeanHomRef" -> new SimpleSignature("Double"),
    "dpStDevHomRef" -> new SimpleSignature("Double"),
    "dpMeanHet" -> new SimpleSignature("Double"),
    "dpStDevHet" -> new SimpleSignature("Double"),
    "dpMeanHomVar" -> new SimpleSignature("Double"),
    "dpStDevHomVar" -> new SimpleSignature("Double"),
    "gqMean" -> new SimpleSignature("Double"),
    "gqStDev" -> new SimpleSignature("Double"),
    "gqMeanHomRef" -> new SimpleSignature("Double"),
    "gqStDevHomRef" -> new SimpleSignature("Double"),
    "gqMeanHet" -> new SimpleSignature("Double"),
    "gqStDevHet" -> new SimpleSignature("Double"),
    "gqMeanHomVar" -> new SimpleSignature("Double"),
    "gqStDevHomVar" -> new SimpleSignature("Double"),
    "nNonRef" -> new SimpleSignature("Int"),
    "rTiTv" -> new SimpleSignature("Double"),
    "rHetHomVar" -> new SimpleSignature("Double"),
    "rDeletionInsertion" -> new SimpleSignature("Double"))
}

class SampleQCCombiner extends Serializable {
  var nNotCalled: Int = 0
  var nHomRef: Int = 0
  var nHet: Int = 0
  var nHomVar: Int = 0

  val dpHomRefSC = new StatCounter()
  val dpHetSC = new StatCounter()
  val dpHomVarSC = new StatCounter()

  var nSNP: Int = 0
  var nIns: Int = 0
  var nDel: Int = 0
  var nSingleton: Int = 0
  var nTi: Int = 0
  var nTv: Int = 0

  val gqHomRefSC: StatCounter = new StatCounter()
  val gqHetSC: StatCounter = new StatCounter()
  val gqHomVarSC: StatCounter = new StatCounter()

  def dpSC: StatCounter = {
    val r = dpHomRefSC.copy()
    r.merge(dpHetSC)
    r.merge(dpHomVarSC)
    r
  }

  def gqSC: StatCounter = {
    val r = gqHomRefSC.copy()
    r.merge(gqHetSC)
    r.merge(gqHomVarSC)
    r
  }

  // FIXME per-genotype

  def merge(v: Variant, vIsSingleton: Boolean, g: Genotype): SampleQCCombiner = {
    g.gt match {
      case Some(0) =>
        nHomRef += 1
        g.dp.foreach { v =>
          dpHomRefSC.merge(v)
        }
        g.gq.foreach { v =>
          gqHomRefSC.merge(v)
        }
      case Some(1) =>
        nHet += 1
        if (v.altAllele.isSNP) {
          nSNP += 1
          if (v.altAllele.isTransition)
            nTi += 1
          else {
            assert(v.altAllele.isTransversion)
            nTv += 1
          }
        } else if (v.altAllele.isInsertion)
          nIns += 1
        else if (v.altAllele.isDeletion)
          nDel += 1
        if (vIsSingleton)
          nSingleton += 1
        g.dp.foreach { v =>
          dpHetSC.merge(v)
        }
        g.gq.foreach { v =>
          gqHetSC.merge(v)
        }
      case Some(2) =>
        nHomVar += 1
        if (v.altAllele.isSNP) {
          nSNP += 1
          if (v.altAllele.isTransition)
            nTi += 1
          else {
            assert(v.altAllele.isTransversion)
            nTv += 1
          }
        } else if (v.altAllele.isInsertion)
          nIns += 1
        else if (v.altAllele.isDeletion)
          nDel += 1
        if (vIsSingleton)
          nSingleton += 1
        g.dp.foreach { v =>
          dpHomVarSC.merge(v)
        }
        g.gq.foreach { v =>
          gqHomVarSC.merge(v)
        }
      case None =>
        nNotCalled += 1
      case _ =>
        throw new IllegalArgumentException("Genotype value " + g.gt.get + " must be 0, 1, or 2.")
    }

    this
  }

  def merge(that: SampleQCCombiner): SampleQCCombiner = {
    nNotCalled += that.nNotCalled
    nHomRef += that.nHomRef
    nHet += that.nHet
    nHomVar += that.nHomVar

    nSNP += that.nSNP
    nIns += that.nIns
    nDel += that.nDel
    nSingleton += that.nSingleton
    nTi += that.nTi
    nTv += that.nTv

    dpHomRefSC.merge(that.dpHomRefSC)
    dpHetSC.merge(that.dpHetSC)
    dpHomVarSC.merge(that.dpHomVarSC)

    gqHomRefSC.merge(that.gqHomRefSC)
    gqHetSC.merge(that.gqHetSC)
    gqHomVarSC.merge(that.gqHomVarSC)

    this
  }

  def emitSC(sb: mutable.StringBuilder, sc: StatCounter) {
    sb.tsvAppend(someIf(sc.count > 0, sc.mean))
    sb += '\t'
    sb.tsvAppend(someIf(sc.count > 0, sc.stdev))
  }

  def emit(sb: mutable.StringBuilder) {

    val nCalled = nHomRef + nHet + nHomVar
    val callRate = divOption(nHomRef + nHet + nHomVar, nHomRef + nHet + nHomVar + nNotCalled)

    sb.tsvAppend(callRate)
    sb += '\t'
    sb.append(nCalled)
    sb += '\t'
    sb.append(nNotCalled)
    sb += '\t'
    sb.append(nHomRef)
    sb += '\t'
    sb.append(nHet)
    sb += '\t'
    sb.append(nHomVar)
    sb += '\t'

    sb.append(nSNP)
    sb += '\t'
    sb.append(nIns)
    sb += '\t'
    sb.append(nDel)
    sb += '\t'

    sb.append(nSingleton)
    sb += '\t'

    sb.append(nTi)
    sb += '\t'
    sb.append(nTv)
    sb += '\t'


    emitSC(sb, dpSC)
    sb += '\t'
    emitSC(sb, dpHomRefSC)
    sb += '\t'
    emitSC(sb, dpHetSC)
    sb += '\t'
    emitSC(sb, dpHomVarSC)
    sb += '\t'

    emitSC(sb, gqSC)
    sb += '\t'
    emitSC(sb, gqHomRefSC)
    sb += '\t'
    emitSC(sb, gqHetSC)
    sb += '\t'
    emitSC(sb, gqHomVarSC)
    sb += '\t'

    // nNonRef
    sb.append(nHet + nHomVar)
    sb += '\t'

    // nTiTvf
    sb.tsvAppend(divOption(nTi, nTv))
    sb += '\t'

    // rHetHomVar
    sb.tsvAppend(divOption(nHet, nHomVar))
    sb += '\t'

    // rDeletionInsertion
    sb.tsvAppend(divOption(nDel, nIns))
  }

  def asMap: Map[String, Any] = {

    Map[String, Any]("callRate" -> divOption(nHomRef + nHet + nHomVar, nHomRef + nHet + nHomVar + nNotCalled),
      "nCalled" -> (nHomRef + nHet + nHomVar),
      "nNotCalled" -> nNotCalled,
      "nHomRef" -> nHomRef,
      "nHet" -> nHet,
      "nHomVar" -> nHomVar,
      "nSNP" -> nSNP,
      "nInsertion" -> nIns,
      "nDeletion" -> nDel,
      "nSingleton" -> nSingleton,
      "nTransition" -> nTi,
      "nTransversion" -> nTv,
      "dpMean" -> someIf(dpSC.count > 0, dpSC.mean),
      "dpStDev" -> someIf(dpSC.count > 0, dpSC.stdev),
      "dpMeanHomRef" -> someIf(dpHomRefSC.count > 0, dpHomRefSC.mean),
      "dpStDevHomRef" -> someIf(dpHomRefSC.count > 0, dpHomRefSC.stdev),
      "dpMeanHet" -> someIf(dpHetSC.count > 0, dpHetSC.mean),
      "dpStDevHet" -> someIf(dpHetSC.count > 0, dpHetSC.stdev),
      "dpMeanHomVar" -> someIf(dpHomVarSC.count > 0, dpHomVarSC.mean),
      "dpStDevHomVar" -> someIf(dpHomVarSC.count > 0, dpHomVarSC.stdev),
      "gqMean" -> someIf(gqSC.count > 0, gqSC.mean),
      "gqStDev" -> someIf(gqSC.count > 0, gqSC.stdev),
      "gqMeanHomRef" -> someIf(gqHomRefSC.count > 0, gqHomRefSC.mean),
      "gqStDevHomRef" -> someIf(gqHomRefSC.count > 0, gqHomRefSC.stdev),
      "gqMeanHet" -> someIf(gqHetSC.count > 0, gqHetSC.mean),
      "gqStDevHet" -> someIf(gqHetSC.count > 0, gqHetSC.stdev),
      "gqMeanHomVar" -> someIf(gqHomVarSC.count > 0, gqHomVarSC.mean),
      "gqStDevHomVar" -> someIf(gqHomVarSC.count > 0, gqHomVarSC.stdev),
      "nNonRef" -> (nHet + nHomVar),
      "rTiTv" -> divOption(nTi, nTv),
      "rHetHomVar" -> divOption(nHet, nHomVar),
      "rDeletionInsertion" -> divOption(nDel, nIns))
      .flatMap { case (k, v) => v match {
        case Some(value) => Some(k, value)
        case None => None
        case _ => Some(k, v)
      }
      }
  }

}

object SampleQC extends Command {

  class Options extends BaseOptions {

    @Args4jOption(required = false, name = "-o", aliases = Array("--output"),
      usage = "Output file")
    var output: String = _
  }

  def newOptions = new Options

  def name = "sampleqc"

  def description = "Compute per-sample QC metrics"

  def results(vds: VariantDataset): RDD[(Int, SampleQCCombiner)] = {

    /*
    val singletons = sSingletonVariants(vds)
    val singletonsBc = vds.sparkContext.broadcast(singletons)
    vds
      .aggregateBySampleWithKeys(new SampleQCCombiner)(
        (comb, v, s, g) => comb.merge(v, singletonsBc.value(v), g),
        (comb1, comb2) => comb1.merge(comb2))
        */

    val localSamplesBc = vds.sparkContext.broadcast(vds.localSamples)
    vds
      .rdd
      .mapPartitions[(Int, SampleQCCombiner)] { (it: Iterator[(Variant, Annotations, Iterable[Genotype])]) =>
      val zeroValue = Array.fill[SampleQCCombiner](localSamplesBc.value.length)(new SampleQCCombiner)
      localSamplesBc.value.iterator
        .zip(it.foldLeft(zeroValue) { case (acc, (v, va, gs)) =>
          val vIsSingleton = gs.iterator.existsExactly1(_.isCalledNonRef)
          for ((g, i) <- gs.iterator.zipWithIndex)
            acc(i) = acc(i).merge(v, vIsSingleton, g)
          acc
        }.iterator)
    }.foldByKey(new SampleQCCombiner)((comb1, comb2) => comb1.merge(comb2))
  }

  def run(state: State, options: Options): State = {
    val vds = state.vds

    val output = options.output

    val sampleIdsBc = state.sc.broadcast(vds.sampleIds)

    val r = results(vds)

    if (output != null) {
      hadoopDelete(output, state.hadoopConf, recursive = true)
      r.map { case (s, comb) =>
        val sb = new StringBuilder()
        sb.append(sampleIdsBc.value(s))
        sb += '\t'
        comb.emit(sb)
        sb.result()
      }.writeTable(output, Some("sampleID\t" + SampleQCCombiner.header))
    }
    val rMap = r
      .mapValues(_.asMap)
      .collectAsMap()
    val qcAnnotations = (0 until vds.nSamples)
      .map((s) => Annotations(Map("qc" -> rMap.get(s).getOrElse(s, Map.empty))))

    state.copy(
      vds = vds.copy(
        metadata = vds.metadata.addSampleAnnotations(
          Annotations(Map("qc" -> Annotations(SampleQCCombiner.signatures))),
          qcAnnotations)
      ))
  }
}
