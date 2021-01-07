package zio.sql.postgresql

import java.time.LocalDate
import java.util.UUID

import zio.Cause
import zio.test.Assertion._
import zio.test._

import zio.sql.Jdbc
import java.util.Properties
import zio.sql.TestContainer
import zio.Has
import zio.ZLayer
import zio.blocking.Blocking
import zio.test.environment.TestEnvironment
import scala.language.postfixOps

object PostgresModuleTest2
    extends DefaultRunnableSpec
    with Jdbc
    with /*PostgresRunnableSpec with */ ShopSchema
    with PostgresModule {

  private def connProperties(user: String, password: String): Properties = {
    val props = new Properties
    props.setProperty("user", user)
    props.setProperty("password", password)
    props
  }

  private lazy val executorLayer = {
    println("!!! SETUP executorLayer")
    val poolConfigLayer = TestContainer
      .postgres("postgres:alpine:13")
      .map(a => Has(ConnectionPool.Config(a.get.jdbcUrl, connProperties(a.get.username, a.get.password))))

    val connectionPoolLayer = ZLayer.identity[Blocking] >+> poolConfigLayer >>> ConnectionPool.live

    (ZLayer.identity[
      Blocking
    ] ++ connectionPoolLayer >+> ReadExecutor.live >+> UpdateExecutor.live >+> DeleteExecutor.live >+> TransactionExecutor.live).orDie
  }

  import Customers._
  import Orders._

  private def customerSelectJoseAssertion(condition: Expr[_, customers.TableType, Boolean]) = {
    case class Customer(id: UUID, fname: String, lname: String, verified: Boolean, dateOfBirth: LocalDate)

    val query =
      select(customerId ++ fName ++ lName ++ verified ++ dob) from customers where (condition)

    println(renderRead(query))

    val expected =
      Seq(
        Customer(
          UUID.fromString("636ae137-5b1a-4c8c-b11f-c47c624d9cdc"),
          "Jose",
          "Wiggins",
          false,
          LocalDate.parse("1987-03-23")
        )
      )

    val testResult = execute(query)
      .to[UUID, String, String, Boolean, LocalDate, Customer] { case row =>
        Customer(row._1, row._2, row._3, row._4, row._5)
      }

    val assertion = for {
      r <- testResult.runCollect
    } yield assert(r)(hasSameElementsDistinct(expected))

    assertion.mapErrorCause(cause => Cause.stackless(cause.untraced))
  }

  override def spec = suite("Postgres module")(
    testM("Can select from single table") {
      case class Customer(id: UUID, fname: String, lname: String, dateOfBirth: LocalDate)

      val query = select(customerId ++ fName ++ lName ++ dob) from customers

      println(renderRead(query))

      val expected =
        Seq(
          Customer(
            UUID.fromString("60b01fc9-c902-4468-8d49-3c0f989def37"),
            "Ronald",
            "Russell",
            LocalDate.parse("1983-01-05")
          ),
          Customer(
            UUID.fromString("f76c9ace-be07-4bf3-bd4c-4a9c62882e64"),
            "Terrence",
            "Noel",
            LocalDate.parse("1999-11-02")
          ),
          Customer(
            UUID.fromString("784426a5-b90a-4759-afbb-571b7a0ba35e"),
            "Mila",
            "Paterso",
            LocalDate.parse("1990-11-16")
          ),
          Customer(
            UUID.fromString("df8215a2-d5fd-4c6c-9984-801a1b3a2a0b"),
            "Alana",
            "Murray",
            LocalDate.parse("1995-11-12")
          ),
          Customer(
            UUID.fromString("636ae137-5b1a-4c8c-b11f-c47c624d9cdc"),
            "Jose",
            "Wiggins",
            LocalDate.parse("1987-03-23")
          )
        )

      val testResult = execute(query)
        .to[UUID, String, String, LocalDate, Customer] { case row =>
          Customer(row._1, row._2, row._3, row._4)
        }

      val assertion = for {
        r <- testResult.runCollect
      } yield assert(r)(hasSameElementsDistinct(expected))

      assertion.mapErrorCause(cause => Cause.stackless(cause.untraced))
    },
    testM("Can select with property unary operator") {
      customerSelectJoseAssertion(verified isNotTrue)
    },
    testM("Can select from single table with limit, offset and order by") {
      case class Customer(id: UUID, fname: String, lname: String, dateOfBirth: LocalDate)

      val query = (select(customerId ++ fName ++ lName ++ dob) from customers).limit(1).offset(1).orderBy(fName)

      println(renderRead(query))

      val expected =
        Seq(
          Customer(
            UUID.fromString("636ae137-5b1a-4c8c-b11f-c47c624d9cdc"),
            "Jose",
            "Wiggins",
            LocalDate.parse("1987-03-23")
          )
        )

      val testResult = execute(query)
        .to[UUID, String, String, LocalDate, Customer] { case row =>
          Customer(row._1, row._2, row._3, row._4)
        }

      val assertion = for {
        r <- testResult.runCollect
      } yield assert(r)(hasSameElementsDistinct(expected))

      assertion.mapErrorCause(cause => Cause.stackless(cause.untraced))
    },
    testM("Can select from joined tables (inner join)") {
      val query = select(fName ++ lName ++ orderDate) from (customers join orders).on(fkCustomerId === customerId)

      println(renderRead(query))

      case class Row(firstName: String, lastName: String, orderDate: LocalDate)

      val expected = Seq(
        Row("Ronald", "Russell", LocalDate.parse("2019-03-25")),
        Row("Ronald", "Russell", LocalDate.parse("2018-06-04")),
        Row("Alana", "Murray", LocalDate.parse("2019-08-19")),
        Row("Jose", "Wiggins", LocalDate.parse("2019-08-30")),
        Row("Jose", "Wiggins", LocalDate.parse("2019-03-07")),
        Row("Ronald", "Russell", LocalDate.parse("2020-03-19")),
        Row("Alana", "Murray", LocalDate.parse("2020-05-11")),
        Row("Alana", "Murray", LocalDate.parse("2019-02-21")),
        Row("Ronald", "Russell", LocalDate.parse("2018-05-06")),
        Row("Mila", "Paterso", LocalDate.parse("2019-02-11")),
        Row("Terrence", "Noel", LocalDate.parse("2019-10-12")),
        Row("Ronald", "Russell", LocalDate.parse("2019-01-29")),
        Row("Terrence", "Noel", LocalDate.parse("2019-02-10")),
        Row("Ronald", "Russell", LocalDate.parse("2019-09-27")),
        Row("Alana", "Murray", LocalDate.parse("2018-11-13")),
        Row("Jose", "Wiggins", LocalDate.parse("2020-01-15")),
        Row("Terrence", "Noel", LocalDate.parse("2018-07-10")),
        Row("Mila", "Paterso", LocalDate.parse("2019-08-01")),
        Row("Alana", "Murray", LocalDate.parse("2019-12-08")),
        Row("Mila", "Paterso", LocalDate.parse("2019-11-04")),
        Row("Mila", "Paterso", LocalDate.parse("2018-10-14")),
        Row("Terrence", "Noel", LocalDate.parse("2020-04-05")),
        Row("Jose", "Wiggins", LocalDate.parse("2019-01-23")),
        Row("Terrence", "Noel", LocalDate.parse("2019-05-14")),
        Row("Mila", "Paterso", LocalDate.parse("2020-04-30"))
      )

      val result = execute(query)
        .to[String, String, LocalDate, Row] { case row =>
          Row(row._1, row._2, row._3)
        }

      val assertion = for {
        r <- result.runCollect
      } yield assert(r)(hasSameElementsDistinct(expected))

      assertion.mapErrorCause(cause => Cause.stackless(cause.untraced))
    },
    testM("Can select using like") {
      case class Customer(id: UUID, fname: String, lname: String, dateOfBirth: LocalDate)

      val query = select(customerId ++ fName ++ lName ++ dob) from customers where (fName like "Jo%")

      println(renderRead(query))
      val expected = Seq(
        Customer(
          UUID.fromString("636ae137-5b1a-4c8c-b11f-c47c624d9cdc"),
          "Jose",
          "Wiggins",
          LocalDate.parse("1987-03-23")
        )
      )

      val testResult = execute(query)
        .to[UUID, String, String, LocalDate, Customer] { case row =>
          Customer(row._1, row._2, row._3, row._4)
        }

      val assertion = for {
        r <- testResult.runCollect
      } yield assert(r)(hasSameElementsDistinct(expected))

      assertion.mapErrorCause(cause => Cause.stackless(cause.untraced))
    },
    testM("Transactions is returning the last value") {
      val query = select(customerId) from customers

      val result    = execute(
        ZTransaction.Select(query) *> ZTransaction.Select(query)
      )
      val assertion = assertM(result.flatMap(_.runCollect))(hasSize(Assertion.equalTo(5))).orDie

      assertion.mapErrorCause(cause => Cause.stackless(cause.untraced))
    },
    testM("Transaction is failing") {
      val query = select(customerId) from customers

      val result = execute(
        ZTransaction.Select(query) *> ZTransaction.fail(new Exception("failing")) *> ZTransaction.Select(query)
      ).mapError(_.getMessage)

      assertM(result.flip)(equalTo("failing")).mapErrorCause(cause => Cause.stackless(cause.untraced))
    }
  ).provideCustomLayerShared(TestEnvironment.live >+> executorLayer)

}
