package org.broadinstitute.k3.variant.vsm

import org.broadinstitute.k3.SparkSuite
import org.broadinstitute.k3.variant.{Variant, VariantSampleMatrix}
import sys.process._
import scala.language.postfixOps
import org.broadinstitute.k3.methods.{sSingletonVariants, LoadVCF}
import org.testng.annotations.Test

class VSMSuite extends SparkSuite {
  val vsmTypes = List("managed", "sparky", "tuple", "df")

  @Test def testsSingletonVariants() {
    val singletons: List[Set[Variant]] =
      vsmTypes
        .map(vsmtype => {
        val vdsdir = "/tmp/sample." + vsmtype + ".vds"

        val result = "rm -rf " + vdsdir !;
        assert(result == 0)

        LoadVCF(sc, sqlContext, "src/test/resources/sample.vcf.gz", vsmtype = vsmtype)
          .write(sqlContext, vdsdir)

        val vds = VariantSampleMatrix.read(sqlContext, vdsdir)
        sSingletonVariants(vds)
      })

    assert(singletons.tail.forall(s => s == singletons.head))
  }
}
