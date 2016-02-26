package org.broadinstitute.hail.expr

import org.broadinstitute.hail.Utils._
import scala.util.parsing.combinator.JavaTokenParsers
import scala.util.parsing.input.Position

object ParserUtils {
  def error(pos: Position, msg: String): Nothing = {
    val lineContents = pos.longString.split("\n").head
    val prefix = s"<input>:${pos.line}:"
    fatal(
      s"""$msg
         |$prefix$lineContents
         |${" " * prefix.length}${
        lineContents.take(pos.column - 1).map { c => if (c == '\t') c else ' ' }
      }^""".stripMargin)
  }
}

object Parser extends JavaTokenParsers {
  def parse[T](symTab: Map[String, (Int, Type)], a: Array[Any], code: String): () => T = {
    // println(s"code = $code")
    val t: AST = parseAll(expr, code) match {
      case Success(result, _) => result.asInstanceOf[AST]
      case NoSuccess(msg, next) => ParserUtils.error(next.pos, msg)
    }
    // println(s"t = $t")

    t.typecheck(symTab)
    val f: () => Any = t.eval((symTab, a))
    () => f().asInstanceOf[T]
  }

  def withPos[T](p: => Parser[T]): Parser[Positioned[T]] =
    positioned[Positioned[T]](p ^^ { x => Positioned(x) })

  def parseExportArgs(symTab: Map[String, (Int, Type)],
    a: Array[Any],
    code: String): (Option[String], Array[() => Any]) = {
    val (header, ts) = parseAll(export_args, code) match {
      case Success(result, _) => result.asInstanceOf[(Option[String], Array[AST])]
      case NoSuccess(msg, _) => fatal(msg)
    }

    ts.foreach(_.typecheck(symTab))
    val fs = ts.map { t =>
      t.eval((symTab, a))
    }
    (header, fs)
  }

  def expr: Parser[AST] = if_expr | or_expr

  def if_expr: Parser[AST] =
    withPos("if") ~ ("(" ~> expr <~ ")") ~ expr ~ ("else" ~> expr) ^^ { case ifx ~ cond ~ thenTree ~ elseTree =>
      If(ifx.pos, cond, thenTree, elseTree)
    }

  def or_expr: Parser[AST] =
    and_expr ~ rep(withPos("||" | "|") ~ and_expr) ^^ { case lhs ~ lst =>
      lst.foldLeft(lhs) { case (acc, op ~ rhs) => BinaryOp(op.pos, acc, op.x, rhs) }
    }

  def and_expr: Parser[AST] =
    lt_expr ~ rep(withPos("&&" | "&") ~ lt_expr) ^^ { case lhs ~ lst =>
      lst.foldLeft(lhs) { case (acc, op ~ rhs) => BinaryOp(op.pos, acc, op.x, rhs) }
    }

  def lt_expr: Parser[AST] =
    eq_expr ~ rep(withPos("<=" | ">=" | "<" | ">") ~ eq_expr) ^^ { case lhs ~ lst =>
      lst.foldLeft(lhs) { case (acc, op ~ rhs) => Comparison(op.pos, acc, op.x, rhs) }
    }

  def eq_expr: Parser[AST] =
    add_expr ~ rep(withPos("==" | "!=") ~ add_expr) ^^ { case lhs ~ lst =>
      lst.foldLeft(lhs) { case (acc, op ~ rhs) => Comparison(op.pos, acc, op.x, rhs) }
    }

  def add_expr: Parser[AST] =
    mul_expr ~ rep(withPos("+" | "-") ~ mul_expr) ^^ { case lhs ~ lst =>
      lst.foldLeft(lhs) { case (acc, op ~ rhs) => BinaryOp(op.pos, acc, op.x, rhs) }
    }

  def mul_expr: Parser[AST] =
    tilde_expr ~ rep(withPos("*" | "/" | "%") ~ tilde_expr) ^^ { case lhs ~ lst =>
      lst.foldLeft(lhs) { case (acc, op ~ rhs) => BinaryOp(op.pos, acc, op.x, rhs) }
    }

  def tilde_expr: Parser[AST] =
    dot_expr ~ rep(withPos("~") ~ dot_expr) ^^ { case lhs ~ lst =>
      lst.foldLeft(lhs) { case (acc, op ~ rhs) => BinaryOp(op.pos, acc, op.x, rhs) }
    }

  def export_args: Parser[(Option[String], Array[AST])] =
  // FIXME | not backtracking properly.  Why?
    args ^^ { a => (None, a) } |||
      named_args ^^ { a =>
        (Some(a.map(_._1).mkString("\t")), a.map(_._2))
      }

  def named_args: Parser[Array[(String, AST)]] =
    named_arg ~ rep("," ~ named_arg) ^^ { case arg ~ lst =>
      (arg :: lst.map { case _ ~ arg => arg }).toArray
    }

  def named_arg: Parser[(String, AST)] =
    ident ~ "=" ~ expr ^^ { case id ~ eq ~ expr => (id, expr) }

  def args: Parser[Array[AST]] =
    repsep(expr, ",") ^^ { _.toArray }

  def dot_expr: Parser[AST] =
    unary_expr ~ rep((withPos(".") ~ ident) | (withPos("(") ~ args ~ ")")) ^^ { case lhs ~ lst =>
      lst.foldLeft(lhs) { (acc, t) => (t: @unchecked) match {
        case (dot: Positioned[_]) ~ sym => Select(dot.pos, acc, sym)
        case (lparen: Positioned[_]) ~ (args: Array[AST]) ~ ")" => Apply(lparen.pos, acc, args)
      }
      }
    }

  def unary_expr: Parser[AST] =
    rep(withPos("-" | "!")) ~ primary_expr ^^ { case lst ~ rhs =>
      lst.foldRight(rhs) { case (op, acc) =>
        UnaryOp(op.pos, op.x, acc)
      }
    }

  def primary_expr: Parser[AST] =
    withPos("""-?\d+\.\d+[dD]?""".r) ^^ (r => Const(r.pos, r.x.toDouble, TDouble)) |
      withPos("""-?\d+(\.\d*)?[eE][+-]?\d+[dD]?""".r) ^^ (r => Const(r.pos, r.x.toDouble, TDouble)) |
      // FIXME L suffix
      withPos(wholeNumber) ^^ (r => Const(r.pos, r.x.toInt, TInt)) |
      withPos(stringLiteral) ^^ { r =>
        val x = r.x
        assert(x.head == '"' && x.last == '"')
        Const(r.pos, x.tail.init, TString)
      } |
      withPos("true") ^^ (r => Const(r.pos, true, TBoolean)) |
      withPos("false") ^^ (r => Const(r.pos, false, TBoolean)) |
      guard(not("if" | "else")) ~> withPos(ident) ^^ (r => SymRef(r.pos, r.x)) |
      "{" ~> expr <~ "}" |
      "(" ~> expr <~ ")"
}
