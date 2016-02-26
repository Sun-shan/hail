package org.broadinstitute.hail

package object expr {
  type SymbolTable = Map[String, (Int, Type)]
  type EvalContext = (SymbolTable, Array[Any])

  implicit val toInt = IntNumericConversion
  implicit val toLong = LongNumericConversion
  implicit val toFloat = FloatNumericConversion
  implicit val toDouble = DoubleNumericConversion
}
