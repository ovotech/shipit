package elasticsearch

import elasticsearch.Pagination.Page
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class PaginationSpec extends AnyFlatSpec with Matchers {

  ({
    Page[String](Nil, 1, 0).lastPage shouldBe 1
    Page[String](Nil, 1, 1).lastPage shouldBe 1
    Page[String](Nil, 1, 19).lastPage shouldBe 1
    Page[String](Nil, 1, 20).lastPage shouldBe 1
    Page[String](Nil, 1, 21).lastPage shouldBe 2
    Page[String](Nil, 1, 50).lastPage shouldBe 3
  } / it should "calculate the last page")

}
