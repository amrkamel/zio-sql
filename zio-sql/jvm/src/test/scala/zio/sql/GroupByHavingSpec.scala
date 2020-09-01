package zio.sql

import zio.test.Assertion.anything
import zio.test.{ assert, suite, test, DefaultRunnableSpec }

object GroupByHavingSpec extends DefaultRunnableSpec {

  import AggregatedProductSchema._

  def spec = suite("groupBy")(
    test("works") {
      assert(orderValue)(anything)
    }
  )
}

object AggregatedProductSchema {
  val sqldsl = new Sql {}
  import sqldsl.ColumnSet._
  import sqldsl.AggregationDef._
  import sqldsl._

  val productTable = (
    string("id") ++
      string("name") ++
      int("amount") ++
      double("price")
  ).table("product")

  val id :*: name :*: amount :*: price :*: _ = productTable.columns

  val orderValue =
    select { Arbitrary(name) ++ Sum(price) }
      .from(productTable)
      .groupBy(name)
      .having(Sum(price) > 10)
}
