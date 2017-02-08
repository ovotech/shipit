package es

import es.ES.Page
import org.scalatest.{FlatSpec, Matchers}

class PaginationSpec extends FlatSpec with Matchers {

  it should "calculate the last page" in {
    Page[String](Nil, 1, 0).lastPage shouldBe 1
    Page[String](Nil, 1, 1).lastPage shouldBe 1
    Page[String](Nil, 1, 19).lastPage shouldBe 1
    Page[String](Nil, 1, 20).lastPage shouldBe 1
    Page[String](Nil, 1, 21).lastPage shouldBe 2
    Page[String](Nil, 1, 50).lastPage shouldBe 3
  }

}
